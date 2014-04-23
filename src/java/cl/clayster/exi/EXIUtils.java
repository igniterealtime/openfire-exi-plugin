package cl.clayster.exi;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.util.JiveGlobals;

/**
 * Contains useful methods to execute EXI functions needed by {@link EXIFilter} such as reading a file, getting an attribute from an XML document, among others.
 * 
 * @author Javier Placencio
 *
 */
public class EXIUtils {

	final static String schemasFolder = JiveGlobals.getHomeDirectory() + "/plugins/exi/classes/";
	final static String schemasFileLocation = schemasFolder + "schemas.xml";
	final static String exiFolder = schemasFolder + "canonicalSchemas/";
	final static String defaultCanonicalSchemaLocation = exiFolder + "defaultSchema.xsd";
	final static String CANONICAL_SCHEMA_LOCATION = "canonicalSchemaLocation";
	final static String EXI_CONFIG = "exiConfig";
	final static String SCHEMA_ID = "schemaId";
	final static String EXI_PROCESSOR = EXIProcessor.class.getName();
	
	final protected static char[] hexArray = "0123456789abcdef".toCharArray();
	
	/**
	 * Returns a hexadecimal String representation of the given bytes.
	 * 
	 * @param bytes an array of bytes to be represented as a hexadecimal String
	 */
	public static String bytesToHex(byte[] bytes){
	    char[] hexChars = new char[bytes.length * 2];
	    int v;
	    for ( int j = 0; j < bytes.length; j++ ) {
	        v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	    /*
	    // remove extra 000000s in the end of the string
	    int i = hexChars.length - 1;
	    while(hexChars[i] == '0'){
	    	i--;
	    }
	    if(i % 2 == 0){
	    	i++;
	    }
	    String str = new String(hexChars);
	    str = str.substring(0, i+1);
	    return str;
	    */
	}
	
	public static String readFile(String fileLocation){
		try{
			return FileUtils.readFileToString(new File(fileLocation));
		}catch (IOException e) {
			return null;
		}
	}
	
	public static boolean writeFile(String fileName, String content){
		try {
			if(fileName != null && content != null){
				FileOutputStream out;
				
				out = new FileOutputStream(fileName);
				out.write(content.getBytes());
				out.close();
				return true;
			}
		} catch (FileNotFoundException e) {
			return false;
		} catch (IOException e) {
			return false;
		}
		return false;
	}
	
	public static String getAttributeValue(String text, String attribute) {
		attribute = " " + attribute;
		if(text.indexOf(attribute) == -1){
			return null;
		}
		text = text.substring(text.indexOf(attribute) + attribute.length());	// starting after targetNamespace
    	text = text.substring(0, text.indexOf('>'));	// cut what comes after '>'
    	char comilla = '\'';
    	if(text.indexOf(comilla) == -1){
    		comilla = '"';
    	}
    	text = text.substring(text.indexOf(comilla) + 1);	
    	text = text.substring(0, text.indexOf(comilla));	
		return text;
	}

	/***************** server only methods ****************/
	public static String downloadXml(String url){
		StringBuilder sb = new StringBuilder();
		String responseContent = "<error message=''/>";
		URLConnection uConn = null;
		try{
			uConn = new URL(url).openConnection();
			// look for errors
			switch(((HttpURLConnection) uConn).getResponseCode()){
				case -1:
					responseContent = "<unknownError/>";
					break;
				case 404:	// HTTP error
					responseContent = "<httpError code='404' message='Not Found'/>";
					break;
				case 400:case 401:case 402:case 403: case 405:case 406:case 407:case 408:case 409:case 410:case 411:case 412:case 413:case 414:case 415:
				case 416:case 417:case 418:case 419:case 420:case 421:case 422:case 423:case 424:case 425:case 426:case 427:case 428:case 429:case 430:
				case 431:case 440:case 444:case 449:case 450:case 451:case 495:case 496:case 497:case 499:  
					responseContent = "<httpError code='" + ((HttpURLConnection) uConn).getResponseCode() + "' message='Client Error'/>";
					break;
				case 500:case 501:case 502:case 503:case 504:case 505:case 506:case 507:case 508:case 509:case 510:
				case 511:case 522:case 523:case 524:case 598:case 599:
					responseContent = "<httpError code='" + ((HttpURLConnection) uConn).getResponseCode() + "' message='Server Error'/>";
					break;
				default :	// SUCCESS!
					String inputLine;
					BufferedReader in = new BufferedReader(new InputStreamReader(uConn.getInputStream()));
			        while ((inputLine = in.readLine()) != null){
			        	sb.append(inputLine + '\n');
			        }
			        in.close();
			        DocumentHelper.parseText(sb.toString());
			        return sb.substring(0, sb.length());
			}
		} catch(MalformedURLException e){
			responseContent = "<invalidUrl message='Unrecognized schema.'/>";
		} catch (SocketTimeoutException e) {
			responseContent = "<timeout message='No response returned.'/>";
	    } catch (DocumentException e){
	    	int sc = uConn.getContentType().indexOf(';');
	    	String contentType = sc != -1 ? uConn.getContentType().substring(0, sc) : uConn.getContentType();
			responseContent = "<invalidContentType contentTypeReturned='" + contentType + "'/>";
	    } catch (Exception e){	    	
	    	responseContent = "<error message='No free space left.'/>";
	    }
		
		return ("<downloadSchemaResponse xmlns='http://jabber.org/protocol/compress/exi' url='" + url
					+ "' result='false'>" + responseContent + "</downloadSchemaResponse>");
	}
	
	/**
	 * Returns the index within <code>data</code> of the first occurence of <code>pattern</code>.
	 * @param data the byte[] where to look for the pattern
	 * @param pattern the pattern to look for within data
	 * @return the index where pattern was found or -1 if it was not found
	 */
	public static int indexOf(byte[] data, byte[] pattern){
		int index = -1;
		int count = 0;
		if(!(data == null || pattern == null || data.length < 1 || pattern.length < 1) && data.length >= pattern.length){
			for(index = 0 ; index <= data.length-pattern.length ; index++){
				if(data[index] == pattern[0]){
					count = 1;
					for(int p = 1 ; p < pattern.length ; p++){
						if(data[index + p] != pattern[p])	break;
						count++;
					}
					if(count == pattern.length)	break;
				}
			}
			if(count < pattern.length)	index = -1;
		}		
		return index;
	}
	
	/**
	 * Returns a new byte array, which is the result of concatenating a and b.
	 * @param a the first part of the resulting byte array
	 * @param b the second part of the resulting byte array
	 * @return the resulting byte array
	 */
	public static byte[] concat(byte[] a, byte[] b){
		if(a == null || a.length == 0)	return b;
		if(b == null || b.length == 0)	return a;
		byte[] c = new byte[a.length + b.length];
		System.arraycopy(a, 0, c, 0, a.length);
		System.arraycopy(b, 0, c, a.length, b.length);
		return c;
	}
	
	/**
	 * Looks for all schema files (*.xsd) in the given folder and creates two new files: 
	 * a canonical schema file which imports all existing schema files;
	 * and an xml file called schema.xml which contains each schema namespace, file size in bytes and its md5Hash code 
	 * 
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 */
	static void generateSchemasFile() throws IOException {
		try{
			File folder = new File(EXIUtils.schemasFolder);
			if(!folder.exists()){
				folder.mkdir();
			}
			if(!new File(EXIUtils.exiFolder).exists()){
				new File(EXIUtils.exiFolder).mkdir();
			}
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
            	if (file.isFile() && file.getName().endsWith(".xsd")) {
            	// se hace lo siguiente para cada archivo XSD en la carpeta folder	
            		fileLocation = file.getAbsolutePath();
					r = 0;
					md.reset();
					StringBuilder sb = new StringBuilder();
	            	
					if(fileLocation == null)	break;
					is = new FileInputStream(fileLocation);
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
            //variables to write the schemas files
            BufferedWriter stanzasWriter = null;
            stanzasWriter = new BufferedWriter(new FileWriter(EXIUtils.schemasFileLocation));
            
            stanzasWriter.write("<schemas>");
            for(String ns : namespaces){
            	stanzasWriter.write("\n\t" + schemasStanzas.get(ns));
            }
            stanzasWriter.write("\n</schemas>");
			stanzasWriter.close();
		}catch (NoSuchAlgorithmException e){
			e.printStackTrace();
		}
	}
	
	/**
	 * Generates XEP-0322's default canonical schema
	 * @throws IOException
	 */
	static void generateDefaultCanonicalSchema() throws IOException {
		String[] schemasNeeded = {"http://etherx.jabber.org/streams", "http://jabber.org/protocol/compress/exi"};
		boolean[] schemasFound = {false, false};
		Element setup;
		try {
			setup = DocumentHelper.parseText(EXIUtils.readFile(EXIUtils.schemasFileLocation)).getRootElement();
		} catch (DocumentException e1) {
			e1.printStackTrace();
			return;
		}
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version='1.0' encoding='UTF-8'?>"
        		+ "\n\n<xs:schema "
        		+ "\n\txmlns:xs='http://www.w3.org/2001/XMLSchema'"
        		+ "\n\txmlns:stream='http://etherx.jabber.org/streams'"
        		+ "\n\txmlns:exi='http://jabber.org/protocol/compress/exi'"
        		+ "\n\ttargetNamespace='urn:xmpp:exi:default'"
        		+ "\n\telementFormDefault='qualified'>");
        
		Element schema;
        for (@SuppressWarnings("unchecked") Iterator<Element> i = setup.elementIterator("schema"); i.hasNext(); ) {
        	schema = i.next();
        	String ns = schema.attributeValue("ns");
        	if(ns.equalsIgnoreCase(schemasNeeded[0])){
        		schemasFound[0] = true;
        		if(schemasFound[1]){
        			break;
        		}
        	}
        	else if(ns.equalsIgnoreCase(schemasNeeded[1])){
        		schemasFound[1] = true;
        		if(schemasFound[0]){
        			break;
        		}
        	}
        }
        if(schemasFound[0] && schemasFound[1]){
    		sb.append("\n\t<xs:import namespace='" + schemasNeeded[0] + "'/>");
    		sb.append("\n\t<xs:import namespace='" + schemasNeeded[1] + "'/>");
    	}
        else{
        	throw new IOException("Missing schema for default canonical schema: " + (schemasFound[0] ? schemasNeeded[0] : schemasNeeded[1])); 
        }
        sb.append("\n</xs:schema>");
        
        String content = sb.toString();
        String fileName = EXIUtils.defaultCanonicalSchemaLocation;
        
        BufferedWriter newCanonicalSchemaWriter = new BufferedWriter(new FileWriter(fileName));
        newCanonicalSchemaWriter.write(content);
        newCanonicalSchemaWriter.close();
        return;
	}
	
}
