
/**
 * Garett Petersen
 * 2/24/2016
 * Professor: Pantic
 * Program assignment 6
 * 
 * In this project, we’ll be (mostly) implementing TCP’s connection establishment
 * and connection teardown procedures. It will be similar to the previous project 
 * in that you must implement TCP on top of IPv4.
 */
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Random;
import javax.xml.bind.DatatypeConverter;

public class TcpClient {
	private final static short IPV4_HEADERSIZE = 20; //ipv4 header size is 20(bytes)
	private final static short TCP_HEADERSIZE = 20; //28 bytes
	private final byte[] source = new byte[4];
	private final byte[] dest = new byte[4];
	private InputStream socketIn;
	private PrintStream out;
	private short DATASIZE = 2;
	private int mySeq = 0;
	private int ackResponse = 0;
	
	public static void main(String[] args) throws UnknownHostException, IOException {
		TcpClient tcp = new TcpClient();
		tcp.run();
	}
	
	private void run() throws UnknownHostException, IOException { 
		String host = "cs380.codebank.xyz";
		int port = 38006;
		short counter = 1;
		
		source[0] = 0b0; //0
		source[1] = 0b0; //0
		source[2] = 0b01; //1
		source[3] = 0b01; //1
		
		
		dest[0] = 0b00110100; //52
		dest[1] = 0b00100001; //33
		dest[2] = (byte) 0b10000011; //131
		dest[3] = 0b00010000; //16
		
		try (Socket socket = new Socket(host, port)) { 
			socketIn = (socket.getInputStream());
			out = new PrintStream(socket.getOutputStream());
			System.out.println("Connected to " + host + ":" + port + "!\n");
			// just used to see ip address 
			InetAddress ServerIP = socket.getInetAddress();
			System.out.println(ServerIP);
			
			if (!tcpHandshake()) { //if handshake fails...
				System.out.println("Closing connection...");
			}
			else { //send 12 packets 
				// "You should have started the data sequence number by
				// incrementing the value you used in your ACK packet."
				++mySeq;
				while(counter < 13) {
					byte[] randomData = new byte[DATASIZE];
						new Random().nextBytes(randomData);
					//cwr, ece, urg, ack, psh, rst, syn, fin
					byte flags = 0b00000010;
					
					byte[] tcp = buildTCP(randomData, mySeq, ackResponse, flags); //random data, random sequence #, ack (random + 1)
					byte[] packet = buildPacket(tcp);
					
					out.write(packet);
					byte[] response = new byte[4];
					int count = socketIn.read(response);
					System.out.println(counter + ") " + DatatypeConverter.printHexBinary(response));
				
					mySeq += DATASIZE;
					DATASIZE *= 2;
					counter++;
				}
				//start connection teardown
				System.out.println("\nStart of teardown");
				runTeardown();
			}
			socketIn.close();
			out.close();
		}
		System.exit(0);
	}
	
	/*
	 * You will send a complete IPv4 packet containing a TCP header that has 
	 * the value of your sequence number + 1, the ACK flag set, 
	 * and an acknowledgment of my sequence number + 1.
	 */
	
	private byte[] buildTCP(byte[] data, int seq, int ack, byte flags) { 
		byte[] tcp = new byte[TCP_HEADERSIZE + data.length]; //tcp_headersize = 20 bytes
		
		//source and dest ports
		tcp[0] = 0;
		tcp[1] = 0;
		tcp[2] = 0;
		tcp[3] = 0;
		
		//seq num
		tcp[4] = (byte) (seq >> 24 & 0xFF);
		tcp[5] = (byte) (seq >> 16 & 0xFF);
		tcp[6] = (byte) (seq >> 8 & 0xFF);
		tcp[7] = (byte) (seq & 0xFF);
		
		//ack number
		tcp[8] = (byte) (ack >> 24 & 0xFF);
		tcp[9] = (byte) (ack >> 16 & 0xFF);
		tcp[10] = (byte) (ack >> 8 & 0xFF);
		tcp[11] = (byte) (ack & 0xFF);
		
		//data offset & reserved & ns
		tcp[12] = 0b0101; //5 words
		tcp[12] <<= 4;
		
		//cwr, ece, urg, ack, psh, rst, syn, fin
		tcp[13] = flags;
		
		//window size
		tcp[14] = 0;
		tcp[15] = 0;
		
		//checksum
		tcp[16] = 0;
		tcp[17] = 0;
		
		//urgent pointer
		tcp[18] = 0;
		tcp[19] = 0;
		
		for (int i=20, j=0; i<data.length; ++i, ++j) { 
			tcp[i] = data[j];
		}
		
		short shortarray[] = byteToShort(tcp);
		short checksum = (short)tcpChecksum(shortarray, (short) tcp.length);
		
		//checksum
		tcp[16] = (byte) ((checksum >>> 8) & 0xFF);
		tcp[17] = (byte) (checksum & 0xFF);
		
		return tcp;
	}
	
