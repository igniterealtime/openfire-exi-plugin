package cl.clayster.exi;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.xml.transform.TransformerException;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.xerces.impl.dv.util.Base64;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.util.JiveGlobals;
import org.xml.sax.SAXException;

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
    
    @Override
    public void messageSent(NextFilter nextFilter, IoSession session, Object message) throws Exception {
    	
		if (message instanceof ByteBuffer) {
			ByteBuffer byteBuffer = (ByteBuffer) message;
			String msg = new String(byteBuffer.array());
			if(msg.startsWith("<stream:features") && msg.contains("</compression>")){
				int i = msg.indexOf("</compression>");
				msg = msg.substring(0, i)
						.concat("<method>exi</method>")
						.concat(msg.substring(i, msg.length()));
				message = msg.getBytes();
			}
    	}
    	super.messageSent(nextFilter, session, message);
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
    			String setupResponse = setupResponse((String) message, session);
        		if(setupResponse != null){
        			ByteBuffer bb = ByteBuffer.wrap(setupResponse.getBytes());
        	        session.write(bb);
        		}
        		else{
        			System.err.println("An error occurred while processing the negotiation.");
        		}
        		throw new Exception("<setup> PROCESSED!!!!!");
    		}
    		else if(msg.startsWith("<compress ")){
    			if(createExiProcessor(session)){
    				String respuesta = "<compressed xmlns='http://jabber.org/protocol/compress' configurationId='" + session.hashCode() + "'/>";
        			ByteBuffer bb = ByteBuffer.wrap(respuesta.getBytes());
        	        session.write(bb);
        	        addCodec(session);
        		}
        		else{
        			ByteBuffer bb = ByteBuffer.wrap("<failure xmlns=\'http://jabber.org/protocol/compress\'><setup-failed/></failure>".getBytes());
        	        session.write(bb);
        		}
    			throw new Exception("<compress> PROCESSED!!!!!");
    		}
    		else if(msg.startsWith("<uploadSchema ")){
    			uploadMissingSchema(msg, session);
    			throw new Exception("<uploadSchema> processed");
    		}
    		else if(msg.startsWith("<downloadSchema ")){
    			String url = EXIUtils.getAttributeValue(msg, "url");
    			if(url != null){
    				String respuesta = "";
    				try{
    					String descarga = EXIUtils.downloadXml(url);
		    			if(descarga.startsWith("<downloadSchemaResponse ")){
		    				// error already found during download process
		    				respuesta = descarga;
		    			}
		    			else{	// SUCCESS!
		    				saveDownloadedSchema(descarga, session);
		    				respuesta = "<downloadSchemaResponse xmlns='http://jabber.org/protocol/compress/exi' url='" + url + "' result='true'/>";
		    			}
    				}catch (DocumentException e){	// error while parsing the just saved file, not probable (error makes sense while uploading)
    					respuesta = "<downloadSchemaResponse xmlns='http://jabber.org/protocol/compress/exi' url='" + url
    							+ "' result='false'><invalidContentType contentTypeReturned='text/html'/></downloadSchemaResponse>";
    				}catch (Exception e){
        				respuesta = "<downloadSchemaResponse xmlns='http://jabber.org/protocol/compress/exi' url='" + url
        						+ "' result='false'><error message='No free space left.'/></downloadSchemaResponse>";
        			}
	    			session.write(ByteBuffer.wrap((respuesta).getBytes()));
	    			throw new Exception("<downloadSchemaResponse> PROCESSED!!!!!");
    			}
    		}
    	}
    	super.messageReceived(nextFilter, session, message);
    	
    }

