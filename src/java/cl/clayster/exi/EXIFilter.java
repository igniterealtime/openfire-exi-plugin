package cl.clayster.exi;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;
import org.jivesoftware.util.JiveGlobals;

/**
 * This class is a filter that recognizes EXI sessions and adds an EXIEncoder and an EXIDecoder to those sessions. 
 * It also implements the basic EXI variables shared by the EXIEncoder and EXIDecoder such as the Grammars. 
 *
 * @author Javier Placencio
 */
public class EXIFilter extends IoFilterAdapter {
	
	public static final String EXI_PROCESSOR = EXIProcessor.class.getName();
	private boolean enabled = true;
	EXIDecoderFilter exiDecoderFilter = new EXIDecoderFilter();
	
	String newExiProcessorXsdLocation;	// se usa para crear el mismo EXIProcessor en EXIEncoderInterceptor, luego se borra

    public EXIFilter() {
        enabled = JiveGlobals.getBooleanProperty("plugin.exi", true);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        JiveGlobals.setProperty("plugin.xmldebugger.", Boolean.toString(enabled)); 
    }
    
    
    /**
     * <p>Identifies EXI sessions (based on distinguishing bits -> should be based on Negotiation) and adds an EXIEncoder and EXIDecoder to that session</p>
     *	
     */
    @Override
    public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception {
    	if(message instanceof ByteBuffer){
    		ByteBuffer byteBuffer = (ByteBuffer) message;
        	byte[] exiBytes = byteBuffer.array();
        	if(EXIProcessor.isEXI(exiBytes[0])){
        	// TODO: cambiará al implementar la negociación (6)
        		newExiProcessorXsdLocation = EXIProcessor.xsdLocation;
        		session.setAttribute(EXI_PROCESSOR, new EXIProcessor(newExiProcessorXsdLocation));
        		session.getFilterChain().addAfter("exiFilter", "exiDecoder", exiDecoderFilter);
            	session.getFilterChain().remove("exiFilter");
        	}
    	}
    	
    	super.messageReceived(nextFilter, session, message);
    }

}