	/**
	 * packet is implemented exactly the same as previous assignments.
	 * just copy and pasted it basically
	 * @param data
	 * @return
	 */
	
	private byte[] buildPacket(byte[] data) { 
		byte packet[] = new byte[IPV4_HEADERSIZE + data.length]; //HEADERSIZE is 20
		
		packet[0] = 0x4;
		packet[0] <<= 4;
		packet[0] |= 0x5;
		
		packet[1] = 0;
		
		short tempshort = (short) (IPV4_HEADERSIZE + data.length);
		packet[2] = (byte) ((tempshort >>> 8) & 0xFF);
		packet[3] = (byte) (tempshort & 0xFF); //adjust based on data size
		
		packet[4] = 0;
		packet[5] = 0;	//id
		
		packet[6] = 0b010; //flags & offset
		packet[6] <<= 5;
		packet[6] |= 0b0;
		packet[7] = 0b0;
		
		packet[8] = 0b00110010; //ttl
		
		packet[9] = 0x06; //protocol 
		
		for(int i=12, j=0; i<16; ++i, ++j) 
			packet[i] = source[j];
		
		for(int i=16, j=0; i<20; ++i, ++j) 
			packet[i] = dest[j];
		
		short[] shortarray = byteToShort(packet);
		short[] t = new short[1];
		t[0] = (short) checksum(shortarray);
		byte t1[] = toByteArray(t);

		packet[10] = t1[0]; //checksum locations
		packet[11] = t1[1]; 
		
		for(int i=20, j=0; j<data.length; ++i, ++j)
			packet[i] = data[j];
		
		return packet;
		
	}
	
	
	private void runTeardown() throws IOException { 
		byte[] randomData = new byte[2];
		new Random().nextBytes(randomData);
		byte flags = 0b00000001;
		byte[] tcp = buildTCP(randomData, mySeq, ackResponse, flags); //random data, random sequence #, ack (random + 1)
		byte[] packet = buildPacket(tcp);
		
		out.write(packet);
		byte[] response = new byte[4];
		int count = socketIn.read(response);
		System.out.println("Teardown Response: " + DatatypeConverter.printHexBinary(response));
		
		byte[] ackRes = new byte[TCP_HEADERSIZE];
		count = socketIn.read(ackRes);
		
		byte[] finResponse = new byte[TCP_HEADERSIZE];
		count = socketIn.read(finResponse);
		
		flags = 0b00010000;
		tcp = buildTCP(randomData, mySeq, ackResponse, flags); //random data, random sequence #, ack (random + 1)
		packet = buildPacket(tcp);
		out.write(packet);
		
		count = socketIn.read(response);
		System.out.println("Ack Response: " + DatatypeConverter.printHexBinary(response));
	}
	
