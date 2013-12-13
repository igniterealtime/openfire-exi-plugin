package cl.clayster.exi;

import java.nio.charset.Charset;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;
import org.jivesoftware.openfire.net.ClientStanzaHandler;
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
	
	boolean streamStartFlag = false;
	
	public EXICodecFilter() {}
	
	@Override
	public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
		String msg = "";
		if(writeRequest.getMessage() instanceof ByteBuffer){
			msg = Charset.forName("UTF-8").decode(((ByteBuffer) writeRequest.getMessage()).buf()).toString();
			try{
				ByteBuffer bb = ByteBuffer.allocate(msg.length());
				bb = ((EXIProcessor) session.getAttribute(EXIFilter.EXI_PROCESSOR)).encodeByteBuffer(msg);
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
				System.arraycopy(byteBuffer.array(), 0, dest, exiBytes.length, byteBuffer.capacity());
				exiBytes = dest;
			}
			if(EXIProcessor.isEXI(exiBytes[0])){
				if(exiBytes.length > 3){
					// Decode EXI bytes
System.out.println("Decoding EXI message: " + EXIUtils.bytesToHex(exiBytes));
//TODO: reemplazar substring(38) de una forma bonita (elimina <?xml version=1.0.......)
					try{
						xml = ((EXIProcessor) session.getAttribute(EXIFilter.EXI_PROCESSOR)).decodeBytes(exiBytes).substring(38);
System.out.println("EXIDECODED (" + session.hashCode() + "): " + xml);
					} catch (Exception e){
						e.printStackTrace();
					}
					session.setAttribute("exiBytes", null);	// los bytes antiguos ya fueron usados con el utlimo mensaje
					
					if(xml.startsWith("<exi:streamStart ")){
						String streamStart = " <exi:streamStart from='"
								+ JiveGlobals.getProperty("xmpp.domain", "127.0.0.1").toLowerCase()
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
		            super.messageReceived(nextFilter, session, ByteBuffer.wrap(xml.getBytes()));
		            return;
				}
				session.setAttribute("exiBytes", exiBytes);
            }
			super.messageReceived(nextFilter, session, message);
        }
	}
	
	@Override
    public void sessionClosed(NextFilter nextFilter, IoSession session) throws Exception {
    	EXIFilter.sessions.remove(((ClientStanzaHandler) session.getAttribute("HANDLER")).getAddress());
    	super.sessionClosed(nextFilter, session);
    }

}
