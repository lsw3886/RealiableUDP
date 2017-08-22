import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.BufferPoolMXBean;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;


import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;

public class Client {
	final static int MAX = 65536;
	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.out.println("실행방법 틀렸어요. 인자1 : localhost, 인자2:1301");
			System.exit(0);
		}
		InetAddress ia = InetAddress.getByName(args[0]); //주소를 받습니다.
		int port = Integer.parseInt(args[1]);// port를 받습니다.
		LLC llc = new LLC(); 
		ACKmanager ac = new ACKmanager(); //ACK의 여부에 따라 senderThread를 관리하는 클래스입니다.
		SenderThread sender = new SenderThread(ia, port, ac, llc); //메시지 송신 쓰레드 생성
		sender.start();
		ReceiverThread receiver = new ReceiverThread(sender.getSocket(), ac, llc);// 메시지 수신 쓰레드 생성
		receiver.start();
		
		
	}

}


class SenderThread extends Thread{  //메시지 전송 쓰레드
	  private InetAddress server;

	  private DatagramSocket socket;
	  
	  private boolean stopped = false;
	  ACKmanager ACKM;
	  private int port;
	  DatagramPacket outputP;
	  LLC llc = new LLC();
	  
	  public DatagramSocket getSocket() {
		    return this.socket;
	  }
	  public void halt() {
		    this.stopped = true;
		  }
	  
	  public SenderThread(InetAddress address, int port, ACKmanager ac, LLC llc) throws SocketException {
	    this.server = address;
	    this.port = port;
	    this.socket = new DatagramSocket();
	    this.socket.connect(server, port); // 입력받았던 정보를 바탕으로 소켓을 연결합니다.
	    this.ACKM = ac;
	    this.llc = llc;
	  }

	  
	public void run() {

	    try {
	      BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
	      UFrame uframe = new UFrame();
	      byte[] Uframedata = uframe.getSABME(); 
	      byte[] connect = llc.getLLC(Uframedata);
	      
	      outputP = new DatagramPacket(connect, connect.length, server, port);
	      socket.send(outputP); //SABME 전송
	      System.out.println("SABME를 전송합니다. UA를 기다립니다.");
	      CTimer UAtimer = new CTimer(this.socket, outputP, ACKM); // TimeOut를 관리할 쓰레드를 생성합니다.
	      UAtimer.start();//타이머 쓰레드를 시작합니다.

	      this.ACKM.waitingACK();
	      IFrame iframe = new IFrame();
	      
	      while (true) {
	        if (stopped)
	          return;
	        

	        String theLine = userInput.readLine(); //키보드 입력부분
	        if (theLine.equals("."))
	          break;
	        byte[] keydata = theLine.getBytes();
	        byte[] framedata = iframe.getIFrame(keydata);
	        byte[] data = llc.getLLC(framedata);
	        
	        outputP = new DatagramPacket(data, data.length, server, port); // 전송할 패킷
	        CTimer timer = new CTimer(this.socket, outputP, ACKM); // TimeOut를 관리할 쓰레드를 생성합니다.
	        socket.send(outputP);//패킷을 전송합니다.
	        timer.start();//타이머 쓰레드를 시작합니다.

	        this.ACKM.waitingACK(); // 패킷을 전송하였으므로, ACK를 기다립니다. ACK가 올 때까지 전송쓰레드는 쉽니다.
	        
	        
	        
	        Thread.yield();
	      }
	    }
	    catch (IOException ex) {
		      System.err.println(ex);
	    } 
	  }
	
}


class ReceiverThread extends Thread{ // 수신 쓰레드
	  DatagramSocket socket;
	  ACKmanager ACKM;
	  private boolean stopped = false;
	  LLC llc = new LLC();

