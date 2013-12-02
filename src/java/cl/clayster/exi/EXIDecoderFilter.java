package cl.clayster.exi;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;
import org.jivesoftware.openfire.net.ClientStanzaHandler;
import org.xmpp.packet.JID;

/**
 *  Decodes EXI stanzas from a specific IoSession, it stores the JID address of the respective user, allowing to easily relate both sessions 
 *  and remove the encoder when the session is closed.
 * 
 * @author Javier Placencio
 *
 */
public class EXIDecoderFilter extends IoFilterAdapter {
	
	JID address;
	
		
	public EXIDecoderFilter(JID address) {
		this.address = address;
	}
	
	public EXIDecoderFilter() {}

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
				if(exiBytes.length > 1){
					// Decode EXI bytes
System.out.println("Decoding EXI message...");
//TODO: reemplazar substring(38) de una forma bonita
					try{
						xml = ((EXIProcessor) session.getAttribute(EXIFilter.EXI_PROCESSOR)).decodeBytes(exiBytes).substring(38);
					} catch (Exception e){
						e.printStackTrace();
					}
System.out.println("EXIDECODED (" + session.hashCode() + "): " + xml);
					if(xml.startsWith("<exi:streamStart ")){
						throw new Exception("<exi:streamStart> PROCESSED!!!!!");
					}
	
		            session.setAttribute("exiBytes", null);
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
