package cl.clayster.exi;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;

import javax.xml.transform.TransformerException;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;
import org.apache.xerces.impl.dv.util.Base64;
import org.dom4j.DocumentException;
import org.xml.sax.SAXException;

import com.siemens.ct.exi.exceptions.EXIException;

/**
 * This class is used to process <uploadSchema> stanzas, which carry a schema that will be used for the compression that is being negotiated.
 * 
 * @author Javier Placencio
 *
 */
public class UploadSchemaFilter extends IoFilterAdapter {

	static String filterName = "uploadSchemaFilter";
	final String uploadSchemaStartTag = "<uploadSchema";
	final String uploadSchemaEndTag = "</uploadSchema>";
	final String setupStartTag = "<setup";
	final String setupEndTag = "</setup>";
	final String compressStartTag = "<compress";
	final String compressEndTag = "</compress>";
	
	public UploadSchemaFilter(){}

	@Override
    public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception {
		// Decode the bytebuffer and print it to the stdout
	    if (message instanceof ByteBuffer) {
	        ByteBuffer byteBuffer = (ByteBuffer) message;
	        // Decode buffer
	        Charset encoder = Charset.forName("UTF-8");
	        CharBuffer charBuffer = encoder.decode(byteBuffer.buf());
	        byte[] bba = byteBuffer.array();
	        
	        String cont = (String) session.getAttribute("cont");
	        if(cont == null)	cont = "";
	        session.setAttribute("cont", "");
	        String msg = cont + charBuffer.toString();
	        
	        byte[] baCont = (byte[]) session.getAttribute("baCont");
	        if(baCont == null)	baCont = new byte[] {};
	        bba = EXIUtils.concat(baCont, bba);
	        
	        do{
		        if(msg.startsWith(uploadSchemaStartTag)){
		        	if(!msg.contains(uploadSchemaEndTag)){
		        		session.setAttribute("baCont", bba);
		        		session.setAttribute("cont", msg);
		        		return;
		        	}
		        	// msg will be the first <uploadSchema> element, cont will be the next element to be processed.

		        	cont = msg.substring(msg.indexOf(uploadSchemaEndTag) + uploadSchemaEndTag.length());
		        	session.setAttribute("cont", cont);
		        	msg = msg.substring(0, msg.indexOf(uploadSchemaEndTag) + uploadSchemaEndTag.length());
		        	
		            String startTagStr = msg.substring(0, msg.indexOf('>') + 1);
		            String contentType = EXIUtils.getAttributeValue(startTagStr, "contentType");
		            String md5Hash = EXIUtils.getAttributeValue(startTagStr, "md5Hash");
		            String bytes = EXIUtils.getAttributeValue(startTagStr, "bytes");
		            
		            if(contentType != null && !"text".equals(contentType) && md5Hash != null && bytes != null){
		            	// the same as before but for the byte buffer array
			        	int srcPos = EXIUtils.indexOf(bba, uploadSchemaEndTag.getBytes()) + uploadSchemaEndTag.getBytes().length;
			        	if(srcPos != -1){
			        		baCont = new byte[bba.length - srcPos];
				        	System.arraycopy(bba, srcPos, baCont, 0, bba.length - srcPos);
				        	System.arraycopy(bba, 0, bba, 0, srcPos);
				        	session.setAttribute("baCont", baCont);
			        	}    
			        	
		            	byte[] ba = new byte[EXIUtils.indexOf(bba, uploadSchemaEndTag.getBytes()) - startTagStr.getBytes().length];
		            	System.arraycopy(bba, startTagStr.getBytes().length, ba, 0, ba.length);
	                    uploadCompressedMissingSchema(ba, contentType, md5Hash, bytes, session);
		            }
		            else{
		            	uploadMissingSchema(msg, session);
		            }
		        }
		        else if(msg.startsWith(setupStartTag)){
		        	if(!msg.contains(setupEndTag)){
		        		session.setAttribute("cont", msg);
		        		return;
		        	}
		        	cont = msg.substring(msg.indexOf(setupEndTag) + setupEndTag.length());
		        	session.setAttribute("cont", cont);
		        	msg = msg.substring(0, msg.indexOf(setupEndTag) + setupEndTag.length());
		    
		        	session.getFilterChain().remove("uploadSchemaFilter");
	                super.messageReceived(nextFilter, session, msg);
	                return;
		        }
		        else if(msg.startsWith("<iq") || msg.startsWith("<presence")){
		        	session.setAttribute("baCont", null);
		        	session.setAttribute("cont", null);
		        	super.messageReceived(nextFilter, session, message);
		        	return;
		        }
		        else{
		        	session.setAttribute("baCont", bba);
	        		session.setAttribute("cont", msg);
	        		return;
		        }
		        msg = cont;
		        bba = baCont;
	        }while(!cont.equals(""));
	    }
    	// Pass the message to the next filter
    	super.messageReceived(nextFilter, session, message);
    }
	
	void uploadCompressedMissingSchema(byte[] content, String contentType, String md5Hash, String bytes, IoSession session) 
    		throws IOException, NoSuchAlgorithmException, DocumentException, EXIException, SAXException, TransformerException{
    	String filePath = EXIUtils.schemasFolder + Calendar.getInstance().getTimeInMillis() + ".xsd";
		
    	if(!"text".equals(contentType) && md5Hash != null && bytes != null){
    		String xml = "";
			if(contentType.equals("ExiDocument")){
    			xml = EXIProcessor.decodeSchemaless(content);
    		}
    		else if(contentType.equals("ExiBody")){
    			xml = EXIProcessor.decodeExiBodySchemaless(content);
    		}
			EXIUtils.writeFile(filePath, xml);
    	}
    	
		String ns = EXIFilter.addNewSchemaToSchemasFile(filePath, md5Hash, bytes);
		EXIFilter.addNewSchemaToCanonicalSchema(filePath, ns, session);
	}
	
	void uploadMissingSchema(String content, IoSession session) 
    		throws IOException, NoSuchAlgorithmException, DocumentException, EXIException, SAXException, TransformerException{
    	String filePath = EXIUtils.schemasFolder + Calendar.getInstance().getTimeInMillis() + ".xsd";
    	OutputStream out = new FileOutputStream(filePath);
    	
    	content = content.substring(content.indexOf('>') + 1, content.indexOf("</"));
		byte[] outputBytes = content.getBytes();
		
    	outputBytes = Base64.decode(content);
    	out.write(outputBytes);
    	out.close();
    	
		String ns = EXIFilter.addNewSchemaToSchemasFile(filePath, null, null);
		EXIFilter.addNewSchemaToCanonicalSchema(filePath, ns, session);
	}
}