/** Setup **/

	private String setupResponse(String message, IoSession session){
		String setupResponse = null;
		try{
			Element setup = DocumentHelper.parseText((String) message).getRootElement();
			String configId = setup.attributeValue("configurationId"); 
			if(configId != null){
				String agreement;
				if(new File(EXIUtils.exiSchemasFolder + "canonicalSchema_" + configId + ".xsd").exists())
					agreement = "true";
				else{
					agreement = "false";
				}
				return "<setupResponse xmlns='http://jabber.org/protocol/compress/exi' agreement='" + agreement + "' configurationId='" + configId + "'/>";
			}
		} catch (DocumentException e){
			e.printStackTrace();
		}
		
		try {
			generateSchemasFile(EXIUtils.schemasFolder);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			// obtener el schemas File del servidor y transformarlo a un elemento XML
			Element serverSchemas;
	        String schemasFileContent = EXIUtils.readFile(EXIUtils.schemasFileLocation);
	        if(schemasFileContent == null){
	        	return null;
	        }
	        serverSchemas = DocumentHelper.parseText(schemasFileContent).getRootElement();
			
	        // transformar el <setup> recibido en un documento xml
	        Element setup = DocumentHelper.parseText((String) message).getRootElement();
    		boolean missingSchema;
    		Element auxSchema1, auxSchema2;
    		String ns, bytes, md5Hash;
	        for (@SuppressWarnings("unchecked") Iterator<Element> i = setup.elementIterator("schema"); i.hasNext();) {
	        	auxSchema1 = i.next();
	        	missingSchema = true;
	        	ns = auxSchema1.attributeValue("ns");
	        	bytes = auxSchema1.attributeValue("bytes");
	        	md5Hash = auxSchema1.attributeValue("md5Hash");
	        	for(@SuppressWarnings("unchecked") Iterator<Element> j = serverSchemas.elementIterator("schema"); j.hasNext();){
	        		auxSchema2 = j.next();
	        		if(auxSchema2.attributeValue("ns").equals(ns)
	        				&& auxSchema2.attributeValue("bytes").equals(bytes)
	        				&& auxSchema2.attributeValue("md5Hash").equals(md5Hash)){
	        			missingSchema = false;
		            	break;
		            }
	        	}
	        	if(missingSchema){
	        		auxSchema1.setName("missingSchema");
	        	}
	        }
	        // TODO: solucionar lo del orden de los import en el canonicalSchema (ns: http://www.w3.org/XML/1998/namespace no puede ir primero??!)
	        
        	serverSchemas = generateCanonicalSchema(serverSchemas, session);
	        setup.setName("setupResponse");
	        setupResponse = setup.asXML();
		} catch (FileNotFoundException e1) {
			return null;
		} catch (IOException e) {
			return null;
		} catch (DocumentException e) {
			return null;
		}
		return setupResponse;
    }
	
	/**
	 * Looks for all schema files (*.xsd) in the given folder and creates two new files: 
	 * a canonical schema file which imports all existing schema files;
	 * and an xml file called schema.xml which contains each schema namespace, file size in bytes and its md5Hash code 
	 *   
	 * 
	 * @param folderLocation
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 */
	private void generateSchemasFile(String folderLocation) throws NoSuchAlgorithmException, IOException {
		File folder = new File(folderLocation);
        File[] listOfFiles = folder.listFiles();
        File file;
        String fileLocation;
        
		MessageDigest md = MessageDigest.getInstance("MD5");
		InputStream is;
		DigestInputStream dis;
		
		String namespace = null, md5Hash = null;
		int r;
		
		// variables to write the stanzas in the right order (namepsace alfabethical order)
        List<String> namespaces = new ArrayList<String>();		
        HashMap<String, String> schemasStanzas = new HashMap<String, String>();	
        int n = 0;
            
            for (int i = 0; i < listOfFiles.length; i++) {
            	file = listOfFiles[i];
            	if (file.isFile() && file.getName().endsWith(".xsd") && !file.getName().endsWith("canonicalSchema.xsd")) {
            	// se hace lo siguiente para cada archivo XSD en la carpeta folder	
            		fileLocation = file.getAbsolutePath();
					r = 0;
					md.reset();
					StringBuilder sb = new StringBuilder();
	            	
					if(fileLocation == null)	break;
					is = Files.newInputStream(Paths.get(fileLocation));
					dis = new DigestInputStream(is, md);
					
					// leer el archivo y guardarlo en sb
					while(r != -1){
						r = dis.read();
						sb.append((char)r);
					}
					
					// buscar el namespace del schema
					namespace = EXIUtils.getAttributeValue(sb.toString(), "targetNamespace");
					md5Hash = EXIUtils.bytesToHex(md.digest());
	
					n = 0;
					while(n < namespaces.size() &&
							namespaces.get(n) != null &&
							namespaces.get(n)
							.compareToIgnoreCase(namespace) <= 0){
						n++;
					}
					namespaces.add(n, namespace);
					// schemasStanzas also contains schemaLocation to make it easier to generate a new canonicalSchema later
					schemasStanzas.put(namespace, "<schema ns='" + namespace + "' bytes='" + file.length() + "' md5Hash='" + md5Hash + "' schemaLocation='" + fileLocation + "'/>");
            	}
			}
            //variables to write the stanzas and canonicalSchema files
            BufferedWriter stanzasWriter = null;
            File stanzasFile = new File(EXIUtils.schemasFileLocation);
            stanzasWriter = new BufferedWriter(new FileWriter(stanzasFile));
            
            stanzasWriter.write("<setupResponse>");
            for(String ns : namespaces){
            	stanzasWriter.write("\n\t" + schemasStanzas.get(ns));
            }
            stanzasWriter.write("\n</setupResponse>");
			stanzasWriter.close();
	}
	
	/**
	 * Generates a canonical schema out of the schemas' namespaces sent in the <setup> stanza during EXI compression negotiation. 
	 * Once the server makes sure that it has all schemas needed, it creates a specific canonical schema for the connection being negotiated.
	 * It takes the location from a general canonical schema which includes all the schemas contained in a given folder.
	 * 
	 * @param schemasStanzas
	 * @throws IOException
	 */
	private Element generateCanonicalSchema(Element setup, IoSession session) throws IOException {
		File newCanonicalSchema = new File(EXIUtils.exiSchemasFolder + "canonicalSchema_" + session.hashCode() + ".xsd");
        BufferedWriter newCanonicalSchemaWriter = new BufferedWriter(new FileWriter(newCanonicalSchema));
        newCanonicalSchemaWriter.write("<?xml version='1.0' encoding='UTF-8'?> \n\n<xs:schema \n\txmlns:xs='http://www.w3.org/2001/XMLSchema' \n\ttargetNamespace='urn:xmpp:exi:cs' \n\txmlns='urn:xmpp:exi:cs' \n\telementFormDefault='qualified'>\n");
        
		Element schema;
        for (@SuppressWarnings("unchecked") Iterator<Element> i = setup.elementIterator("schema"); i.hasNext(); ) {
        	schema = i.next();
        	newCanonicalSchemaWriter.write("\n\t<xs:import namespace='" + schema.attributeValue("ns") + "' schemaLocation='" + schema.attributeValue("schemaLocation") + "'/>");
        	schema.remove(schema.attribute("schemaLocation"));
        }
        newCanonicalSchemaWriter.write("\n</xs:schema>");
        newCanonicalSchemaWriter.close();
        
        session.setAttribute(EXIUtils.CANONICAL_SCHEMA_LOCATION, newCanonicalSchema.getAbsolutePath());
        return setup;
	}
	
	
