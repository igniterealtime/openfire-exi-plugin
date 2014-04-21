package cl.clayster.exi;

import java.nio.charset.Charset;
import java.util.Iterator;

import javax.xml.transform.TransformerException;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.util.JiveGlobals;

import com.siemens.ct.exi.core.EXIHeaderDecoder;
import com.siemens.ct.exi.exceptions.EXIException;
import com.siemens.ct.exi.io.channel.BitDecoderChannel;

/**
 * This class recognizes EXI Alternative Binding. There is only two possible messages that can be received, otherwise the filter will be eliminated from 
 * the current session. In other words, the alternative binding requires EXI messages from the very start. 
 * @author Javier Placencio
 *
 */
public class EXIAlternativeBindingFilter extends IoFilterAdapter {
	
	public static final String filterName = "exiAltBindFilter";
	static final String flag = "exiAltFlag";
	private static final String quickSetupFlag = "exiAltQuickSetup";
	private static final String agreementSentFlag = "exiAltAgreementSent";
	private static final String setupFlag = "exiAltSetupReceived";
	
	public EXIAlternativeBindingFilter(){}
	
	@Override
	public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
		if(writeRequest.getMessage() instanceof ByteBuffer){
			ByteBuffer bb = (ByteBuffer) writeRequest.getMessage();
			String msg = Charset.forName("UTF-8").decode(((ByteBuffer) writeRequest.getMessage()).buf()).toString();
			if(msg.contains("<stream:stream ")){
				String open = open(EXIUtils.getAttributeValue(msg, "id"));
System.out.println("ENCODING to send: " + open);
				bb = ((EXIProcessor) session.getAttribute(EXIUtils.EXI_PROCESSOR)).encodeByteBuffer(open, true);
				super.filterWrite(nextFilter, session, new WriteRequest(bb, writeRequest.getFuture(), writeRequest.getDestination()));
				
				if(msg.contains("<stream:features")){
					msg = msg.substring(msg.indexOf("<stream:features"))
							.replaceAll("<stream:features", "<stream:features xmlns:stream=\"http://etherx.jabber.org/streams\"");
System.out.println("ENCODING to send: " + msg);
					super.filterWrite(nextFilter, session, 
							new WriteRequest(((EXIProcessor) session.getAttribute(EXIUtils.EXI_PROCESSOR)).encodeByteBuffer(msg)
									, writeRequest.getFuture(), writeRequest.getDestination()));
				}
				return;
			}
			else if(msg.startsWith("<stream:features")){
				msg = msg.replaceAll("<stream:features", "<stream:features xmlns:stream=\"http://etherx.jabber.org/streams\"");
			}
			else if(msg.startsWith("<exi:setupResponse ")){
				if(msg.contains("agreement=\"true\"")){
					session.setAttribute(EXIAlternativeBindingFilter.agreementSentFlag, true);
				}
				else{
					msg = "<streamEnd xmlns:exi='http://jabber.org/protocol/compress/exi'/>";
				}
			}
System.out.println("ENCODING to send: " + msg);
			bb = ((EXIProcessor) session.getAttribute(EXIUtils.EXI_PROCESSOR)).encodeByteBuffer(msg);
			super.filterWrite(nextFilter, session, new WriteRequest(bb, writeRequest.getFuture(), writeRequest.getDestination()));
		}
	}
	

	@Override
	public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception {
		// Decode the bytebuffer and print it to the stdout
    	if (message instanceof ByteBuffer) {
    		String msg;
    		ByteBuffer byteBuffer = (ByteBuffer) message;
    		// Keep current position in the buffer
            int currentPos = byteBuffer.position();
            
			byte[] exiBytes = (byte[]) session.getAttribute("exiBytes");
			if(exiBytes == null){
				exiBytes = byteBuffer.array();
			}
			else{
				byte[] dest = new byte[exiBytes.length + byteBuffer.limit()];
				System.arraycopy(exiBytes, 0, dest, 0, exiBytes.length);
				System.arraycopy(byteBuffer.array(), 0, dest, exiBytes.length, byteBuffer.limit());
				exiBytes = dest;				
			}
			if(EXIProcessor.hasEXICookie(exiBytes)){
				session.setAttribute(EXIAlternativeBindingFilter.flag, true);
				if(!session.containsAttribute(EXIAlternativeBindingFilter.quickSetupFlag)){
					session.setAttribute(EXIAlternativeBindingFilter.quickSetupFlag, false);
					try{
						EXIHeaderDecoder headerDecoder = new EXIHeaderDecoder();
		            	BitDecoderChannel headerChannel = new BitDecoderChannel(((ByteBuffer)message).asInputStream());
		            	EXISetupConfiguration exiConfig = new EXISetupConfiguration(true);
		            	exiConfig.setSchemaIdResolver(new SchemaIdResolver());
		            	exiConfig = (EXISetupConfiguration) headerDecoder.parse(headerChannel, exiConfig);
		            	if(exiConfig.getGrammars().isSchemaInformed()){
		            		exiConfig.setSchemaId(exiConfig.getGrammars().getSchemaId());
		            		EXIProcessor ep = new EXIProcessor(exiConfig);
		            		msg = ep.decodeByteArray(exiBytes);
			            	session.setAttribute(EXIUtils.EXI_PROCESSOR, ep);
			            	session.setAttribute(EXIUtils.EXI_CONFIG, exiConfig);
			            	session.setAttribute(EXIAlternativeBindingFilter.quickSetupFlag, true);
System.out.println("quick setup: " + exiConfig);
		            	}
		            	else{
		            		
		            	}
					} catch (EXIException e){
						e.printStackTrace();
					} catch (TransformerException e){
						e.printStackTrace();
					}
				}
				
				if(session.getAttribute(EXIAlternativeBindingFilter.quickSetupFlag).equals(false)){
					EXISetupConfiguration exiConfig = new EXISetupConfiguration();
					if(session.containsAttribute(EXIUtils.EXI_CONFIG)){
						exiConfig = (EXISetupConfiguration) session.getAttribute(EXIUtils.EXI_CONFIG);
					}
					session.setAttribute(EXIUtils.EXI_PROCESSOR, new EXIProcessor(exiConfig));
System.err.println("new EXIProcessor: " + session.getAttribute(EXIUtils.EXI_PROCESSOR));
System.err.println("with: " + exiConfig);
				}
            }
			if(session.containsAttribute(EXIAlternativeBindingFilter.flag)){
				// Decode EXI bytes
System.err.println("Decoding: " + EXIUtils.bytesToHex(exiBytes));
System.err.println("\tusing EXISetupConfigurations: " + (session.containsAttribute(EXIUtils.EXI_CONFIG) ? (EXISetupConfiguration) session.getAttribute(EXIUtils.EXI_CONFIG) : new EXISetupConfiguration()));
				try{
					msg = ((EXIProcessor) session.getAttribute(EXIUtils.EXI_PROCESSOR)).decodeByteArray(exiBytes);
				} catch (TransformerException e){
					if(session.containsAttribute("exiBytes")){
						byte[] anterior = (byte[]) session.getAttribute("exiBytes");
						byte[] aux = new byte[anterior.length + exiBytes.length];
						System.arraycopy(exiBytes, 0, aux, anterior.length, exiBytes.length - anterior.length);
		    			System.arraycopy(anterior, 0, aux, 0, anterior.length);
		    			exiBytes = aux;
					}
					session.setAttribute("exiBytes", exiBytes);
					return;
				}
				session.setAttribute("exiBytes", null);	// old bytes have been used with the last message
				
				Element xml = DocumentHelper.parseText(msg).getRootElement();
System.err.println("Decoded: " + xml.asXML());
				if(xml.getName().equals("open")){
					if(session.containsAttribute(EXIAlternativeBindingFilter.agreementSentFlag)){
						// this is the last open (after receiving setupResponse)
						((EXIFilter) session.getFilterChain().get(EXIFilter.filterName)).addCodec(session);
						session.write(ByteBuffer.wrap(open(null).getBytes()));
					}
					else{
						super.messageReceived(nextFilter, session, translateOpen(xml));
					}
					return;
				}
				else if(xml.getName().equals("setup")){
					session.setAttribute(EXIAlternativeBindingFilter.setupFlag, true);
					String setupResponse = ((EXIFilter) session.getFilterChain().get(EXIFilter.filterName)).setupResponse(xml, session);
		    		if(setupResponse != null){
		    			setupResponse = setupResponse.replaceAll("<setupResponse", "<exi:setupResponse")
		    					.replaceAll("<schema", "<exi:schema").replaceAll("</setupResponse>", "</exi:setupResponse>");
						session.write(ByteBuffer.wrap(setupResponse.getBytes()));
		    		}
		    		else	System.err.println("An error occurred while processing alternative negotiation.");
                	return;
                }
				else if(xml.getName().equals("presence")){
					if(session.getAttribute(EXIAlternativeBindingFilter.quickSetupFlag).equals(true)
							|| !session.containsAttribute(EXIAlternativeBindingFilter.setupFlag)){
						super.messageReceived(nextFilter, session, xml.asXML());
						((EXIFilter) session.getFilterChain().get(EXIFilter.filterName)).addCodec(session);
						return;
					}
				}
                message = xml.asXML();
            }
            else{
            	// Reset to old position in the buffer
                byteBuffer.position(currentPos);
                session.getFilterChain().remove(EXIAlternativeBindingFilter.filterName);
            }
		}
    	else{
    		session.getFilterChain().remove(EXIAlternativeBindingFilter.filterName);
    	}
		super.messageReceived(nextFilter, session, message);
	}
	
	static String translateOpen(Element open){
		StringBuilder sb = new StringBuilder();
		sb.append("<stream:stream to=\"").append(open.attributeValue("to")).append('\"');
		sb.append(" version=\"").append(open.attributeValue("version")).append('\"');
		//sb.append(" xmlns=\"jabber:client xmlns:stream=\"http://etherx.jabber.org/streams\"");
		
		Element aux;
		for(@SuppressWarnings("unchecked") Iterator<Element> j = open.elementIterator("xmlns"); j.hasNext();){
			aux = j.next();
			sb.append(" xmlns");
			String prefix = aux.attributeValue("prefix");
			if(prefix != null && !prefix.equals(""))	sb.append(":").append(prefix);
			sb.append("=\"").append(aux.attributeValue("namespace")).append('\"');
		}
		sb.append(">");
		return sb.toString();
	}

	static String open(String id) {
		String hostName = JiveGlobals.getProperty("xmpp.domain", "127.0.0.1").toLowerCase();	
		StringBuilder sb = new StringBuilder();
		sb.append("<exi:open xmlns:exi='http://jabber.org/protocol/compress/exi'")
		.append(" version='1.0' from='").append(hostName).append("' xml:lang='en' xmlns:xml='http://www.w3.org/XML/1998/namespace'");
		if(id != null){
			sb.append(" id=\"" + id + "\"");
		}
		sb.append("><exi:xmlns prefix='stream' namespace='http://etherx.jabber.org/streams'/>"
				+ "<exi:xmlns prefix='' namespace='jabber:client'/>"
				+ "<exi:xmlns prefix='xml' namespace='http://www.w3.org/XML/1998/namespace'/>"
				+ "</exi:open>");
		return sb.toString();
	}
}