	  public ReceiverThread(DatagramSocket ds, ACKmanager ac, LLC llc) throws SocketException {
	    this.socket = ds;
	    this.ACKM = ac; 
	    this.llc = llc;
	    
	  }
	  public void halt() {
		    this.stopped = true;
		  }
	  public static byte[] hexStringToByteArray(String s) {
		    int len = s.length();
		    byte[] data = new byte[len / 2];
		    for (int i = 0; i < len; i += 2) {
		        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
		                             + Character.digit(s.charAt(i+1), 16));
		    }
		    return data;
		}
		
		
		public static String byteArrayToHexString(byte[] bytes){ 
			
			StringBuilder sb = new StringBuilder(); 
			
			for(byte b : bytes){ 
				
				sb.append(String.format("%02X", b&0xff)); 
			} 
			
			return sb.toString(); 
		}
		
		public int checkCRC(byte[] data){
			int len = data.length;
			ByteBuffer buf = ByteBuffer.allocate(len);
			buf.put(data);
			
			int crc = buf.getInt(len-4);
			
			byte[] oridata = new byte[len-4];
			
			System.arraycopy(data, 0, oridata, 0, oridata.length);
			
		
			CRC32 crc32 = new CRC32();
			crc32.update(oridata);
			
			int h = (int)crc32.getValue();
			//System.out.println(Integer.toHexString(h));
			
			if(crc == (int)crc32.getValue()){
				
				return 1;
			}else{
				return 0;
			}
		}
		
		
	  public void run() {
	    byte[] buffer = new byte[600];
	    
	    while (true) {
	      if (stopped)
	        return; 
	      DatagramPacket dp = new DatagramPacket(buffer, buffer.length);//수신받을 패킷 준비
		  ByteBuffer buf = ByteBuffer.allocate(600);

	      try {
	    	
	        socket.receive(dp); //수신을 받습니다.
	        
	        buf.put(dp.getData());
	        buffer = buf.array();
	        byte control = buf.get(16);
	        int len = buf.getShort(12);
			
			byte []dbuf = new byte[len];
			System.arraycopy(dp.getData(),0 , dbuf, 0, len);
			String xs = byteArrayToHexString(dbuf);
			System.out.println("전송받은 전체 프레임 : " + xs);     
	        	        
	        String pcon = String.format("0x%x", control);// control 판별
	        //System.out.println(pcon);
	        if(checkCRC(dbuf)==1){
		        if (pcon.equals("0x80")){ // ACK메시지를 받았다면 
		        	
		        	System.out.println("ACK is received"); //ACK를 받았습니다.
		        	ACKM.confirmACK();//ACK를 받았으므로, senderThread를 다시 시작합니다.
	
		        	
		        }else if(pcon.equals("0xc6")){//UA라고 판단이 되면
		        	
		        	System.out.println("UA를 받았습니다. connected!"); // 연결되었다고 출력.
		        	
		        		        	
		        	ACKM.confirmACK();//송신쓰레드를 깨워서 키보드입력받을 준비를 합니다.
		        	
		        	byte[] DAarr = new byte[6];
		        	byte[] SAarr = new byte[6];
		        	System.arraycopy(dbuf, 0, DAarr, 0, 6);
		        	System.arraycopy(dbuf, 6, SAarr, 0, 6);
		        	this.llc.DA = DAarr;
		        	this.llc.SA = SAarr;
		        }
		        
		        else if(pcon.equals("0x90")){ //REJ라고 판단이 되면
			        System.out.println("REJ당했습니다. 5초후 재전송합니다.");
		        }
		        else{
		        	
		        }
	        }else{
	        	
	        }
	        Thread.yield();
	       
	      } catch (IOException ex) {
	        System.err.println(ex);
	      }
	    }
	  }
	  
	  

}

class ACKmanager{
	//ACK수신에 따라 senderThread를 관리하는 클래스
	int ACK = 0;
	void confirmACK(){ //receive에서 사용할 메소드, 잠자고 있는 sender쓰레드를 깨웁니다.
		synchronized (this) {
			ACK=1;
			notify();
		
		}
	}
	
	void waitingACK(){//sender에서 쓸 메소드, sender를 재울 때 씁니다.
		
		synchronized(this){
			try {
				ACK=-1;
				wait();
				
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	
}


class CTimer extends Thread{ //타임아웃을 할 관리할 쓰레드입니다.
	DatagramSocket socket;
	DatagramPacket packet;
	ACKmanager ACKm;
	public CTimer (DatagramSocket socket, DatagramPacket packet, ACKmanager ACKm){
		this.socket= socket;
		this.packet = packet;
		this.ACKm = ACKm;
	}
	
	public void run(){

		int count = 0;

		while(true){

			try {
				Thread.sleep(5000); //타임아웃 제한시간 5초
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(count ==2){
				System.out.println("재전송 횟수 초과, 메시지를 다시 입력하세요.");
				count = 0;
				ACKm.confirmACK();
				break;
				
			}else{
				if(ACKm.ACK == -1){
					try { //ACK가 도착하지 않았을때
						System.out.println("재전송");
						count += 1;
						socket.send(packet); //재전송합니다.
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}else if (ACKm.ACK == 1) {
					break;//ACK가 도착하였다면, Timer를 나갑니다.
					
				}else{
					
				}
			}
			
		}
		
			
		
		
	}
	
	
}

class LLC{//LLC를 구성합니다.
	byte[] DA = {(byte) 0x00,(byte) 0x00,(byte) 0x00,(byte) 0x00,(byte) 0x00,(byte) 0x00};
	byte[] SA = {(byte) 0xaa,(byte) 0xbb,(byte) 0xcc,(byte) 0xdd,(byte) 0xee,(byte) 0xff};
	byte[] DataAndPadding = null;
	byte[] CRC32 = {(byte)0xbb,(byte)0xbb,(byte)0xbb,(byte)0xbb};
	
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
	
	public static String byteArrayToHexString(byte[] bytes){ 
		
		StringBuilder sb = new StringBuilder(); 
		
		for(byte b : bytes){ 
			
			sb.append(String.format("%02X", b&0xff)); 
		} 
		
		return sb.toString(); 
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

class IFrame{//IFRAME을 구성합니다.

	byte DSAP = (byte) 0xff;
	byte SSAP = (byte) 0xff;
	byte NR = (byte) 0x00;
	byte NS = (byte) 0x00;
	int length= 0;
	
	ByteBuffer buf;
	
	public byte[] getIFrame(byte[] info){
		buf = ByteBuffer.allocate(4 + info.length);
		setSeq();
		
		buf.put(DSAP);
		buf.put(SSAP);
		buf.put(NS);
		buf.put(NR);
		buf.put(info);
		
		byte[] result = buf.array();
		this.length = result.length;
		
		return result;
		
		
		
		
	}
	
	void setSeq(){
		int nss = this.NS;
		
		if(nss==127){
			nss = 0;
		}else{
			nss+=1;
		}
		
		this.NS = (byte)nss;
		
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
		buf.put(CRR);
		buf.put(NR);
		byte[] result = buf.array();
		
		return result;
		
		
	}
	
	void setSeq(){
		int nrs = this.NR;
		
		if(nrs==127){
			nrs = 0;
		}else{
			nrs+= nrs+1;
		}
		
		this.NR = (byte)nrs;
		
	}
	
}




