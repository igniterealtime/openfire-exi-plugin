package cl.clayster.exi;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;

import java.io.FileWriter;
import java.io.IOException;


public class FilePrinterFilter extends IoFilterAdapter
{
	
	public static String filterName = "stats";
	
	public FilePrinterFilter(){}
	
	int r = 1;

	@Override
	public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception {
		if(message instanceof String){
			addMsg(EXIUtils.bytesToHex(((String)message).getBytes()), session.hashCode());
		}
		else if (message instanceof IoBuffer) {
            IoBuffer byteBuffer = (IoBuffer) message;
    		// Keep current position in the buffer
            int currentPos = byteBuffer.position();
            
			byte[] msg = byteBuffer.array();
			addMsg(EXIUtils.bytesToHex(msg), session.hashCode());
			
			byteBuffer.position(currentPos);
		}
		super.messageReceived(nextFilter, session, message);
	}
	
	private void addMsg(String msg, int id) throws IOException {
		//System.out.println("writing for " + id + ": " + msg);
		FileWriter f = new FileWriter("output" + id + ".txt", true);
		f.write(msg + "\n");
		f.close();
	}
	
}
