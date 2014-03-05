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

import org.apache.commons.io.FileUtils;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;

import com.siemens.ct.exi.CodingMode;

/**
 * Contains useful methods to execute EXI functions needed by {@link EXIFilter} such as reading a file, getting an attribute from an XML document, among others.
 * 
 * @author Javier Placencio
 *
 */
public class EXIUtils {
	
	// for the folders it is needed the base openfire location to be appended first with JiveGlobals.getHomeDirectory()
	final protected static String exiSchemasFolder = "/plugins/exi/classes/exiSchemas/";
	final protected static String schemasFolder = "/plugins/exi/classes/";
	final protected static String schemasFileLocation = "/plugins/exi/classes/exiSchemas/schemas.xml";
	final protected static String CANONICAL_SCHEMA_LOCATION = "canonicalSchemaLocation";
	final protected static String ALIGNMENT = "alignment";
	final protected static String BLOCK_SIZE = "blockSize";
	final protected static String STRICT = "strict";
	final protected static String VALUE_MAX_LENGTH = "valueMaxLength";
	final protected static String VALUE_PARTITION_CAPACITY = "valuePartitionCapacity";
	final protected static String EXI_CONFIG = "exiConfig";
	final protected static String CONFIG_ID = "configId";
	
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
	
	/**
	 * Gets an EXI configuration id and parses it to return an <code>EXISetupConfiguration</code> class
	 * @param configId a unique configuration id for a previously used EXI configuration
	 * @return the respective EXI Configuration class, or null if there was any problem
	 */
	static EXISetupConfiguration parseQuickConfigId(String configId){
		EXISetupConfiguration exiConfig = null;
		if(configId != null){
			exiConfig = new EXISetupConfiguration();
			exiConfig.setId(configId);
			try{
				// next comments tell what is done by EXIFilter when it processes a successful setup stanza
				// the first 36 chars (indexes 0-35) are just the UUID, number 37 is '_' (index 36)
				Integer alignment = Character.getNumericValue(configId.charAt(37)); //The next digit (index 37) represents the alignment (0=bit-packed, 1=byte-packed, 2=pre-compression, 3=compression)
				if(alignment < 0 || alignment > 3)	alignment = EXIProcessor.defaultAlignmentCode;
				Boolean strict = configId.charAt(38) == '1';	//The next digit (index 38) represents if it is strict or not
				configId = configId.substring(39);
				Integer blockSize = Integer.valueOf(configId.substring(0, configId.indexOf('_')));	// next number represents blocksize (until the next '_')
				configId = configId.substring(configId.indexOf('_') + 1);
				Integer valueMaxLength = Integer.valueOf(configId.substring(0, configId.indexOf('_')));	// next number between dashes is valueMaxLength
				Integer valuePartitionCapacity = Integer.valueOf(configId.substring(configId.indexOf('_') + 1)); // last number is valuePartitionCapacity
			
				switch((int) alignment){
					case 1:
						exiConfig.setAlignment(CodingMode.BYTE_PACKED);
						break;
					case 2:
						exiConfig.setAlignment(CodingMode.PRE_COMPRESSION);
						break;
					case 3:
						exiConfig.setAlignment(CodingMode.COMPRESSION);
						break;
					default:
						exiConfig.setAlignment(CodingMode.BIT_PACKED);
						break;
				};
				exiConfig.setStrict(strict);
				exiConfig.setBlockSize(blockSize);
				exiConfig.setValueMaxLength(valueMaxLength);
				exiConfig.setValuePartitionCapacity(valuePartitionCapacity);
			} catch(Exception e){
				return null;
			}
		}
		return exiConfig;
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
	
	
	
}
