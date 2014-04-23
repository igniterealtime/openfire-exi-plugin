package cl.clayster.exi;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;

import javax.xml.transform.TransformerException;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

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
				ByteBuffer bb = ByteBuffer.allocate(msg.length());
				bb = ((EXIProcessor) session.getAttribute(EXIUtils.EXI_PROCESSOR)).encodeByteBuffer(msg);
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
			
System.out.println("DECODING(" + session.hashCode() + "): " + EXIUtils.bytesToHex(exiBytes));
			if(!EXIProcessor.isEXI(exiBytes[0])){
				super.messageReceived(nextFilter, session, message);
			}
			else{
				BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(exiBytes));
				while(bis.available() > 0){
					// Decode EXI bytes
					try{
						String xmlStr = ((EXIProcessor) session.getAttribute(EXIUtils.EXI_PROCESSOR)).decode(bis);
						Element xml = DocumentHelper.parseText(xmlStr).getRootElement();
						session.setAttribute("exiBytes", null); // old bytes have been used with the last message
	System.out.println("DECODED(" + session.hashCode() + "): " + xml.asXML());
						if("open".equals(xml.getName())){
							String open = EXIAlternativeBindingFilter.translateOpen(xml);
							session.write(ByteBuffer.wrap(EXIAlternativeBindingFilter.open(open).getBytes()));
							super.messageReceived(nextFilter, session, open);
							return;
						}
						else if("streamEnd".equals(xml.getName())){
							xmlStr = "</stream:stream>";
						}
						super.messageReceived(nextFilter, session, xmlStr);
					} catch (TransformerException e){
						int av = bis.available();
						if(av > 0){
							byte[] restingBytes = new byte[bis.available()];
							bis.read(restingBytes);
	System.out.println("Saving: " + EXIUtils.bytesToHex(restingBytes));
							session.setAttribute("exiBytes", restingBytes);
						}
						else{
	System.out.println("Saving: " + EXIUtils.bytesToHex(exiBytes));
							session.setAttribute("exiBytes", exiBytes);
						}
						super.messageReceived(nextFilter, session, ByteBuffer.wrap("".getBytes()));
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
