
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;


public class Server {

	public static void main(String[] args) throws IOException {
		byte buf[] = new byte[600];
		int port = 6780;
		
		DatagramSocket socket = new DatagramSocket(port);
		
		receive receiver = new receive(socket, port); //���ž����带 ����
		receiver.start();

	}
	
	

}

class receive extends Thread{//���ž�����
	
	 DatagramSocket Socket = null;
	 private boolean stopped = false;
	 DatagramPacket receivePacket = null;
	 DatagramPacket AckPacket = null;
	 
	 
	 public DatagramPacket getPacket(){
		 
		 return this.receivePacket;
	 }
	
	 public DatagramSocket getSocket() {
		    return this.Socket;
	 }
	 
	 public void halt() {
		 	this.stopped = true;
	 }
	  
	public receive(DatagramSocket socket, int port) throws SocketException{
		this.Socket = socket;
	}
	
	public static byte[] hexStringToByteArray(String s) { //16���� ���ڿ��� ����Ʈ ���� �ٲٴ� �޼ҵ�
	    int len = s.length();
	    byte[] data = new byte[len / 2];
	    for (int i = 0; i < len; i += 2) {
	        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
	                             + Character.digit(s.charAt(i+1), 16));
	    }
	    return data;
	}
	
	
	public static String byteArrayToHexString(byte[] bytes){ //����Ʈ���� 16���� ���ڿ��� �ٲٴ� �޼ҵ�
		
		StringBuilder sb = new StringBuilder(); 
		
		for(byte b : bytes){ 
			
			sb.append(String.format("%02X", b&0xff)); 
		} 
		
		return sb.toString(); 
	}
	
	public int checkCRC(byte[] data){//CRC�� üũ�մϴ�.
		int len = data.length;
		ByteBuffer buf = ByteBuffer.allocate(len);
		buf.put(data);
		
		int crc = buf.getInt(len-4);
		
		byte[] oridata = new byte[len-4]; 
		
		System.arraycopy(data, 0, oridata, 0, oridata.length);//CRC�� ��� ������ �κ��� �̾Ƴ��ϴ�.
		
	
		CRC32 crc32 = new CRC32();
		crc32.update(oridata);//�и��� �κ��� CRC�� �������ϴ�.
		
		int h = (int)crc32.getValue();
		//System.out.println(Integer.toHexString(h));
		
		if(crc == (int)crc32.getValue()){//��Ŷ�� �پ�� CRC�� ���� ���� CRC�� ���մϴ�.
			
			return 1;//���ٸ� �̻��� ���ٰ� �Ǵ��ϰ� 1�� RETURN
		}else{
			return 0;//�̻��ִٸ� 0�� RETURN
		}
		
	}
	
	public void run(){
		try{
			SFrame sframe = new SFrame();
			LLC llc = new LLC();
			while(true){
				byte buf[] = new byte[600];
				
				ByteBuffer buffer = ByteBuffer.allocate(600);
				if (stopped)
			        return;

				receivePacket = new DatagramPacket(buf, buf.length);
				Socket.receive(receivePacket); //��Ŷ�� �����մϴ�.
				buffer.put(receivePacket.getData());
				
				int len = buffer.getShort(12);
				
				byte []dbuf = new byte[len];
				
				System.arraycopy(receivePacket.getData(),0 , dbuf, 0, len);
				
				String xs = byteArrayToHexString(dbuf);
				
				System.out.println("���Ź��� ��Ŷ : " + xs); //���Ź��� ��ü ��Ŷ�� ����մϴ�.
				
				
				
				
				if( checkCRC(dbuf) == 1 ){ //CRC��� �̻��� ���ٸ�
					byte[] rr = sframe.getRR(); 
					byte[] ack = llc.getLLC(rr);//ACK�� �غ��մϴ�.
					//
					
					byte[] control = new byte[1];
					System.arraycopy(dbuf, 16, control, 0, 1);
					String asdf = byteArrayToHexString(control);
					int con = Integer.parseInt(asdf.replace("0x", ""), 16); //�������� CONTROL�κ� �̾Ƴ��ϴ�.

					
					
					if(con <128){ //IFRAME�̶��,
						byte []Sbuf = new byte[len-22];
						System.arraycopy(dbuf,18 , Sbuf, 0, len-22);
						
				        String msg = new String(Sbuf);
				        System.out.println("���Ź��� �޼��� : " + msg); //IFRAME �������� INFROMATION�� �и��ؼ� ä�� ��ȭ�� ����մϴ�.
				        AckPacket = new DatagramPacket(ack, ack.length, receivePacket.getAddress(), receivePacket.getPort());
						Socket.send(AckPacket);//ACK�� �����ϴ�.
						
					}else{//UFRAME - SABME���(����)
						System.out.println("SABME�� �޾ҽ��ϴ�.");
						UFrame uframe = new UFrame();
						buf = uframe.getUA();
						byte[] llcData = llc.getLLC(buf);
						AckPacket = new DatagramPacket(llcData, llcData.length, receivePacket.getAddress(), receivePacket.getPort());
						Socket.send(AckPacket); //UA�� �����մϴ�.
					}
									
					
					
	
				}else{//CRC�˻縦 ������� ���ߴٸ�
					byte[] rej = sframe.getREJ();
					byte[] nack = llc.getLLC(rej);
					//REJ�� �غ��մϴ�.
					
					AckPacket = new DatagramPacket(nack, nack.length, receivePacket.getAddress(), receivePacket.getPort());
					Socket.send(AckPacket); //NACK��Ŷ�� Ŭ���̾�Ʈ�� �����մϴ�.

					
					
					
				}
						        
				
				Thread.yield();
				
				
				
				
				
			}
			
			
			
		} catch (SocketException e) {
			e.printStackTrace();
			} catch (IOException e) {
			e.printStackTrace();
			}
	}
	
}

