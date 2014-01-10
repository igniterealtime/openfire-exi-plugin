package cl.clayster.exi;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;

/**
 * This class recognizes EXI Alternative Binding. There is only two possible messages that can be received, otherwise the filter will be eliminated from 
 * the current session. In other words, the alternative binding requires EXI messages from the very start. 
 * @author Javier Placencio
 *
 */
public class EXIAlternativeBindingFilter extends IoFilterAdapter {
	
	public static final String filterName = "altBindFilter";
	
	public EXIAlternativeBindingFilter(){}

	@Override
	public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception {
		// Decode the bytebuffer and print it to the stdout
        if (message instanceof ByteBuffer) {
            ByteBuffer byteBuffer = (ByteBuffer) message;
            // Keep current position in the buffer
            int currentPos = byteBuffer.position();
            byte[] ba = new byte[4];
            System.arraycopy(byteBuffer.array(), 0, ba, 0, 4);
            
            if(!EXIProcessor.hasEXICookie(ba)){
            	// Reset to old position in the buffer
                byteBuffer.position(currentPos);
                session.getFilterChain().remove(EXIAlternativeBindingFilter.filterName);
            }
            else{
            	String xml = EXIProcessor.decodeSchemaless(byteBuffer.array()).substring(38);
System.out.println("EXIDECODED schemaless (" + session.hashCode() + "): " + xml);
                if(xml.startsWith("<exi:setup ")){
                	System.out.println("Se recibió <exi:setup>");
                }
                else if(xml.startsWith("<exi:streamStart ")){
                	session.write(message);
                	throw new Exception("exi:streamStart");
                }
                message = xml;
            }
        }
        // Pass the message to the next filter
		super.messageReceived(nextFilter, session, message);
	}
}
