package cl.clayster.exi;

import java.io.FileWriter;
import java.io.IOException;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;


public class FilePrinterFilter extends IoFilterAdapter {
	
	public static String filterName = "stats";
	
	public FilePrinterFilter(){}
	
	int r = 1;

	@Override
	public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception {
		if(message instanceof String){
			addMsg(EXIUtils.bytesToHex(((String)message).getBytes()));
		}
		else if (message instanceof ByteBuffer) {
    		ByteBuffer byteBuffer = (ByteBuffer) message;
    		// Keep current position in the buffer
            int currentPos = byteBuffer.position();
            
			byte[] msg = byteBuffer.array();
			addMsg(EXIUtils.bytesToHex(msg));
			
			byteBuffer.position(currentPos);
		}
		super.messageReceived(nextFilter, session, message);
	}
	
	private void addMsg(String msg) throws IOException {
		System.out.println("writing: " + msg);
		FileWriter f = new FileWriter("output.txt", true);
		f.write(msg + "\n");
		f.close();
	}
	
}