class LLC{ //��ü �������� �����մϴ�.
	byte[] DA = {(byte) 0xaa,(byte) 0xbb,(byte) 0xcc,(byte) 0xdd,(byte) 0xee,(byte) 0xff};
	byte[] SA = {(byte) 0xaa,(byte) 0xbb,(byte) 0xcc,(byte) 0xdd,(byte) 0xee,(byte) 0xff};
	byte[] DataAndPadding = null;
	byte[] CRC32 = {(byte)0xbb,(byte)0xbb,(byte)0xbb,(byte)0xbb};
	CRC32 crc = new CRC32();
	ByteBuffer buf;
	
	public byte[] getLLC(byte[] frame){
		int len = (frame.length)+18;
		DataAndPadding = frame;
		buf = ByteBuffer.allocate(len-4);
		
		buf.put(DA);
		buf.put(SA);
		buf.putShort((short)len);
		buf.put(DataAndPadding);
				
		byte[] result1 = buf.array();

		buf = ByteBuffer.allocate(len);
		buf.put(result1);
		CRC32 crc = new CRC32();
		crc.update(result1);
		
		int crc32 = (int)crc.getValue();
		
		buf.putInt(crc32);
		
		byte[] result2 = buf.array();
		
		
		return result2;
	}
	
	
	
}

class SFrame{//SFRAME�� �����մϴ�.
	byte DSAP = (byte) 0xff;
	byte SSAP = (byte) 0xff;
	byte CRR = (byte) 0x80;
	byte CREJ = (byte) 0x90;
	ByteBuffer buf;
	byte NR = (byte) 0x00;
	
	
	public byte[] getRR(){
		buf = ByteBuffer.allocate(4);
		setSeq();
		
		buf.put(DSAP);
		buf.put(SSAP);
		buf.put(CRR);
		buf.put(NR);
		byte[] result = buf.array();
		
		return result;
		
		
		
		
	}
	
	public byte[] getREJ(){
		buf = ByteBuffer.allocate(4);

		buf.put(DSAP);
		buf.put(SSAP);
		buf.put(CREJ);
		buf.put(NR);
		byte[] result = buf.array();
		
		return result;
		
		
	}
	
	void setSeq(){
		
		
		int nrs = this.NR;
		
		if(nrs==127){
			nrs = 0;
		}else{
			nrs+=1;
		}
		
		this.NR = (byte)nrs;
		
	}
	
}

class UFrame { //UFRAME�� �����մϴ�.
	
	byte DSAP = (byte) 0xff;
	byte SSAP = (byte) 0xff;
	byte CSABME = (byte) 0xf6;
	byte CUA = (byte) 0xc6;
	ByteBuffer buf;

	public byte[] getSABME(){
			
		buf = ByteBuffer.allocate(3);
		buf.put(DSAP);
		buf.put(SSAP);
		buf.put(CSABME);
		
		byte[] result = buf.array();
		
		return result;
			
	}
	
	
	public byte[] getUA(){
		
		buf = ByteBuffer.allocate(3);
		buf.put(DSAP);
		buf.put(SSAP);
		buf.put(CUA);
		byte[] result = buf.array();
		
		return result;
		
		
	}
	
	
	
	
}

