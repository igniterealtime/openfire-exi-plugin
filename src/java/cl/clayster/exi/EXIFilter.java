package cl.clayster.exi;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.nio.NIOConnection;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.util.JiveGlobals;

import com.siemens.ct.exi.exceptions.EXIException;

/**
 * This class is a filter that recognizes EXI sessions and adds an EXIEncoder and an EXIDecoder to those sessions. 
 * It also implements the basic EXI variables shared by the EXIEncoder and EXIDecoder such as the Grammars. 
 *
 * @author Javier Placencio
 */
public class EXIFilter extends IoFilterAdapter {
	
	private static final String setupLocation = "C:/Users/Javier/workspace/Personales/openfire/target/openfire/plugins/exi/res/schemas.xml";
	public static final String EXI_PROCESSOR = EXIProcessor.class.getName();
	public static final String filterName = "exiFilter";
	private boolean enabled = true;
	EXIDecoderFilter exiDecoderFilter = new EXIDecoderFilter();
	
	
	String newExiProcessorXsdLocation;	// se usa para crear el mismo EXIProcessor en EXIEncoderInterceptor, luego se borra

    public EXIFilter(EXIEncoderInterceptor exiEncoderInterceptor) {
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
     * @throws Exception 
     *	
     */
    @Override
    public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception {
    	if(message instanceof String){
    		String msg = ((String) message);
    		if(msg.startsWith("<setup ")){
    			String setupResponse = setupResponse((String) message);
        		if(setupResponse != null){
        			ByteBuffer bb = ByteBuffer.wrap(setupResponse.getBytes());
        	        session.write(bb);
        		}
        		else{
        			System.err.println("An error occurred while processing the negotiation.");
        		}
        		throw new Exception("processed");
    		}
    		else if(msg.startsWith("<compress ")){
    			String compressed = registerExiUser(session);
    			if(compressed != null){
        			ByteBuffer bb = ByteBuffer.wrap(compressed.getBytes());
        	        session.write(bb);
        		}
        		else{
        			System.err.println("An error occurred while processing the negotiation.");
        		}
    			throw new Exception("processed");
    		}
    	}
    	super.messageReceived(nextFilter, session, message);
    	
    }
    
    
    private String setupResponse(String message){
    	List<Element> serverSchemas = new ArrayList<Element>();
		BufferedReader br = null;
		String setupResponse = null;
		try {
			br = new BufferedReader(new FileReader(setupLocation));
			Document document;
			String line = br.readLine();
			
	        while (line != null && line.indexOf("<schema") > -1) {
	        	line = line.substring(line.indexOf("<schema"));	// saltar todo (espacios, etc) hasta el siguiente elemento
	        	document = DocumentHelper.parseText(line);
	            serverSchemas.add(document.getRootElement());
	            line = br.readLine();
	        }
	        br.close();
			
	        document = DocumentHelper.parseText((String) message);
	        Element setup = document.getRootElement();
    		Element schema;
    		boolean ok;
	        for (@SuppressWarnings("unchecked") Iterator<Element> i = setup.elementIterator("schema"); i.hasNext(); ) {
	        	schema = i.next();
	        	ok = false;
	        	for(Element e : serverSchemas){
	        		if(e.attributeValue("ns").equals(schema.attributeValue("ns")) 
		            		&& e.attributeValue("bytes").equals(schema.attributeValue("bytes"))
		            		&& e.attributeValue("md5Hash").equals(schema.attributeValue("md5Hash"))){
	        			ok = true;
		            	break;
		            }
	        	}
	        	if(!ok)	schema.setName("missingSchema");
	        }
	        setup.setName("setupResponse");
	        setupResponse = setup.asXML();
System.out.println(setupResponse);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (DocumentException e) {
			e.printStackTrace();
		}
		return setupResponse;
    }
    
    /**
     * Associates an EXIDecoder and an EXIEncoder to this user's session.
     * 
     * @param session IoSession associated to the user's socket
     * @return 
     */
    public String registerExiUser(IoSession session){
    	// TODO: crear el Canonical Schema
        newExiProcessorXsdLocation = "C:/Users/Javier/workspace/Personales/openfire/target/openfire/plugins/exi/res/canonicalSchema.xsd";
        EXIProcessor exiProcessor;
		try {
			exiProcessor = new EXIProcessor(newExiProcessorXsdLocation);
		} catch (EXIException e) {
			e.printStackTrace();
			return null;
		}
        session.setAttribute(EXI_PROCESSOR, exiProcessor);
		session.getFilterChain().addBefore("xmpp", "exiDecoder", exiDecoderFilter);
    	session.getFilterChain().remove(EXIFilter.filterName);
        
        // TODO: asociar el EXIEncoderInterceptor a la session (xmpp)
        XMPPServer server = XMPPServer.getInstance();
        SessionManager sessionManager = server.getSessionManager();
        NIOConnection conn = (NIOConnection) session.getAttribute("CONNECTION");
        Collection<ClientSession> clientSessions = sessionManager.getSessions();
        LocalClientSession lcl;
        for(ClientSession cl : clientSessions){
        	lcl = (LocalClientSession) cl;
        	if(lcl.getConnection().equals(conn)){
        		EXIPlugin.exiEncoderInterceptor.addEXIProcessor(lcl.getAddress(), exiProcessor);
        	}
        }
        return "<compressed xmlns=\'http://jabber.org/protocol/compress\'/>";
    }
    
}
