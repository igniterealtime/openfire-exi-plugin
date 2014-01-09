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

public class UploadSchemaFilter extends IoFilterAdapter {
	
	EXIFilter exiFilter;
	final String uploadSchemaStartTag = "<uploadSchema";
	final String uploadSchemaEndTag = "</uploadSchema>";
	final String setupStartTag = "<setup";
	final String setupEndTag = "</setup>";
	final String compressStartTag = "<compress";
	final String compressEndTag = "</compress>";
	
	public UploadSchemaFilter(EXIFilter exiFilter){
		this.exiFilter = exiFilter;
	}

	@Override
    public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception {
		// Decode the bytebuffer and print it to the stdout
	    if (message instanceof ByteBuffer) {
	        ByteBuffer byteBuffer = (ByteBuffer) message;
	        // Keep current position in the buffer
	        int currentPos = byteBuffer.position();
	        // Decode buffer
	        Charset encoder = Charset.forName("UTF-8");
	        CharBuffer charBuffer = encoder.decode(byteBuffer.buf());
	        
	        String cont = (String) session.getAttribute("cont");
	        if(cont == null)	cont = "";
	        session.setAttribute("cont", "");
	        String msg = cont + charBuffer.toString();
	        do{
		        if(msg.startsWith(uploadSchemaStartTag)){
		        	if(!msg.contains(uploadSchemaEndTag)){
		        		session.setAttribute("cont", msg);
		        		return;
		        	}
		        	// msg is the first element, cont is the next element to be processed.
		        	cont = msg.substring(msg.indexOf(uploadSchemaEndTag) + uploadSchemaEndTag.length());
		        	session.setAttribute("cont", cont);
		        	msg = msg.substring(0, msg.indexOf(uploadSchemaEndTag) + uploadSchemaEndTag.length());
		        	
		            String startTagStr = msg.substring(0, msg.indexOf('>') + 1);
		            String contentType = EXIUtils.getAttributeValue(startTagStr, "contentType");
		            String md5Hash = EXIUtils.getAttributeValue(startTagStr, "md5Hash");
		            String bytes = EXIUtils.getAttributeValue(startTagStr, "bytes");
		            
		            if(contentType != null && !"text".equals(contentType) && md5Hash != null && bytes != null){
		            	//TODO: caso en que llegan dos archivos en un mensaje
	                    byte[] ba = new byte[byteBuffer.array().length - startTagStr.getBytes().length - uploadSchemaEndTag.getBytes().length];
	                    System.arraycopy(byteBuffer.array(), startTagStr.getBytes().length, ba, 0, ba.length);
System.out.println("uploadCompressedMissingSchema: " + EXIUtils.bytesToHex(ba));
	                    uploadCompressedMissingSchema(ba, contentType, md5Hash, bytes, session);
		            }
		            else{
		            	uploadMissingSchema(msg, session);
		            }
		        }
		        else if(msg.startsWith("<setup")){
		        	if(!msg.contains(setupEndTag)){
		        		session.setAttribute("cont", msg);
		        		return;
		        	}
		        	cont = msg.substring(msg.indexOf(setupEndTag) + setupEndTag.length());
		        	session.setAttribute("cont", cont);
		        	msg = msg.substring(0, msg.indexOf(setupEndTag) + setupEndTag.length());
		        	
	                exiFilter.messageReceived(nextFilter, session, msg);
	                return;
		        }
		        else if(msg.startsWith("<compress")){
		        	if(!msg.contains(compressEndTag)){
		        		session.setAttribute("cont", msg);
		        		return;
		        	}
		        	cont = msg.substring(msg.indexOf(compressEndTag) + compressEndTag.length());
		        	session.setAttribute("cont", cont);
		        	msg = msg.substring(0, msg.indexOf(compressEndTag) + compressEndTag.length());
		        	
	                session.getFilterChain().remove("uploadSchemaFilter");
	                exiFilter.messageReceived(nextFilter, session, msg);
	                return;
		        }
		        else{
		        	break;
		        }
		        msg = cont;
	        }while(!cont.equals(""));
	        
	        // Reset to old position in the buffer
	        byteBuffer.position(currentPos);
	    }
    	// Pass the message to the next filter
    	super.messageReceived(nextFilter, session, message);
    }
	
	void uploadCompressedMissingSchema(byte[] content, String contentType, String md5Hash, String bytes, IoSession session) 
    		throws IOException, NoSuchAlgorithmException, DocumentException, EXIException, SAXException, TransformerException{
    	String filePath = EXIUtils.schemasFolder + Calendar.getInstance().getTimeInMillis() + ".xsd";
		
    	if(!"text".equals(contentType) && md5Hash != null && bytes != null){
			if(contentType.equals("ExiDocument")){
    			String xml = EXIProcessor.decodeSchemaless(content);
    			EXIUtils.writeFile(filePath, xml);
    		}
    		else if(contentType.equals("ExiBody")){
    			// TODO
    		}	
    	}
    	
		String ns = exiFilter.addNewSchemaToSchemasFile(filePath, md5Hash, bytes);
		exiFilter.addNewSchemaToCanonicalSchema(filePath, ns, session);
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
    	
		String ns = exiFilter.addNewSchemaToSchemasFile(filePath, null, null);
		exiFilter.addNewSchemaToCanonicalSchema(filePath, ns, session);
	}
}
