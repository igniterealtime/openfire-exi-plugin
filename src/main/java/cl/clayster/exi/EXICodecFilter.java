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

import com.siemens.ct.exi.core.exceptions.EXIException;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.openfire.XMPPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.transform.TransformerException;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Decodes EXI stanzas from a specific IoSession and stores the JID address of the respective user.
 * This allows to easily relate both sessions and remove the encoder when the session is closed.
 *
 * @author Javier Placencio
 */
public class EXICodecFilter extends IoFilterAdapter
{
    private static final Logger Log = LoggerFactory.getLogger(EXICodecFilter.class);

    public EXICodecFilter()
    {
    }

    @Override
    public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception
    {
        if (writeRequest.getMessage() instanceof IoBuffer) {
            final ByteBuffer bytes = ((IoBuffer) writeRequest.getMessage()).buf();
            final String msg = UTF_8.decode(bytes).toString();
            final String outputMsg;
            if (msg.startsWith("</stream:stream>")) {
                if (session.containsAttribute(EXIAlternativeBindingFilter.flag)) {
                    outputMsg = "<streamEnd xmlns:exi='http://jabber.org/protocol/compress/exi'/>";
                } else {
                    outputMsg = "<exi:streamEnd xmlns:exi='http://jabber.org/protocol/compress/exi'/>";
                }
            } else if (msg.startsWith("<exi:streamStart")) {
                final Element startStream = EXIUtils.generateStreamStart(null, XMPPServer.getInstance().getServerInfo().getXMPPDomain(), false);
                outputMsg = startStream.asXML();
            } else {
                outputMsg = msg;
            }
            Log.trace("Encoding {} XMPP characters into EXI bytes for session {}", outputMsg.length(), session.hashCode());
            try {
                EXIProcessor attribute = (EXIProcessor) session.getAttribute(EXIUtils.EXI_PROCESSOR);
                IoBuffer bb = attribute.encodeByteBuffer(outputMsg);
                writeRequest.setMessage(bb);
                super.filterWrite(nextFilter, session, writeRequest);
                return;
            } catch (EXIException e) {
                Log.warn("Exception while trying to filter a write.", e);
            }
        }
        super.filterWrite(nextFilter, session, writeRequest);
    }

    @Override
    public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception
    {
        if (message instanceof IoBuffer) {
            IoBuffer byteBuffer = (IoBuffer) message;
            byte[] exiBytes = (byte[]) session.getAttribute("exiBytes");
            if (exiBytes == null) {
                exiBytes = new byte[byteBuffer.limit()];
                System.arraycopy(byteBuffer.array(), 0, exiBytes, 0, exiBytes.length);
            } else {
                byte[] dest = new byte[exiBytes.length + byteBuffer.limit()];
                System.arraycopy(exiBytes, 0, dest, 0, exiBytes.length);
                System.arraycopy(byteBuffer.array(), 0, dest, exiBytes.length, byteBuffer.limit());
                exiBytes = dest;
            }

            Log.trace("Decoding {} EXI bytes from session {} into XMPP characters.", exiBytes.length, session.hashCode());
            if (!EXIProcessor.isEXI(exiBytes[0])) {
                super.messageReceived(nextFilter, session, message);
            } else {
                BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(exiBytes));
                while (bis.available() > 0) {
                    // Decode EXI bytes
                    try {
                        bis.mark(exiBytes.length);
                        String xmlStr = ((EXIProcessor) session.getAttribute(EXIUtils.EXI_PROCESSOR)).decode(bis);
                        Element xml = DocumentHelper.parseText(xmlStr).getRootElement();
                        session.setAttribute("exiBytes", null); // old bytes have been used with the last message
                        if ("streamStart".equals(xml.getName())) {
                            String open = EXIUtils.convertStreamStart(xml);
                            final Element startStream = EXIUtils.generateStreamStart(null, XMPPServer.getInstance().getServerInfo().getXMPPDomain(), false);
                            session.write(IoBuffer.wrap(startStream.asXML().getBytes()));
                            super.messageReceived(nextFilter, session, open);
                            return;
                        } else if ("streamEnd".equals(xml.getName())) {
                            xmlStr = "</stream:stream>";
                        }
                        super.messageReceived(nextFilter, session, xmlStr);
                    } catch (TransformerException e) {
                        bis.reset();
                        byte[] restingBytes = new byte[bis.available()];
                        bis.read(restingBytes);
                        Log.trace("Unable to decode EXI data received from session {}. Incomplete data? Saving for later processing.", session.hashCode(), e);
                        session.setAttribute("exiBytes", restingBytes);
                        super.messageReceived(nextFilter, session, IoBuffer.wrap("".getBytes()));
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void sessionClosed(NextFilter nextFilter, IoSession session) throws Exception
    {
        super.sessionClosed(nextFilter, session);
    }

}
