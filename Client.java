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
			System.out.println("������ Ʋ�Ⱦ��. ����1 : localhost, ����2:1301");
			System.exit(0);
		}
		InetAddress ia = InetAddress.getByName(args[0]); //�ּҸ� �޽��ϴ�.
		int port = Integer.parseInt(args[1]);// port�� �޽��ϴ�.
		LLC llc = new LLC(); 
		ACKmanager ac = new ACKmanager(); //ACK�� ���ο� ���� senderThread�� �����ϴ� Ŭ�����Դϴ�.
		SenderThread sender = new SenderThread(ia, port, ac, llc); //�޽��� �۽� ������ ����
		sender.start();
		ReceiverThread receiver = new ReceiverThread(sender.getSocket(), ac, llc);// �޽��� ���� ������ ����
		receiver.start();
		
		
	}

}


class SenderThread extends Thread{  //�޽��� ���� ������
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
	    this.socket.connect(server, port); // �Է¹޾Ҵ� ������ �������� ������ �����մϴ�.
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
	      socket.send(outputP); //SABME ����
	      System.out.println("SABME�� �����մϴ�. UA�� ��ٸ��ϴ�.");
	      CTimer UAtimer = new CTimer(this.socket, outputP, ACKM); // TimeOut�� ������ �����带 �����մϴ�.
	      UAtimer.start();//Ÿ�̸� �����带 �����մϴ�.

	      this.ACKM.waitingACK();
	      IFrame iframe = new IFrame();
	      
	      while (true) {
	        if (stopped)
	          return;
	        

	        String theLine = userInput.readLine(); //Ű���� �Էºκ�
	        if (theLine.equals("."))
	          break;
	        byte[] keydata = theLine.getBytes();
	        byte[] framedata = iframe.getIFrame(keydata);
	        byte[] data = llc.getLLC(framedata);
	        
	        outputP = new DatagramPacket(data, data.length, server, port); // ������ ��Ŷ
	        CTimer timer = new CTimer(this.socket, outputP, ACKM); // TimeOut�� ������ �����带 �����մϴ�.
	        socket.send(outputP);//��Ŷ�� �����մϴ�.
	        timer.start();//Ÿ�̸� �����带 �����մϴ�.

	        this.ACKM.waitingACK(); // ��Ŷ�� �����Ͽ����Ƿ�, ACK�� ��ٸ��ϴ�. ACK�� �� ������ ���۾������ ���ϴ�.
	        
	        
	        
	        Thread.yield();
	      }
	    }
	    catch (IOException ex) {
		      System.err.println(ex);
	    } 
	  }
	
}


class ReceiverThread extends Thread{ // ���� ������
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
	      DatagramPacket dp = new DatagramPacket(buffer, buffer.length);//���Ź��� ��Ŷ �غ�
		  ByteBuffer buf = ByteBuffer.allocate(600);

	      try {
	    	
	        socket.receive(dp); //������ �޽��ϴ�.
	        
	        buf.put(dp.getData());
	        buffer = buf.array();
	        byte control = buf.get(16);
	        int len = buf.getShort(12);
			
			byte []dbuf = new byte[len];
			System.arraycopy(dp.getData(),0 , dbuf, 0, len);
			String xs = byteArrayToHexString(dbuf);
			System.out.println("���۹��� ��ü ������ : " + xs);     
	        	        
	        String pcon = String.format("0x%x", control);// control �Ǻ�
	        //System.out.println(pcon);
	        if(checkCRC(dbuf)==1){
		        if (pcon.equals("0x80")){ // ACK�޽����� �޾Ҵٸ� 
		        	
		        	System.out.println("ACK is received"); //ACK�� �޾ҽ��ϴ�.
		        	ACKM.confirmACK();//ACK�� �޾����Ƿ�, senderThread�� �ٽ� �����մϴ�.
	
		        	
		        }else if(pcon.equals("0xc6")){//UA��� �Ǵ��� �Ǹ�
		        	
		        	System.out.println("UA�� �޾ҽ��ϴ�. connected!"); // ����Ǿ��ٰ� ���.
		        	
		        		        	
		        	ACKM.confirmACK();//�۽ž����带 ������ Ű�����Է¹��� �غ� �մϴ�.
		        	
		        	byte[] DAarr = new byte[6];
		        	byte[] SAarr = new byte[6];
		        	System.arraycopy(dbuf, 0, DAarr, 0, 6);
		        	System.arraycopy(dbuf, 6, SAarr, 0, 6);
		        	this.llc.DA = DAarr;
		        	this.llc.SA = SAarr;
		        }
		        
		        else if(pcon.equals("0x90")){ //REJ��� �Ǵ��� �Ǹ�
			        System.out.println("REJ���߽��ϴ�. 5���� �������մϴ�.");
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
	//ACK���ſ� ���� senderThread�� �����ϴ� Ŭ����
	int ACK = 0;
	void confirmACK(){ //receive���� ����� �޼ҵ�, ���ڰ� �ִ� sender�����带 ����ϴ�.
		synchronized (this) {
			ACK=1;
			notify();
		
		}
	}
	
	void waitingACK(){//sender���� �� �޼ҵ�, sender�� ��� �� ���ϴ�.
		
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


class CTimer extends Thread{ //Ÿ�Ӿƿ��� �� ������ �������Դϴ�.
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
				Thread.sleep(5000); //Ÿ�Ӿƿ� ���ѽð� 5��
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(count ==2){
				System.out.println("������ Ƚ�� �ʰ�, �޽����� �ٽ� �Է��ϼ���.");
				count = 0;
				ACKm.confirmACK();
				break;
				
			}else{
				if(ACKm.ACK == -1){
					try { //ACK�� �������� �ʾ�����
						System.out.println("������");
						count += 1;
						socket.send(packet); //�������մϴ�.
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}else if (ACKm.ACK == 1) {
					break;//ACK�� �����Ͽ��ٸ�, Timer�� �����ϴ�.
					
				}else{
					
				}
			}
			
		}
		
			
		
		
	}
	
	
}

class LLC{//LLC�� �����մϴ�.
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

class IFrame{//IFRAME�� �����մϴ�.

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




