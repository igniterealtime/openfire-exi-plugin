/**
 * Copyright 2013-2014 Javier Placencio, 2023 Ignite Realtime Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cl.clayster.exi;

import com.siemens.ct.exi.core.coder.EXIHeaderDecoder;
import com.siemens.ct.exi.core.exceptions.EXIException;
import com.siemens.ct.exi.core.io.channel.BitDecoderChannel;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.transform.TransformerException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * This class recognizes EXI Alternative Binding. There is only two possible messages that can be received, otherwise the filter will be eliminated from
 * the current session. In other words, the alternative binding requires EXI messages from the very start.
 *
 * @author Javier Placencio
 */
public class EXIAlternativeBindingFilter extends IoFilterAdapter
{
    private static final Logger Log = LoggerFactory.getLogger(EXIAlternativeBindingFilter.class);

    public static final String filterName = "exiAltBindFilter";
    static final String flag = "exiAltFlag";
    private static final String quickSetupFlag = "exiAltQuickSetup";
    private static final String agreementSentFlag = "exiAltAgreementSent";
    private static final String setupFlag = "exiAltSetupReceived";

    public EXIAlternativeBindingFilter()
    {
    }

    @Override
    public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception
    {
        if (writeRequest.getMessage() instanceof IoBuffer) {
            IoBuffer bb = (IoBuffer) writeRequest.getMessage();
            String msg = StandardCharsets.UTF_8.decode(((IoBuffer) writeRequest.getMessage()).buf()).toString();
            if (msg.contains("<stream:stream ")) {
                String open = open(EXIUtils.getAttributeValue(msg, "id"));
                Log.trace("Encoding {} XMPP characters into EXI bytes for session {}", open.length(), session.hashCode());
                bb = ((EXIProcessor) session.getAttribute(EXIUtils.EXI_PROCESSOR)).encodeByteBuffer(open, true);
                writeRequest.setMessage(bb);
                super.filterWrite(nextFilter, session, writeRequest);

                if (msg.contains("<stream:features")) {
                    msg = msg.substring(msg.indexOf("<stream:features"))
                        .replaceAll("<stream:features", "<stream:features xmlns:stream=\"http://etherx.jabber.org/streams\"");
                    Log.trace("Encoding {} XMPP characters into EXI bytes for session {}", msg.length(), session.hashCode());
                    writeRequest.setMessage(((EXIProcessor) session.getAttribute(EXIUtils.EXI_PROCESSOR)).encodeByteBuffer(msg));
                    super.filterWrite(nextFilter, session, writeRequest);
                }
                return;
            } else if (msg.startsWith("<stream:features")) {
                msg = msg.replaceAll("<stream:features", "<stream:features xmlns:stream=\"http://etherx.jabber.org/streams\"");
            } else if (msg.startsWith("<exi:setupResponse ")) {
                if (msg.contains("agreement=\"true\"")) {
                    session.setAttribute(EXIAlternativeBindingFilter.agreementSentFlag, true);
                } else {
                    msg = "<streamEnd xmlns:exi='http://jabber.org/protocol/compress/exi'/>";
                }
            }
            Log.trace("Encoding {} XMPP characters into EXI bytes for session {}", msg.length(), session.hashCode());
            bb = ((EXIProcessor) session.getAttribute(EXIUtils.EXI_PROCESSOR)).encodeByteBuffer(msg);
            writeRequest.setMessage(bb);
            super.filterWrite(nextFilter, session, writeRequest);
        }
    }


