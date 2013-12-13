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
	final String endTag = "</uploadSchema>";
	
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
            String startTag = charBuffer.toString();
            if(startTag.contains(endTag)){
            	startTag = startTag.substring(0, startTag.indexOf('>') + 1);
            	String contentType = EXIUtils.getAttributeValue(startTag, "contentType");
            	String md5Hash = EXIUtils.getAttributeValue(startTag, "md5Hash");
            	String bytes = EXIUtils.getAttributeValue(startTag, "bytes");
            	if(contentType != null && !"text".equals(contentType) && md5Hash != null && bytes != null){
            		byte[] ba = new byte[byteBuffer.array().length - startTag.getBytes().length - endTag.getBytes().length];
            		System.arraycopy(byteBuffer.array(), startTag.getBytes().length, ba, 0, ba.length);
            		uploadCompressedMissingSchema(ba, contentType, md5Hash, bytes, session);
            	}
            	else{
            		uploadMissingSchema((String) message, session);
            	}
            	byte ultimo = byteBuffer.array()[byteBuffer.array().length - 1];
            	if(ultimo == ((byte) '<')){
            		byte[] ba = new byte[1];
            		ba[0] = ultimo;
            		super.messageReceived(nextFilter, session, ByteBuffer.wrap(ba));
            	}
            	// Reset to old position in the buffer
                byteBuffer.position(currentPos);
            	throw new Exception("Upload processed!!");
            }
            else if(startTag.contains("</setup>")){
            	exiFilter.messageReceived(nextFilter, session, startTag);
            	return;
            }
            else if(startTag.contains("</compress>")){
            	session.getFilterChain().remove("uploadSchemaFilter");
            	exiFilter.messageReceived(nextFilter, session, startTag);
            	return;
            }
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