/** Compress **/
	/**
     * Associates an EXIDecoder and an EXIEncoder to this user's session.
     * 
     * @param session IoSession associated to the user's socket
     * @return 
     */
    private boolean createExiProcessor(IoSession session){
        EXIProcessor exiProcessor;
		try {
			exiProcessor = new EXIProcessor((String)session.getAttribute(EXIUtils.CANONICAL_SCHEMA_LOCATION));
		} catch (EXIException e) {
			e.printStackTrace();
			return false;
		}
		session.setAttribute(EXI_PROCESSOR, exiProcessor);
		return true;
    }
    
    /**
     * Adds an EXIEncoder as well as an EXIDecoder to the given IoSession
     * @param session the IoSession where the EXI encoder and decoder will be added to.
     */
    private void addCodec(IoSession session){
		/*
		session.getFilterChain().addAfter("tls", "exiEncoder", new EXIEncoderFilter());
		session.getFilterChain().addBefore("xmpp", "exiDecoder", new EXIDecoderFilter());
    	*/
    	session.getFilterChain().remove("xmpp");
    	session.getFilterChain().addAfter(EXIFilter.filterName, "exiCodec", new ProtocolCodecFilter(new EXICodecFactory()));
    	
    	session.getFilterChain().remove(EXIFilter.filterName);
        return;
    }
    
    
