package cl.clayster.exi;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.dom4j.Element;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.Session;
import org.xmpp.packet.Packet;


/**
 *This class proceses XMPP packets in order to identify EXI key stanzas and negotiate a possible EXI connection, according to XEP-0322.
 *<p>
 *
 * @author Javier Placencio
 */
public class EXIInterceptor implements PacketInterceptor{
	
	private Collection<Session> sessions = new ConcurrentLinkedQueue<Session>();
	EXIFilter exiFilter;
	
	public EXIInterceptor(EXIFilter exiFilter) {
		this.exiFilter = exiFilter;
	}
    
	
	@Override
	public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) throws PacketRejectedException {
		if(sessions.contains(session)){
			return;
		}
		if(incoming && !processed){
			// TODO: identificar el <setup> stanza para hacer la negociación, y aceptar (marcar sesión como EXI) o rechazar la conexión EXI

			// TODO: asociar un EXIEncoderInterceptor a esta session y un EXIDecoderFilter a la IoSession correspondiente 
		}
		if(!incoming && !processed){
			// TODO: identificar el <stream:features> stanza para hacer agregar y ofrecer el método de compresión EXI
			System.err.println(packet.getElement().getName());
			if(packet.getElement().getName().equalsIgnoreCase("stream:features")){ 
				Element compression = packet.getElement().element("compression");
				compression.addElement("method").addText("exi");
				System.out.println("FEATURES");
			}
		}
	}
	
	
}
