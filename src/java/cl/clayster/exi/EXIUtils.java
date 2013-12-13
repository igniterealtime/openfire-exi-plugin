package cl.clayster.exi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;

import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;

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
}
