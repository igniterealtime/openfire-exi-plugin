package cl.clayster.exi;

import com.siemens.ct.exi.core.exceptions.EXIException;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import javax.xml.transform.TransformerException;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;

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
		if(writeRequest.getMessage() instanceof IoBuffer){
			msg = Charset.forName("UTF-8").decode(((IoBuffer) writeRequest.getMessage()).buf()).toString();
System.out.println("ENCODING WITH CODECFILTER(" + session.hashCode() + "): " + msg);
			if(msg.startsWith("</stream:stream>")){
				if(session.containsAttribute(EXIAlternativeBindingFilter.flag)){
					msg = "<streamEnd xmlns:exi='http://jabber.org/protocol/compress/exi'/>";
				}
				else{
					msg = "<exi:streamEnd xmlns:exi='http://jabber.org/protocol/compress/exi'/>";
				}
			}
			else if(msg.startsWith("<exi:open")){
				msg = EXIAlternativeBindingFilter.open(null);
			}
			try{
                IoBuffer bb = IoBuffer.allocate(msg.length());
				bb = ((EXIProcessor) session.getAttribute(EXIUtils.EXI_PROCESSOR)).encodeByteBuffer(msg);
                writeRequest.setMessage(bb);
				super.filterWrite(nextFilter, session, writeRequest);
				return;
			} catch (EXIException e){
				e.printStackTrace();
			}
		}
		super.filterWrite(nextFilter, session, writeRequest);
	}

	@Override
	public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception{
		if (message instanceof IoBuffer) {
            IoBuffer byteBuffer = (IoBuffer) message;
			byte[] exiBytes = (byte[]) session.getAttribute("exiBytes");
			if(exiBytes == null){
				exiBytes = new byte[byteBuffer.limit()];
				System.arraycopy(byteBuffer.array(), 0, exiBytes, 0, exiBytes.length);
			}
			else{
				byte[] dest = new byte[exiBytes.length + byteBuffer.limit()];
				System.arraycopy(exiBytes, 0, dest, 0, exiBytes.length);
				System.arraycopy(byteBuffer.array(), 0, dest, exiBytes.length, byteBuffer.limit());
				exiBytes = dest;
			}
			
System.out.println("DECODING(" + session.hashCode() + "): " + EXIUtils.bytesToHex(exiBytes));
			if(!EXIProcessor.isEXI(exiBytes[0])){
				super.messageReceived(nextFilter, session, message);
			}
			else{
				BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(exiBytes));
				while(bis.available() > 0){
					// Decode EXI bytes
					try{
						bis.mark(exiBytes.length);
						String xmlStr = ((EXIProcessor) session.getAttribute(EXIUtils.EXI_PROCESSOR)).decode(bis);
						Element xml = DocumentHelper.parseText(xmlStr).getRootElement();
						session.setAttribute("exiBytes", null); // old bytes have been used with the last message
	System.out.println("DECODED(" + session.hashCode() + "): " + xml.asXML());
						if("open".equals(xml.getName())){
							String open = EXIAlternativeBindingFilter.translateOpen(xml);
							session.write(IoBuffer.wrap(EXIAlternativeBindingFilter.open(open).getBytes()));
							super.messageReceived(nextFilter, session, open);
							return;
						}
						else if("streamEnd".equals(xml.getName())){
							xmlStr = "</stream:stream>";
						}
						super.messageReceived(nextFilter, session, xmlStr);
					} catch (TransformerException e){
						bis.reset();
						byte[] restingBytes = new byte[bis.available()];
						bis.read(restingBytes);
System.out.println("Saving: " + EXIUtils.bytesToHex(restingBytes));
						session.setAttribute("exiBytes", restingBytes);
						super.messageReceived(nextFilter, session, IoBuffer.wrap("".getBytes()));
						return;
					}
				}
            }
        }
	}
	
	@Override
    public void sessionClosed(NextFilter nextFilter, IoSession session) throws Exception {
    	super.sessionClosed(nextFilter, session);
    }

}
