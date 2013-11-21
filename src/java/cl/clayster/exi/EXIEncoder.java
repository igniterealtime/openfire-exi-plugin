package cl.clayster.exi;

import java.io.IOException;

import javax.xml.transform.TransformerException;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.jivesoftware.openfire.nio.XMPPEncoder;
import org.xml.sax.SAXException;

import com.siemens.ct.exi.exceptions.EXIException;


public class EXIEncoder extends XMPPEncoder {
	
	public EXIEncoder() {}

	@Override
	public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception {
		String msg = null;
		if (message instanceof ByteBuffer) {
			ByteBuffer byteBuffer = (ByteBuffer) message;
			msg = new String(byteBuffer.array());
System.out.println("XML: " + msg);
			if(!msg.startsWith("<compressed ")){
				try {
					ByteBuffer bb = ((EXIProcessor) session.getAttribute(EXIFilter.EXI_PROCESSOR)).encodeByteBuffer(msg.substring(0, msg.lastIndexOf('>') + 1));
					out.write(bb);
					super.encode(session, bb, out);
					return;
				} catch (IOException | EXIException | SAXException | TransformerException e) {
					e.printStackTrace();
				}
System.out.println("EXI: " + new String(((ByteBuffer) message).array()));
			}
		}
		super.encode(session, message, out);
	}

}
