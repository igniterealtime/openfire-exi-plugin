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
import org.dom4j.DocumentException;
import org.xml.sax.SAXException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Calendar;

/**
 * This class is used to process <uploadSchema> stanzas, which carry a schema that will be used for the compression that is being negotiated.
 *
 * @author Javier Placencio
 */
public class UploadSchemaFilter extends IoFilterAdapter
{
    static String filterName = "uploadSchemaFilter";
    final String uploadSchemaStartTag = "<uploadSchema";
    final String uploadSchemaEndTag = "</uploadSchema>";
    final String setupStartTag = "<setup";
    final String setupEndTag = "</setup>";
    final String compressStartTag = "<compress";
    final String compressEndTag = "</compress>";

    public UploadSchemaFilter()
    {
    }

    @Override
    public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception
    {
        // Decode the bytebuffer and print it to the stdout
        if (message instanceof IoBuffer) {
            IoBuffer byteBuffer = (IoBuffer) message;
            // Decode buffer
            Charset encoder = StandardCharsets.UTF_8;
            CharBuffer charBuffer = encoder.decode(byteBuffer.buf());
            byte[] bba = byteBuffer.array();

            String cont = (String) session.getAttribute("cont");
            if (cont == null) cont = "";
            session.setAttribute("cont", "");
            String msg = cont + charBuffer;

            byte[] baCont = (byte[]) session.getAttribute("baCont");
            if (baCont == null) baCont = new byte[]{};
            bba = EXIUtils.concat(baCont, bba);

            do {
                if (msg.startsWith(uploadSchemaStartTag)) {
                    if (!msg.contains(uploadSchemaEndTag)) {
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

                    if (contentType != null && !"text".equals(contentType) && md5Hash != null && bytes != null) {
                        // the same as before but for the byte buffer array
                        int srcPos = EXIUtils.indexOf(bba, uploadSchemaEndTag.getBytes()) + uploadSchemaEndTag.getBytes().length;
                        if (srcPos != -1) {
                            baCont = new byte[bba.length - srcPos];
                            System.arraycopy(bba, srcPos, baCont, 0, bba.length - srcPos);
                            System.arraycopy(bba, 0, bba, 0, srcPos);
                            session.setAttribute("baCont", baCont);
                        }

                        byte[] ba = new byte[EXIUtils.indexOf(bba, uploadSchemaEndTag.getBytes()) - startTagStr.getBytes().length];
                        System.arraycopy(bba, startTagStr.getBytes().length, ba, 0, ba.length);
                        uploadCompressedMissingSchema(ba, contentType, md5Hash, bytes, session);
                    } else {
                        uploadMissingSchema(msg, session);
                    }
                } else if (msg.startsWith(setupStartTag)) {
                    if (!msg.contains(setupEndTag)) {
                        session.setAttribute("cont", msg);
                        return;
                    }
                    cont = msg.substring(msg.indexOf(setupEndTag) + setupEndTag.length());
                    session.setAttribute("cont", cont);
                    msg = msg.substring(0, msg.indexOf(setupEndTag) + setupEndTag.length());

                    session.getFilterChain().remove(filterName);
                    super.messageReceived(nextFilter, session, msg);
                    return;
                } else if (msg.startsWith("<iq") || msg.startsWith("<presence")) {
                    session.setAttribute("baCont", null);
                    session.setAttribute("cont", null);
                    super.messageReceived(nextFilter, session, message);
                    return;
                } else {
                    session.setAttribute("baCont", bba);
                    session.setAttribute("cont", msg);
                    return;
                }
                msg = cont;
                bba = baCont;
            } while (!cont.equals(""));
        }
        // Pass the message to the next filter
        super.messageReceived(nextFilter, session, message);
    }

    void uploadCompressedMissingSchema(byte[] content, String contentType, String md5Hash, String bytes, IoSession session)
        throws IOException, NoSuchAlgorithmException, DocumentException, EXIException, SAXException, TransformerException
    {
        Path filePath = EXIUtils.getSchemasFolder().resolve(Calendar.getInstance().getTimeInMillis() + ".xsd");

        if (!"text".equals(contentType) && md5Hash != null && bytes != null) {
            String xml = "";
            if (contentType.equals("ExiDocument")) {
                xml = EXIProcessor.decodeSchemaless(content);
            } else if (contentType.equals("ExiBody")) {
                xml = EXIProcessor.decodeExiBodySchemaless(content);
            }
            EXIUtils.writeFile(filePath, xml);
        }

        EXIFilter.addNewSchemaToSchemasFile(filePath, md5Hash, bytes);
        EXIFilter.addNewSchemaToCanonicalSchema(filePath, session);
    }

    void uploadMissingSchema(String content, IoSession session)
        throws IOException, NoSuchAlgorithmException, DocumentException, EXIException, SAXException, TransformerException
    {
        Path filePath = EXIUtils.getSchemasFolder().resolve(Calendar.getInstance().getTimeInMillis() + ".xsd");
        OutputStream out = Files.newOutputStream(filePath);

        String contentB64 = content.substring(content.indexOf('>') + 1, content.indexOf("</"));

        byte[] outputBytes = Base64.getDecoder().decode(contentB64);
        out.write(outputBytes);
        out.close();

        EXIFilter.addNewSchemaToSchemasFile(filePath, null, null);
        EXIFilter.addNewSchemaToCanonicalSchema(filePath, session);
    }
}
