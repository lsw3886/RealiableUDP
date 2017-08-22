
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
		
		receive receiver = new receive(socket, port); //수신쓰레드를 생성
		receiver.start();

	}
	
	

}

class receive extends Thread{//수신쓰레드
	
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
	
	public static byte[] hexStringToByteArray(String s) { //16진수 문자열을 바이트 열로 바꾸는 메소드
	    int len = s.length();
	    byte[] data = new byte[len / 2];
	    for (int i = 0; i < len; i += 2) {
	        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
	                             + Character.digit(s.charAt(i+1), 16));
	    }
	    return data;
	}
	
	
	public static String byteArrayToHexString(byte[] bytes){ //바이트열을 16진수 문자열로 바꾸는 메소드
		
		StringBuilder sb = new StringBuilder(); 
		
		for(byte b : bytes){ 
			
			sb.append(String.format("%02X", b&0xff)); 
		} 
		
		return sb.toString(); 
	}
	
	public int checkCRC(byte[] data){//CRC를 체크합니다.
		int len = data.length;
		ByteBuffer buf = ByteBuffer.allocate(len);
		buf.put(data);
		
		int crc = buf.getInt(len-4);
		
		byte[] oridata = new byte[len-4]; 
		
		System.arraycopy(data, 0, oridata, 0, oridata.length);//CRC를 떼어낸 나머지 부분을 뽑아냅니다.
		
	
		CRC32 crc32 = new CRC32();
		crc32.update(oridata);//분리한 부분의 CRC를 만들어냅니다.
		
		int h = (int)crc32.getValue();
		//System.out.println(Integer.toHexString(h));
		
		if(crc == (int)crc32.getValue()){//패킷에 붙어온 CRC와 새로 만든 CRC를 비교합니다.
			
			return 1;//같다면 이상이 없다고 판단하고 1을 RETURN
		}else{
			return 0;//이상있다면 0을 RETURN
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
				Socket.receive(receivePacket); //패킷을 수신합니다.
				buffer.put(receivePacket.getData());
				
				int len = buffer.getShort(12);
				
				byte []dbuf = new byte[len];
				
				System.arraycopy(receivePacket.getData(),0 , dbuf, 0, len);
				
				String xs = byteArrayToHexString(dbuf);
				
				System.out.println("수신받은 패킷 : " + xs); //수신받은 전체 패킷을 출력합니다.
				
				
				
				
				if( checkCRC(dbuf) == 1 ){ //CRC결과 이상이 없다면
					byte[] rr = sframe.getRR(); 
					byte[] ack = llc.getLLC(rr);//ACK를 준비합니다.
					//
					
					byte[] control = new byte[1];
					System.arraycopy(dbuf, 16, control, 0, 1);
					String asdf = byteArrayToHexString(control);
					int con = Integer.parseInt(asdf.replace("0x", ""), 16); //프레임의 CONTROL부분 뽑아냅니다.

					
					
					if(con <128){ //IFRAME이라면,
						byte []Sbuf = new byte[len-22];
						System.arraycopy(dbuf,18 , Sbuf, 0, len-22);
						
				        String msg = new String(Sbuf);
				        System.out.println("수신받은 메세지 : " + msg); //IFRAME 기준으로 INFROMATION을 분리해서 채팅 대화를 출력합니다.
				        AckPacket = new DatagramPacket(ack, ack.length, receivePacket.getAddress(), receivePacket.getPort());
						Socket.send(AckPacket);//ACK를 보냅니다.
						
					}else{//UFRAME - SABME라면(연결)
						System.out.println("SABME를 받았습니다.");
						UFrame uframe = new UFrame();
						buf = uframe.getUA();
						byte[] llcData = llc.getLLC(buf);
						AckPacket = new DatagramPacket(llcData, llcData.length, receivePacket.getAddress(), receivePacket.getPort());
						Socket.send(AckPacket); //UA를 전송합니다.
					}
									
					
					
	
				}else{//CRC검사를 통과하지 못했다면
					byte[] rej = sframe.getREJ();
					byte[] nack = llc.getLLC(rej);
					//REJ를 준비합니다.
					
					AckPacket = new DatagramPacket(nack, nack.length, receivePacket.getAddress(), receivePacket.getPort());
					Socket.send(AckPacket); //NACK패킷을 클라이언트에 전송합니다.

					
					
					
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

class LLC{ //전체 프레임을 구성합니다.
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

class SFrame{//SFRAME을 구성합니다.
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

class UFrame { //UFRAME을 구성합니다.
	
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

