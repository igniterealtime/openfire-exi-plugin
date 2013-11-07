package cl.clayster.exi;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;
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
		
		//TODO: al implementar la negociación (6)
		// revisar el success de EXI y cuando se tenga, agregar la sesion a sessions. 
		// entonces revisar si sessions.contains(session) para ver si decodificar o no el msj.
		
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
					try{
						// Decode EXI bytes
						xml = ((EXIProcessor) session.getAttribute(EXIFilter.EXI_PROCESSOR)).decode(exiBytes);
System.out.println("EXIDECODED (" + session.hashCode() + "): " + xml);
			            session.setAttribute("exiBytes", null);
			            // TODO: reemplazar substring(38) de una forma bonita
			            super.messageReceived(nextFilter, session, ByteBuffer.wrap(xml.substring(38).getBytes()));
			            return;
					}catch(Exception e){
						e.printStackTrace();
						//System.err.println("hay q guardar bytes:" + new String(exiBytes, EXIProcessor.CHARSET));
					}
				}
				session.setAttribute("exiBytes", exiBytes);
            }
			super.messageReceived(nextFilter, session, message);
        }
	}

}
