package cl.clayster.exi;

import java.io.IOException;

import javax.xml.transform.TransformerException;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoSession;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.Session;
import org.xml.sax.SAXException;
import org.xmpp.packet.Packet;

import com.siemens.ct.exi.exceptions.EXIException;


public class EXIEncoderInterceptor implements PacketInterceptor{
        
        EXIFilter exiFilter;
        
        public EXIEncoderInterceptor(EXIFilter exiFilter) {
                this.exiFilter = exiFilter;
        }
    
        @Override
        public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) throws PacketRejectedException {
                if(exiFilter != null){
                        if(!incoming && !processed && EXIFilter.sessions.containsKey(session.getAddress())){
                                // codificar
                                IoSession ioSession = EXIFilter.sessions.get(session.getAddress());
                                String msg = packet.toXML();
System.out.println("XML(" + msg.length() + "): " + msg);
                                ByteBuffer bb = ByteBuffer.allocate(msg.length());
								try {
									bb = ((EXIProcessor) ioSession.getAttribute(EXIFilter.EXI_PROCESSOR)).encodeByteBuffer(msg.substring(0, msg.lastIndexOf('>') + 1));
//System.out.println("EXI(" + bb.limit() + "): " + new String(bb.array()));
System.out.println("EXI(" + bb.limit() + "): " + EXIUtils.bytesToHex(bb.array()));
									ioSession.write(bb);
									throw new PacketRejectedException("EXI: " + bb);
								} catch (IOException e) {
									e.printStackTrace();
								} catch (EXIException e) {
									e.printStackTrace();
								} catch (SAXException e) {
									e.printStackTrace();
								} catch (TransformerException e) {
									e.printStackTrace();
								}
                        }
                        
                }
        }
        
        
}
