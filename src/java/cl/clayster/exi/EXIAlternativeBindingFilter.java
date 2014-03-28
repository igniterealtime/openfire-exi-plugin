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

/**
 * This class recognizes EXI Alternative Binding. There is only two possible messages that can be received, otherwise the filter will be eliminated from 
 * the current session. In other words, the alternative binding requires EXI messages from the very start. 
 * @author Javier Placencio
 *
 */
public class EXIAlternativeBindingFilter extends IoFilterAdapter {
	
	public static final String filterName = "exiAltBindFilter";
	private static final String flag = "exiAltFlag";
	private static final String firstStreamStartFlag = "exiAltStreamStartFlag";
	
	public EXIAlternativeBindingFilter(){}
	
	@Override
	public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
		if(writeRequest.getMessage() instanceof ByteBuffer){
			ByteBuffer bb = (ByteBuffer) writeRequest.getMessage();
			String msg = Charset.forName("UTF-8").decode(((ByteBuffer) writeRequest.getMessage()).buf()).toString();
			if(msg.contains("<stream:stream ")){
				String id = EXIUtils.getAttributeValue(msg, "id");
				String hostName = JiveGlobals.getProperty("xmpp.domain", "127.0.0.1").toLowerCase();				
				String streamStart = "<exi:streamStart xmlns:exi='http://jabber.org/protocol/compress/exi'"
						+ " version='1.0'"
						+ " from='"
						+ hostName
						+ "' xml:lang='en'"
						+ " xmlns:xml='http://www.w3.org/XML/1998/namespace'"
						+ " id=\"" + id + "\">"
						+ " <exi:xmlns prefix='stream' namespace='http://etherx.jabber.org/streams'/>"
						+ " <exi:xmlns prefix='' namespace='jabber:client'/>"
						+ " <exi:xmlns prefix='xml' namespace='http://www.w3.org/XML/1998/namespace'/>"
						+ " </exi:streamStart>";
				if(session.containsAttribute(EXIUtils.EXI_CONFIG)){
					((EXIFilter) session.getFilterChain().get(EXIFilter.filterName)).addCodec(session);
					session.getFilterChain().remove(EXIFilter.filterName);
					session.getFilterChain().remove(EXIAlternativeBindingFilter.filterName);
				}
				else{
System.out.println("ENCODING to send: " + streamStart);
					bb = ((EXIProcessor) session.getAttribute(EXIUtils.EXI_PROCESSOR)).encodeByteBuffer(streamStart,
							true);
					//TODO:		!session.containsAttribute(EXIAlternativeBindingFilter.firstStreamStartFlag));
					session.setAttribute(EXIAlternativeBindingFilter.firstStreamStartFlag, "true");
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
			}
			else if(msg.startsWith("<stream:features")){
				msg = msg.replaceAll("<stream:features", "<stream:features xmlns:stream=\"http://etherx.jabber.org/streams\"");
			}
			/*
			else if(msg.contains("<exi:setupResponse ")){
				
				// if there was agreement, put the new EXIProcessor in the session
				String agreement = EXIUtils.getAttributeValue(msg, "agreement");
    	        if(agreement != null && agreement.equals("true")){
    	        	if(!((EXIFilter) session.getFilterChain().get(EXIFilter.filterName)).createExiProcessor(session)){
    	        		System.err.println("Error while creating EXIProcessor");
    	    		}
    	        	else{
System.out.println("exiConfig: " + session.getAttribute(EXIUtils.EXI_CONFIG));
System.out.println("exiProcessor nue: " + session.getAttribute(EXIUtils.EXI_PROCESSOR));
    	        	}
    	        }
			}
			*/
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
            	session.setAttribute(EXIAlternativeBindingFilter.flag, "true");
            	EXIProcessor ep = ((EXIFilter)session.getFilterChain().get(EXIFilter.filterName)).createExiProcessor(session);
            	if(ep == null){
            		ep = new EXIProcessor();
            	}
            	session.setAttribute(EXIUtils.EXI_PROCESSOR, ep);
System.err.println("new EXIProcessor: " + session.getAttribute(EXIUtils.EXI_PROCESSOR));
            }
			if(session.containsAttribute(EXIAlternativeBindingFilter.flag)){
				// Decode EXI bytes
System.err.println("Decoding: " + EXIUtils.bytesToHex(exiBytes));
System.err.println("\tusing EXIProcessor: " + session.getAttribute(EXIUtils.EXI_PROCESSOR));
				try{
					msg = ((EXIProcessor) session.getAttribute(EXIUtils.EXI_PROCESSOR)).decodeByteArray(exiBytes);
				} catch (TransformerException e){
					session.setAttribute("exiBytes", exiBytes);
					return;
				}
				session.setAttribute("exiBytes", null);	// old bytes have been used with the last message
				
				Element xml = DocumentHelper.parseText(msg).getRootElement();
System.err.println("EXIDECODED: " + xml.asXML());
				if(xml.getName().equals("streamStart")){
					super.messageReceived(nextFilter, session, translateStreamStart(xml));
					return;
				}
				else if(xml.getName().equals("setup")){
					String setupResponse = ((EXIFilter) session.getFilterChain().get(EXIFilter.filterName)).setupResponse(xml, session);
		    		if(setupResponse != null){
		    			setupResponse = setupResponse.replaceAll("<setupResponse", "<exi:setupResponse")
		    					.replaceAll("<schema", "<exi:schema").replaceAll("</setupResponse>", "</exi:setupResponse>");
						session.write(ByteBuffer.wrap(setupResponse.getBytes()));
		    		}
		    		else	System.err.println("An error occurred while processing alternative negotiation.");
                	return;
                }
                message = xml.asXML();
            }
            else{
            	// Reset to old position in the buffer
                byteBuffer.position(currentPos);
                session.getFilterChain().remove(EXIAlternativeBindingFilter.filterName);
            }
		}
		super.messageReceived(nextFilter, session, message);
	}
	
	private String translateStreamStart(Element streamStart){
		StringBuilder sb = new StringBuilder();
		sb.append("<stream:stream to=\"").append(streamStart.attributeValue("to")).append('\"');
		sb.append(" version=\"").append(streamStart.attributeValue("version")).append('\"');
		//sb.append(" xmlns=\"jabber:client xmlns:stream=\"http://etherx.jabber.org/streams\"");
		
		Element aux;
		for(@SuppressWarnings("unchecked") Iterator<Element> j = streamStart.elementIterator("xmlns"); j.hasNext();){
			aux = j.next();
			sb.append(" xmlns");
			String prefix = aux.attributeValue("prefix");
			if(prefix != null && !prefix.equals(""))	sb.append(":").append(prefix);
			sb.append("=\"").append(aux.attributeValue("namespace")).append('\"');
		}
		sb.append(">");
		return sb.toString();
		//return "<stream:stream to=\"exi.clayster.cl\" xmlns=\"jabber:client\" xmlns:stream=\"http://etherx.jabber.org/streams\" version=\"1.0\">";
	}

	
}