	private boolean tcpHandshake() throws IOException { 
		byte[] randomData = new byte[DATASIZE];
			new Random().nextBytes(randomData);
		int randomSeq = new Random().nextInt();
		mySeq = randomSeq;
		//cwr, ece, urg, ack, psh, rst, syn, fin
		byte flags = 0b00000010;
		
		byte[] init = buildTCP(randomData, randomSeq, randomSeq + 1, flags); //random data, random sequence #, ack (random + 1)
		byte[] packet = buildPacket(init);

		out.write(packet);
		byte[] initResponse = new byte[4];
		// this should read in cafebabe if the packet was sent correctly..similar to the udp
		// assignment
		
		int count = socketIn.read(initResponse);

		if (DatatypeConverter.printHexBinary(initResponse).equals("CAFEBABE")) { //CAFEBABE received
			System.out.println("Beginning handshake: " + DatatypeConverter.printHexBinary(initResponse) + "\n");
			System.out.println("\nParsing TCP Header Response...\n");
			
			byte response[] = new byte[TCP_HEADERSIZE];
			count = socketIn.read(response);
			//only extract the returned ack number from 
			ackResponse = response[4] << 24 | (response[5] & 0xFF) << 16 | (response[6] & 0xFF) << 8 | (response[7] & 0xFF);
			
			//cwr, ece, urg, ack, psh, rst, syn, fin
			flags = 0b00010000;
			byte tcp[] = buildTCP(randomData, ++mySeq, ++ackResponse, flags);
			packet = buildPacket(tcp);
			
			out.write(packet);
			byte[] ackResponse = new byte[4];
			count = socketIn.read(ackResponse);

			if (DatatypeConverter.printHexBinary(ackResponse).equals("CAFEBABE")) {
				System.out.println("Three way ackknowledgement response: " + DatatypeConverter.printHexBinary(ackResponse));
				return true;
			}
			else { 
				System.out.println("Error with establishing initial handshake");
				System.out.println("Error code: " + DatatypeConverter.printHexBinary(ackResponse));
				return false;
			}
		}
		else { 
			System.out.println("Error with establishing initial handshake");
			System.out.println("Error code: " + DatatypeConverter.printHexBinary(initResponse));
			return false;
		}
	}
	
	private long tcpChecksum(short[] buf, short length) { 
		long sum = 0;
		short tcp[] = new short[buf.length + 6];
		
		tcp[0] = source[0];
		tcp[0] <<= 8;
		tcp[0] |= source[1];
		tcp[1] = source[2];
		tcp[1] <<= 8;
		tcp[1] |= source[3];
		
		tcp[2] = dest[0];
		tcp[2] <<= 8;
		tcp[2] |= dest[1];
		tcp[3] = dest[2];
		tcp[3] <<= 8;
		tcp[3] |= dest[3];
		
		tcp[4] = 0;
		tcp[4] <<= 8;
		tcp[4] |= 0x06;
		
		tcp[5] = length;
		
		for(int i=6, j=0; i<buf.length + 6; ++i, ++j) 
			tcp[i] = buf[j];
		
		for(int i=0; i<tcp.length; ++i) { 
			sum += (tcp[i] & 0xFFFF);
			if((sum & 0xFFFF0000) > 0) { 
				sum &= 0xFFFF;
				sum++;
			}
		}
		sum = ~(sum & 0xFFFF);
		return sum;
	}
	
	
	private long checksum(short[] buf) { 
		long sum = 0;
		for(int i=0; i<10; ++i) { 
			sum += (buf[i] & 0xFFFF);
			if ((sum & 0xFFFF0000) > 0) { //carry 
				sum &= 0xFFFF;
				sum++;
			}
		}
		sum = ~(sum & 0xFFFF);
		return sum;
	}
	
	public void printPacket(byte[] sendIt) {
		System.out.println("SENDING PACKET OF LENGTH " + sendIt.length);
		for (int i = 0; i < sendIt.length; i++) {
			String temp = "";
			for (int j = 7; j >= 0; j--)
				temp += ((0b1 << j & sendIt[i]) > 0) ? "1" : "0";
			if (i % 4 == 0) 
				System.out.println();
			while (temp.length() < 8) 
				temp += ("0" + temp);
			System.out.print(temp.substring(temp.length() - 8) + " ");
		}
		System.out.println();
	}
	
	private byte[] toByteArray(short[] message) {
		byte[] b = new byte[message.length * 2];
		for (int i = 0, j = 0; i < message.length; i++, j += 2) {
			b[j + 1] |= (message[i] & 0xFF);
			message[i] >>>= 8;
			b[j] |= (message[i] & 0xFF);
		}
		return b;
	}
	
	private short[] byteToShort(byte[] message) {
		short[] shortMessage = new short[(message.length + 1) / 2];
		for (int i = 0, j = 0; j < message.length - 1; i++, j += 2) {
			shortMessage[i] |= (message[j] & 0xFF);
			shortMessage[i] <<= 8;
			shortMessage[i] |= (message[j + 1] & 0xFF);
		}
		return shortMessage;
	}
}