    @Override
    public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception
    {
        // Decode the bytebuffer and print it to the stdout
        if (message instanceof IoBuffer) {
            String msg;
            IoBuffer byteBuffer = (IoBuffer) message;
            // Keep current position in the buffer
            int currentPos = byteBuffer.position();

            byte[] exiBytes = (byte[]) session.getAttribute("exiBytes");
            if (exiBytes == null) {
                exiBytes = byteBuffer.array();
            } else {
                byte[] dest = new byte[exiBytes.length + byteBuffer.limit()];
                System.arraycopy(exiBytes, 0, dest, 0, exiBytes.length);
                System.arraycopy(byteBuffer.array(), 0, dest, exiBytes.length, byteBuffer.limit());
                exiBytes = dest;
            }
            if (EXIProcessor.hasEXICookie(exiBytes)) {
                session.setAttribute(EXIAlternativeBindingFilter.flag, true);
                if (!session.containsAttribute(EXIAlternativeBindingFilter.quickSetupFlag)) {
                    session.setAttribute(EXIAlternativeBindingFilter.quickSetupFlag, false);
                    try {
                        EXIHeaderDecoder headerDecoder = new EXIHeaderDecoder();
                        BitDecoderChannel headerChannel = new BitDecoderChannel(((IoBuffer) message).asInputStream());
                        EXISetupConfiguration exiConfig = new EXISetupConfiguration(true);
                        exiConfig.setSchemaIdResolver(new SchemaIdResolver());
                        exiConfig = (EXISetupConfiguration) headerDecoder.parse(headerChannel, exiConfig);
                        if (exiConfig.getGrammars().isSchemaInformed()) {
                            exiConfig.setSchemaId(exiConfig.getGrammars().getSchemaId());
                            EXIProcessor ep = new EXIProcessor(exiConfig);
                            msg = ep.decodeByteArray(exiBytes);
                            session.setAttribute(EXIUtils.EXI_PROCESSOR, ep);
                            session.setAttribute(EXIUtils.EXI_CONFIG, exiConfig);
                            session.setAttribute(EXIAlternativeBindingFilter.quickSetupFlag, true);
                            Log.debug("quick setup: {}", exiConfig);
                        } else {

                        }
                    } catch (EXIException | TransformerException e) {
                        Log.warn("Exception while trying to process received message.", e);
                    }
                }

                if (session.getAttribute(EXIAlternativeBindingFilter.quickSetupFlag).equals(false)) {
                    EXISetupConfiguration exiConfig = new EXISetupConfiguration();
                    if (session.containsAttribute(EXIUtils.EXI_CONFIG)) {
                        exiConfig = (EXISetupConfiguration) session.getAttribute(EXIUtils.EXI_CONFIG);
                    }
                    session.setAttribute(EXIUtils.EXI_PROCESSOR, new EXIProcessor(exiConfig));
                    Log.debug("new EXIProcessor: {} with: {}", session.getAttribute(EXIUtils.EXI_PROCESSOR), exiConfig);
                }
            }
            if (session.containsAttribute(EXIAlternativeBindingFilter.flag)) {
                // Decode EXI bytes
                Log.trace("Decoding {} EXI bytes into XMPP characters, using EXISetupConfigurations: {}", exiBytes.length, session.containsAttribute(EXIUtils.EXI_CONFIG) ? session.getAttribute(EXIUtils.EXI_CONFIG) : new EXISetupConfiguration());
                try {
                    msg = ((EXIProcessor) session.getAttribute(EXIUtils.EXI_PROCESSOR)).decodeByteArray(exiBytes);
                } catch (TransformerException e) {
                    if (session.containsAttribute("exiBytes")) {
                        byte[] anterior = (byte[]) session.getAttribute("exiBytes");
                        byte[] aux = new byte[anterior.length + exiBytes.length];
                        System.arraycopy(exiBytes, 0, aux, anterior.length, exiBytes.length - anterior.length);
                        System.arraycopy(anterior, 0, aux, 0, anterior.length);
                        exiBytes = aux;
                    }
                    session.setAttribute("exiBytes", exiBytes);
                    return;
                }
                session.setAttribute("exiBytes", null);    // old bytes have been used with the last message

                Element xml = DocumentHelper.parseText(msg).getRootElement();
                Log.trace("Decoded EXI bytes into {} XMPP characters.", xml.asXML().length());
                if (xml.getName().equals("open")) {
                    if (session.containsAttribute(EXIAlternativeBindingFilter.agreementSentFlag)) {
                        // this is the last open (after receiving setupResponse)
                        ((EXIFilter) session.getFilterChain().get(EXIFilter.filterName)).addCodec(session);
                        session.write(IoBuffer.wrap(open(null).getBytes()));
                    } else {
                        super.messageReceived(nextFilter, session, translateOpen(xml));
                    }
                    return;
                } else if (xml.getName().equals("setup")) {
                    session.setAttribute(EXIAlternativeBindingFilter.setupFlag, true);
                    String setupResponse = ((EXIFilter) session.getFilterChain().get(EXIFilter.filterName)).setupResponse(xml, session);
                    if (setupResponse != null) {
                        setupResponse = setupResponse.replaceAll("<setupResponse", "<exi:setupResponse")
                            .replaceAll("<schema", "<exi:schema").replaceAll("</setupResponse>", "</exi:setupResponse>");
                        session.write(IoBuffer.wrap(setupResponse.getBytes()));
                    } else {
                        Log.warn("An error occurred while processing alternative negotiation.");
                    }
                    return;
                } else if (xml.getName().equals("presence")) {
                    if (session.getAttribute(EXIAlternativeBindingFilter.quickSetupFlag).equals(true)
                        || !session.containsAttribute(EXIAlternativeBindingFilter.setupFlag)) {
                        super.messageReceived(nextFilter, session, xml.asXML());
                        ((EXIFilter) session.getFilterChain().get(EXIFilter.filterName)).addCodec(session);
                        return;
                    }
                }
                message = xml.asXML();
            } else {
                // Reset to old position in the buffer
                byteBuffer.position(currentPos);
                session.getFilterChain().remove(EXIAlternativeBindingFilter.filterName);
            }
        } else {
            session.getFilterChain().remove(EXIAlternativeBindingFilter.filterName);
        }
        super.messageReceived(nextFilter, session, message);
    }

    static String translateOpen(Element open)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<stream:stream to=\"").append(open.attributeValue("to")).append('\"');
        sb.append(" version=\"").append(open.attributeValue("version")).append('\"');
        //sb.append(" xmlns=\"jabber:client xmlns:stream=\"http://etherx.jabber.org/streams\"");

        Element aux;
        for (Iterator<Element> j = open.elementIterator("xmlns"); j.hasNext(); ) {
            aux = j.next();
            sb.append(" xmlns");
            String prefix = aux.attributeValue("prefix");
            if (prefix != null && !prefix.equals("")) sb.append(":").append(prefix);
            sb.append("=\"").append(aux.attributeValue("namespace")).append('\"');
        }
        sb.append(">");
        return sb.toString();
    }

    static String open(String id)
    {
        String hostName = JiveGlobals.getProperty("xmpp.domain", "127.0.0.1").toLowerCase();
        StringBuilder sb = new StringBuilder();
        sb.append("<exi:streamStart xmlns:exi='http://jabber.org/protocol/compress/exi'")
            .append(" version='1.0' from='").append(hostName).append("' xml:lang='en' xmlns:xml='http://www.w3.org/XML/1998/namespace'");
        if (id != null) {
            sb.append(" id=\"" + id + "\"");
        }
        sb.append("><exi:xmlns prefix='stream' namespace='http://etherx.jabber.org/streams'/>"
            + "<exi:xmlns prefix='' namespace='jabber:client'/>"
            + "<exi:xmlns prefix='xml' namespace='http://www.w3.org/XML/1998/namespace'/>"
            + "</exi:streamStart>");
        return sb.toString();
    }
}