/** uploadSchema **/
    
    /**
     * Saves a new schema file on the server, which is sent using a Base64 encoding by an EXI client. 
     * The name of the file is related to the time when the file was saved.
     * 
     * @param content the content of the uploaded schema file (base64 encoded)
     * @return The absolute pathname string denoting the newly created schema file.
     * @throws IOException while trying to decode the file content using Base64
     * @throws DocumentException 
     * @throws NoSuchAlgorithmException 
     * @throws TransformerException 
     * @throws SAXException 
     * @throws EXIException 
     */
    private void uploadMissingSchema(String content, IoSession session) 
    		throws IOException, NoSuchAlgorithmException, DocumentException, EXIException, SAXException, TransformerException{
    	String filePath = EXIUtils.schemasFolder + Calendar.getInstance().getTimeInMillis() + ".xsd";
    	OutputStream out = new FileOutputStream(filePath);
    	String md5Hash = null;
		String bytes = null;
    	
    	String attributes = content.substring(0, content.indexOf('>') + 1);
    	content = content.substring(content.indexOf('>') + 1, content.indexOf("</"));
    	String contentType = EXIUtils.getAttributeValue(attributes, "contentType");
    	md5Hash = EXIUtils.getAttributeValue(attributes, "md5Hash");
		bytes = EXIUtils.getAttributeValue(attributes, "bytes");
		
		byte[] outputBytes = content.getBytes();
		
    	if((contentType != null && !contentType.equals("text")) && md5Hash != null && bytes != null){
			if(contentType.equals("ExiBody")){
    			content = EXIProcessor.decodeSchemaless(content.getBytes());
    			outputBytes = content.getBytes();
    		}
    		else if(contentType.equals("ExiDocument")){
    			// TODO
    		}
    		
    	}
    	else {
	    	outputBytes = Base64.decode(content);
    	}
    	out.write(outputBytes);
    	out.close();
    	
		String ns = addNewSchemaToSchemasFile(filePath, md5Hash, bytes);
		addNewSchemaToCanonicalSchema(filePath, ns, session);
	}
    
    private String addNewSchemaToSchemasFile(String fileLocation, String md5Hash, String bytes) throws NoSuchAlgorithmException, IOException, DocumentException {
    	MessageDigest md = MessageDigest.getInstance("MD5");
    	File file = new File(fileLocation);
    	if(md5Hash == null || bytes == null){
    		md5Hash = EXIUtils.bytesToHex(md.digest(Files.readAllBytes(file.toPath())));
    	}
		String ns = EXIUtils.getAttributeValue(EXIUtils.readFile(fileLocation), "targetNamespace");
		
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
        
        Element auxSchema;
        @SuppressWarnings("unchecked")
		Iterator<Element> j = serverSchemas.elementIterator("schema");
        int i = 0;	// índice donde debe ir el nuevo schema (en la lista de schemas)
        if(j.hasNext()){
        	while(j.hasNext()){
        		auxSchema = j.next();
				if(ns.compareToIgnoreCase(auxSchema.attributeValue("ns")) < 0){
					// se debe quedar en esta posición
					break;
				}
				i++;	// debe aumentar su posición solo si es mayor al último namespace comparado
            }
        }
        else{	// no hay ningún schema	(sólo el nuevo)
        	generateSchemasFile(EXIUtils.schemasFolder);
        	return ns;
        }
        
        int i2 = 0; // índice donde debe ir el nuevo schema (en el archivo)
    	for (int k = -1 ; k < i ; k++){
        	i2 = sb.indexOf("<schema ", i2 + 1);
        }
    	
    	String schema = "<schema ns='" + ns + "' bytes='" + ((bytes == null) ? file.length() : bytes) + "' md5Hash='" + md5Hash + "' schemaLocation='" + fileLocation + "'/>";
    	if(i2 == -1){	// debe ir despues de todos (no siguió encontrando schemas)
    		sb.insert(sb.indexOf("</setup"), schema);
    	}
    	else{	// debe ir antes de uno que se encontro en i2
    		sb.insert(i2, schema);
    	}
    	
        
    	BufferedWriter schemaWriter = new BufferedWriter(new FileWriter(EXIUtils.schemasFileLocation));
		schemaWriter.write(sb.toString());
		schemaWriter.close();
		
		return ns;
	}
    
    private void addNewSchemaToCanonicalSchema(String fileLocation, String ns, IoSession session) throws IOException{
		// obtener el schemas File del servidor y transformarlo a un elemento XML
		String canonicalSchemaStr = EXIUtils.readFile(EXIUtils.exiSchemasFolder + "canonicalSchema_" + session.hashCode() + ".xsd");
		StringBuilder canonicalSchemaStrBuilder = new StringBuilder();
		if(canonicalSchemaStr != null && canonicalSchemaStr.indexOf("namespace") != -1){
	        	canonicalSchemaStrBuilder = new StringBuilder(canonicalSchemaStr);
	        	String aux = canonicalSchemaStrBuilder.toString(), importedNamespace = ">";	// importedNamespace hace que se comience justo antes de los xs:import
	        	int index;
	        	do{
	        		aux = aux.substring(aux.indexOf(importedNamespace) + importedNamespace.length());
	        		importedNamespace = EXIUtils.getAttributeValue(aux, "namespace");
	        	}while(importedNamespace != null && ns.compareTo(importedNamespace) > 0 && aux.indexOf("<xs:import ") != -1);
	        	index = canonicalSchemaStrBuilder.indexOf(aux.substring(aux.indexOf('>') + 1));
	        	canonicalSchemaStrBuilder.insert(index, "\n\t<xs:import namespace='" + ns + "' schemaLocation='" + fileLocation + "'/>");
	        }
        else{
        	canonicalSchemaStrBuilder = new StringBuilder();
        	canonicalSchemaStrBuilder.append("<?xml version='1.0' encoding='UTF-8'?> \n\n<xs:schema \n\txmlns:xs='http://www.w3.org/2001/XMLSchema' \n\ttargetNamespace='urn:xmpp:exi:cs' \n\txmlns='urn:xmpp:exi:cs' \n\telementFormDefault='qualified'>\n");
        	canonicalSchemaStrBuilder.append("\n\t<xs:import namespace='" + ns + "' schemaLocation='" + fileLocation + "'/>");
        	canonicalSchemaStrBuilder.append("\n</xs:schema>");
		}
        
        File canonicalSchema = new File(EXIUtils.exiSchemasFolder + "canonicalSchema_" + session.hashCode() + ".xsd");
        BufferedWriter canonicalSchemaWriter = new BufferedWriter(new FileWriter(canonicalSchema));
        canonicalSchemaWriter.write(canonicalSchemaStrBuilder.toString());
        canonicalSchemaWriter.close();
        
        session.setAttribute(EXIUtils.CANONICAL_SCHEMA_LOCATION, canonicalSchema.getAbsolutePath());
	}
    
/* downloadSchema */ 
 
    
    /**
     * 
     * @param schema
     * @param session
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws DocumentException 
     **/
    private void saveDownloadedSchema(String content, IoSession session) throws NoSuchAlgorithmException, IOException, DocumentException {
    	String filePath = EXIUtils.schemasFolder + Calendar.getInstance().getTimeInMillis() + ".xsd";
    	
    	OutputStream out = new FileOutputStream(filePath);
    	out.write(content.getBytes());
    	out.close();
    	
        String ns = addNewSchemaToSchemasFile(filePath, null, null);
		addNewSchemaToCanonicalSchema(filePath, ns, session);
	}
}
