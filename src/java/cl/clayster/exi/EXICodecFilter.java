package cl.clayster.exi;

import java.nio.charset.Charset;

import javax.xml.transform.TransformerException;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;
import org.jivesoftware.util.JiveGlobals;

import com.siemens.ct.exi.exceptions.EXIException;

/**
 *  Decodes EXI stanzas from a specific IoSession, it stores the JID address of the respective user, allowing to easily relate both sessions 
 *  and remove the encoder when the session is closed.
 * 
 * @author Javier Placencio
 *
 */
public class EXICodecFilter extends IoFilterAdapter {
	
	public EXICodecFilter() {}
	
	@Override
	public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
		String msg = "";
		if(writeRequest.getMessage() instanceof ByteBuffer){
			msg = Charset.forName("UTF-8").decode(((ByteBuffer) writeRequest.getMessage()).buf()).toString();
System.out.println("ENCODING WITH CODECFILTER: " + msg);
			try{
				ByteBuffer bb = ByteBuffer.allocate(msg.length());
				bb = ((EXIProcessor) session.getAttribute(EXIUtils.EXI_PROCESSOR)).encodeByteBuffer(msg);
System.out.println("ENCODED WITH CODECFILTER: " + EXIUtils.bytesToHex(bb.array()));
				super.filterWrite(nextFilter, session, new WriteRequest(bb, writeRequest.getFuture(), writeRequest.getDestination()));
				return;
			} catch (EXIException e){
				e.printStackTrace();
			}
		}
		super.filterWrite(nextFilter, session, writeRequest);
	}

	@Override
	public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception{
		
		String xml = null;
		if (message instanceof ByteBuffer) {
			ByteBuffer byteBuffer = (ByteBuffer) message;
			byte[] exiBytes = (byte[]) session.getAttribute("exiBytes");
			if(exiBytes == null){
				exiBytes = byteBuffer.array();
			}
			else{
				byte[] dest = new byte[exiBytes.length + byteBuffer.limit()];
				System.arraycopy(exiBytes, 0, dest, 0, exiBytes.length);
				System.arraycopy(byteBuffer.array(), 0, dest, exiBytes.length, byteBuffer.limit());
				exiBytes = dest;				
			}
			if(!EXIProcessor.isEXI(exiBytes[0])){
				super.messageReceived(nextFilter, session, message);
			}
			else{
				// Decode EXI bytes
				try{
					xml = ((EXIProcessor) session.getAttribute(EXIUtils.EXI_PROCESSOR)).decodeByteArray(exiBytes);
				} catch (TransformerException e){
					session.setAttribute("exiBytes", exiBytes);
					super.messageReceived(nextFilter, session, ByteBuffer.wrap("".getBytes()));
					return;
				}
				session.setAttribute("exiBytes", null);	// old bytes have been used with the last message
				
				if(xml.startsWith("<exi:streamStart ")){
					String streamStart = " <exi:streamStart from='"
							+ JiveGlobals.getProperty("xmpp.domain", "localhost").toLowerCase()
							+ "' version='1.0' xml:lang='en' xmlns:exi='http://jabber.org/protocol/compress/exi'>"
							+ "<exi:xmlns prefix='' namespace='jabber:client'/><exi:xmlns prefix='streams' namespace='http://etherx.jabber.org/streams'/>"
							+ "<exi:xmlns prefix='exi' namespace='http://jabber.org/protocol/compress/exi'/></exi:streamStart>";
					session.write(ByteBuffer.wrap(streamStart.getBytes()));
					throw new Exception("<exi:streamStart> PROCESSED!!!!!");
				}
				else if(xml.startsWith("<exi:streamEnd ")){
					xml = "</stream:stream>";
					session.write(exiBytes);
				}
	            super.messageReceived(nextFilter, session, xml);
            }
        }
	}
	
	@Override
    public void sessionClosed(NextFilter nextFilter, IoSession session) throws Exception {
    	super.sessionClosed(nextFilter, session);
    }

}
