package cl.clayster.exi;

import java.io.IOException;

import javax.xml.transform.TransformerException;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;
import org.xml.sax.SAXException;

import com.siemens.ct.exi.exceptions.EXIException;


public class EXIEncoderFilter extends IoFilterAdapter {
	
	public EXIEncoderFilter() {}

	@Override
	public void messageSent(NextFilter nextFilter, IoSession session, Object message) throws Exception {
		String msg = null;
		if (message instanceof ByteBuffer) {
			ByteBuffer byteBuffer = (ByteBuffer) message;
			msg = new String(byteBuffer.array());
System.out.println("XML: " + msg);
			if(!msg.startsWith("<compressed ")){
				try {
					message = ((EXIProcessor) session.getAttribute(EXIFilter.EXI_PROCESSOR)).encodeByteBuffer(msg.substring(0, msg.lastIndexOf('>') + 1));
				} catch (IOException | EXIException | SAXException | TransformerException e) {
					e.printStackTrace();
				}
System.out.println("EXI: " + new String(((ByteBuffer) message).array()));
			}
		}
		super.messageSent(nextFilter, session, message);
	}

}
