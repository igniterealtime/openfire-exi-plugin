package cl.clayster.exi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.util.JiveGlobals;

import com.siemens.ct.exi.exceptions.EXIException;

/**
 * This class is a filter that recognizes EXI sessions and adds an EXIEncoder and an EXIDecoder to those sessions. 
 * It also implements the basic EXI variables shared by the EXIEncoder and EXIDecoder such as the Grammars. 
 *
 * @author Javier Placencio
 */
public class EXIFilter extends IoFilterAdapter {
	
	public static final String EXI_PROCESSOR = EXIProcessor.class.getName();
	public static final String filterName = "exiFilter";
	private boolean enabled = true;

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
    			if(createExiProcessor(session)){
        			ByteBuffer bb = ByteBuffer.wrap("<compressed xmlns=\'http://jabber.org/protocol/compress\'/>".getBytes());
        	        session.write(bb);
        	        addCodec(session);
        		}
        		else{
        			ByteBuffer bb = ByteBuffer.wrap("<failure xmlns=\'http://jabber.org/protocol/compress\'/><setup-failed/></failure>".getBytes());
        	        session.write(bb);
        		}
    			throw new Exception("processed");
    		}
    	}
    	super.messageReceived(nextFilter, session, message);
    	
    }
    
    
    private String setupResponse(String message){
    	File schemasFile = new File(EXIUtils.schemasFileLocation);
    	if(!schemasFile.exists()){
    		try {
				EXIUtils.generateSchemasFile("C:/Users/Javier/workspace/Personales/openfire/target/openfire/plugins/exi/res");
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}
    	
		String setupResponse = null;
		try {
			// obtener el schemas File del servidor y transformarlo a un elemento XML
			Element serverSchemas;
	        BufferedReader br = new BufferedReader(new FileReader(EXIUtils.schemasFileLocation));
	        StringBuilder sb = new StringBuilder();
	        String line = br.readLine();

	        while (line != null) {
	            sb.append(line);
	            line = br.readLine();
	        }
	        br.close();
	        serverSchemas = DocumentHelper.parseText(sb.toString()).getRootElement();
			
	        // transformar el <setup> recibido en un documento xml
	        Element setup = DocumentHelper.parseText((String) message).getRootElement();
    		boolean ok, ready = true;
    		Element auxSchema1, auxSchema2;
    		String ns, bytes, md5Hash;
	        for (@SuppressWarnings("unchecked") Iterator<Element> i = setup.elementIterator("schema"); i.hasNext();) {
	        	auxSchema1 = i.next();
	        	ok = false;
	        	ns = auxSchema1.attributeValue("ns");
	        	bytes = auxSchema1.attributeValue("bytes");
	        	md5Hash = auxSchema1.attributeValue("md5Hash");
	        	for(@SuppressWarnings("unchecked") Iterator<Element> j = serverSchemas.elementIterator("schema"); j.hasNext();){
	        		auxSchema2 = j.next();
	        		if(auxSchema2.attributeValue("ns").equals(ns)
	        				&& auxSchema2.attributeValue("bytes").equals(bytes)
	        				&& auxSchema2.attributeValue("md5Hash").equals(md5Hash)){
	        			ok = true;
		            	break;
		            }
	        	}
	        	if(!ok){
	        		auxSchema1.setName("missingSchema");
	        		ready = false;
	        	}
	        }
	        // TODO: solucionar lo del orden de los import en el canonicalSchema (ns: http://www.w3.org/XML/1998/namespace no puede ir primero??!)
	        if(ready){
	        	try {
					serverSchemas = EXIUtils.generateCanonicalSchema(serverSchemas);
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				}
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
    private boolean createExiProcessor(IoSession session){
        EXIProcessor exiProcessor;
		try {
			exiProcessor = new EXIProcessor(EXIUtils.newExiProcessorXsdLocation);
		} catch (EXIException e) {
			e.printStackTrace();
			return false;
		}
		session.setAttribute(EXI_PROCESSOR, exiProcessor);
		return true;
    }
    
    private void addCodec(IoSession session){
		session.getFilterChain().addBefore(EXIFilter.filterName, "exiEncoder", new EXIEncoderFilter());
        // add EXIDecoder, which manages the EXISessions
		session.getFilterChain().addBefore("xmpp", "exiDecoder", new EXIDecoderFilter());
    	session.getFilterChain().remove(EXIFilter.filterName);
        return;
    }
}
