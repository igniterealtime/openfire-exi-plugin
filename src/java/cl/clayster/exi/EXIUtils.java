package cl.clayster.exi;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;

/**
 * Contains useful methods to execute EXI functions needed by {@link EXIFilter} such as reading a file, getting an attribute from an XML document, among others.
 * 
 * @author Javier Placencio
 *
 */
public class EXIUtils {
	
	final protected static String exiSchemasFolder = "C:/Users/Javier/workspace/Personales/openfire/target/openfire/plugins/exi/res/exiSchemas/";
	final protected static String schemasFolder = "C:/Users/Javier/workspace/Personales/openfire/target/openfire/plugins/exi/res/";
	final protected static String schemasFileLocation = "C:/Users/Javier/workspace/Personales/openfire/target/openfire/plugins/exi/res/exiSchemas/schemas.xml";
	final protected static String CANONICAL_SCHEMA_LOCATION = "canonicalSchemaLocation";
	
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
	}
	
	public static String readFile(String fileLocation){
		try{
			return new String(Files.readAllBytes(new File(fileLocation).toPath()));
		}catch (IOException e) {
			return null;
		}
	}
	
	
	public static String getAttributeValue(String text, String attribute) {
		if(text.indexOf(attribute) == -1){
			return null;
		}
		text = text.substring(text.indexOf(attribute) + attribute.length());	// desde despues de targetNamespace	
    	text = text.substring(0, text.indexOf('>'));	// cortar lo que viene despues del próximo '>'
    	char comilla = '"';
    	if(text.indexOf(comilla) == -1){
    		comilla = '\'';
    	}
    	text = text.substring(text.indexOf(comilla) + 1);	// cortar lo que hay hasta la primera comilla (inclusive)
    	text = text.substring(0, text.indexOf(comilla));		// cortar lo que hay despues de la nueva primera comilla/segunda comilla de antes (inclusive)
		return text;
	}
	
	public static String downloadTextFile(String url){
		StringBuilder sb = new StringBuilder();
        String inputLine;
        
		try{
			URLConnection uConn = new URL(url).openConnection();
			// look for errors
			switch(((HttpURLConnection) uConn).getResponseCode()){
				case -1:	// invalid URL
					return "<downloadSchemaResponse xmlns='http://jabber.org/protocol/compress/exi' url='" + url + "' result='false'>"
							+ "<invalidUrl message='Unrecognized schema.'/></downloadSchemaResponse>";
				case 404:	// HTTP error
					return "<downloadSchemaResponse xmlns='http://jabber.org/protocol/compress/exi' url='" + url + "' result='false'>"
							+ "<httpError code='404' message='NotFound'/>"
							+ "</downloadSchemaResponse>";
				case 200:	// success, continue
					break;
			}
			BufferedReader in = new BufferedReader(new InputStreamReader(uConn.getInputStream()));
	        while ((inputLine = in.readLine()) != null){
	        	sb.append(inputLine + '\n');
	        }
	        in.close();
System.out.println(sb.toString());
		} catch (SocketTimeoutException e) {
			return "<downloadSchemaResponse xmlns='http://jabber.org/protocol/compress/exi' url='" + url
					+ "' result='false'><timeout message='No response returned.'/></downloadSchemaResponse>";
	    } catch (Exception e){
	    	return "<downloadSchemaResponse xmlns='http://jabber.org/protocol/compress/exi' url='" + url
					+ "' result='false'><error message='No free space left.'/></downloadSchemaResponse>";
	    }
			return sb.substring(0, sb.length());
	}
	
	public static Document downloadXml(URL url) throws DocumentException{
		SAXReader reader = new SAXReader();
        Document document = reader.read(url);
        return document;
	}
}
