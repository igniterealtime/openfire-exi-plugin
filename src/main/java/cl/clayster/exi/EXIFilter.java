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

import com.siemens.ct.exi.core.CodingMode;
import com.siemens.ct.exi.core.FidelityOptions;
import com.siemens.ct.exi.core.exceptions.EXIException;
import com.siemens.ct.exi.core.exceptions.UnsupportedOption;
import org.apache.commons.io.FileUtils;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.apache.xerces.impl.dv.util.Base64;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.transform.TransformerException;
import java.io.*;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * This class is a filter that recognizes EXI sessions and adds an EXIEncoder and an EXIDecoder to those sessions.
 * It also implements the basic EXI variables shared by the EXIEncoder and EXIDecoder such as the Grammars.
 *
 * @author Javier Placencio
 */
public class EXIFilter extends IoFilterAdapter
{
    private static final Logger Log = LoggerFactory.getLogger(EXIFilter.class);

    public static final String filterName = "exiFilter";
    private String setupReceived = "setupReceived";

    public EXIFilter()
    {
    }

    @Override
    public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception
    {
        //"<failure xmlns='http://jabber.org/protocol/compress'><unsupported-method/></failure>"
        if (writeRequest.getMessage() instanceof IoBuffer) {
            int currentPos = ((IoBuffer) writeRequest.getMessage()).position();
            String msg = Charset.forName("UTF-8").decode(((IoBuffer) writeRequest.getMessage()).buf()).toString();
            ((IoBuffer) writeRequest.getMessage()).position(currentPos);
            if (session.containsAttribute(setupReceived) && msg.contains("http://jabber.org/protocol/compress") && msg.contains("unsupported-method")) {
                return;
            } else if (msg.contains("</compression>")) {
                msg = msg.replace("</compression>", "<method>exi</method></compression>");
                writeRequest.setMessage(IoBuffer.wrap(msg.getBytes()));
            }
        }
        super.filterWrite(nextFilter, session, writeRequest);
    }

    /**
     * <p>Identifies EXI sessions (based on distinguishing bits -> should be based on Negotiation) and adds an EXIEncoder and EXIDecoder to that session</p>
     *
     * @throws Exception
     */
    @Override
    public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception
    {
        if (message instanceof String) {
            Element xml = null;
            try {
                xml = DocumentHelper.parseText((String) message).getRootElement();
            } catch (DocumentException e) {
                super.messageReceived(nextFilter, session, message);
                return;
            }
            if ("setup".equals(xml.getName())) {
                session.setAttribute(setupReceived, true);
                String setupResponse = setupResponse(xml, session);
                if (setupResponse == null) {
                    Log.warn("An error occurred while processing the negotiation.");
                } else {
                    IoBuffer bb = IoBuffer.wrap(setupResponse.getBytes());
                    session.write(bb);
                }
                return;
            } else if ("downloadSchema".equals(xml.getName())) {
                String url = xml.attributeValue("null", "url");
                if (url != null) {
                    String respuesta = "";
                    try {
                        String descarga = EXIUtils.downloadXml(url);
                        if (descarga.startsWith("<downloadSchemaResponse ")) {
                            // error already found during download process
                            respuesta = descarga;
                        } else {    // SUCCESS!
                            saveDownloadedSchema(descarga, session);
                            respuesta = "<downloadSchemaResponse xmlns='http://jabber.org/protocol/compress/exi' url='" + url + "' result='true'/>";
                        }
                    } catch (
                        DocumentException e) {    // error while parsing the just saved file, not probable (exception makes sense while uploading)
                        respuesta = "<downloadSchemaResponse xmlns='http://jabber.org/protocol/compress/exi' url='" + url
                            + "' result='false'><invalidContentType contentTypeReturned='text/html'/></downloadSchemaResponse>";
                    } catch (Exception e) {
                        respuesta = "<downloadSchemaResponse xmlns='http://jabber.org/protocol/compress/exi' url='" + url
                            + "' result='false'><error message='No free space left.'/></downloadSchemaResponse>";
                    }
                    session.write(IoBuffer.wrap((respuesta).getBytes()));
                    return;
                }
            } else if ("compress".equals(xml.getName()) && "exi".equals(xml.elementText("method"))) {
                EXIProcessor exiProcessor = createExiProcessor(session);
                if (exiProcessor != null) {
                    session.setAttribute(EXIUtils.EXI_PROCESSOR, exiProcessor);
                    String respuesta = "<compressed xmlns='http://jabber.org/protocol/compress'/>";
                    IoBuffer bb = IoBuffer.wrap(respuesta.getBytes());
                    session.write(bb);
                    addCodec(session);
                } else {
                    IoBuffer bb = IoBuffer.wrap("<failure xmlns=\'http://jabber.org/protocol/compress\'><setup-failed/></failure>".getBytes());
                    session.write(bb);
                }
                return;
            }
        }
        super.messageReceived(nextFilter, session, message);
    }


    /**
     * Parses a <setup> stanza sent by the client and generates a corresponding <setupResponse> stanza. It also creates a Configuration Id, which is
     * a UUID followed by '-'; 1 or 0 depending if the configuration is <b>strict</b> or not; and the <b>block size</b> for the EXI Options to use.
     *
     * @param message A <code>String</code> containing the setup stanza
     * @param session the IoSession that represents the connection to the client
     * @return
     */
    String setupResponse(Element setup, IoSession session) throws NoSuchAlgorithmException, IOException
    {
        String setupResponse = null;
        String configId = "";

        //quick setup
        configId = setup.attributeValue("configurationId");
        if (configId != null) {
            Log.debug("Configuration ID found: {}", configId);
            String agreement = "false";
            try {
                EXISetupConfiguration exiConfig = EXISetupConfiguration.parseQuickConfigId(configId);
                if (exiConfig != null) {
                    session.setAttribute(EXIUtils.EXI_CONFIG, exiConfig);
                    agreement = "true";
                }
            } catch (DocumentException e) {
                agreement = "false";
            }
            return "<setupResponse xmlns='http://jabber.org/protocol/compress/exi' agreement='" + agreement + "' configurationId='" + configId + "'/>";
        }

        EXIUtils.generateSchemasFile();

        try {
            // obtener el schemas File del servidor y transformarlo a un elemento XML
            Element serverSchemas;
            String schemasFileContent = EXIUtils.readFile(EXIUtils.schemasFileLocation);
            if (schemasFileContent == null) {
                return null;
            }
            serverSchemas = DocumentHelper.parseText(schemasFileContent).getRootElement();

            boolean missingSchema;
            Element auxSchema1, auxSchema2;
            String ns, bytes, md5Hash;
            boolean agreement = true;    // turns to false when there is a missing schema
            for (Iterator<Element> i = setup.elementIterator("schema"); i.hasNext(); ) {
                auxSchema1 = i.next();
                missingSchema = true;
                ns = auxSchema1.attributeValue("ns");
                bytes = auxSchema1.attributeValue("bytes");
                md5Hash = auxSchema1.attributeValue("md5Hash");
                for (Iterator<Element> j = serverSchemas.elementIterator("schema"); j.hasNext(); ) {
                    auxSchema2 = j.next();
                    if (auxSchema2.attributeValue("ns").equals(ns)
                        && auxSchema2.attributeValue("bytes").equals(bytes)
                        && auxSchema2.attributeValue("md5Hash").equals(md5Hash)) {
                        missingSchema = false;
                        break;
                    }
                }
                if (missingSchema) {
                    auxSchema1.setName("missingSchema");
                    agreement = false;
                }
            }

            if (!agreement) {
                Log.debug("The client's setup includes schemas that we do not recognize.");
                session.getFilterChain().addBefore("xmpp", UploadSchemaFilter.filterName, new UploadSchemaFilter());
            } else {
                Log.debug("The client's setup includes only schemas that we recognize.");
                EXISetupConfiguration exiConfig = new EXISetupConfiguration();
                // guardar el valor de blockSize y strict en session
                String aux = setup.attributeValue(SetupValues.ALIGNMENT);
                if (aux != null) {
                    exiConfig.setCodingMode(SetupValues.getCodingMode(aux));
                } else {
                    aux = setup.attributeValue(SetupValues.COMPRESSION);
                    if (aux != null) {
                        exiConfig.setCodingMode(CodingMode.COMPRESSION);
                    }
                }
                aux = setup.attributeValue(SetupValues.BLOCK_SIZE);
                if (aux != null) {
                    exiConfig.setBlockSize(Integer.parseInt(aux));
                }
                aux = setup.attributeValue(SetupValues.STRICT);
                try {
                    if (aux != null) {
                        exiConfig.getFidelityOptions().setFidelity(FidelityOptions.FEATURE_STRICT, Boolean.parseBoolean(aux));
                    } else {
                        aux = setup.attributeValue(SetupValues.PRESERVE_COMMENTS);
                        if (aux != null) {
                            exiConfig.getFidelityOptions().setFidelity(FidelityOptions.FEATURE_COMMENT, Boolean.parseBoolean(aux));
                        }
                        aux = setup.attributeValue(SetupValues.PRESERVE_DTD);
                        if (aux != null) {
                            exiConfig.getFidelityOptions().setFidelity(FidelityOptions.FEATURE_DTD, Boolean.parseBoolean(aux));
                        }
                        aux = setup.attributeValue(SetupValues.PRESERVE_LEXICAL);
                        if (aux != null) {
                            exiConfig.getFidelityOptions().setFidelity(FidelityOptions.FEATURE_LEXICAL_VALUE, Boolean.parseBoolean(aux));
                        }
                        aux = setup.attributeValue(SetupValues.PRESERVE_PIS);
                        if (aux != null) {
                            exiConfig.getFidelityOptions().setFidelity(FidelityOptions.FEATURE_PI, Boolean.parseBoolean(aux));
                        }
                        aux = setup.attributeValue(SetupValues.PRESERVE_PREFIXES);
                        if (aux != null) {
                            exiConfig.getFidelityOptions().setFidelity(FidelityOptions.FEATURE_PREFIX, Boolean.parseBoolean(aux));
                        }
                    }
                } catch (UnsupportedOption e) {
                    Log.warn("Exception while trying to process a 'setup' from a client.", e);
                }
                aux = setup.attributeValue(SetupValues.VALUE_MAX_LENGTH);
                if (aux != null) {
                    exiConfig.setValueMaxLength(Integer.parseInt(aux));
                }
                aux = setup.attributeValue(SetupValues.VALUE_PARTITION_CAPACITY);
                if (aux != null || "".equals(aux)) {
                    exiConfig.setValuePartitionCapacity(Integer.parseInt(aux));
                }
                aux = setup.attributeValue(SetupValues.WIDE_BUFFERS);
                if (aux != null || "".equals(aux)) {
                    exiConfig.setSessionWideBuffers(true);
                }
                // generate canonical schema
                configId = createCanonicalSchema(setup, serverSchemas);
                exiConfig.setSchemaId(configId);
                session.setAttribute(EXIUtils.EXI_CONFIG, exiConfig);
                session.setAttribute(EXIUtils.SCHEMA_ID, configId);    // still necessary for uploading schemas with UploadSchemaFilter
                exiConfig.saveConfiguration();
                setup.addAttribute("configurationId", exiConfig.getConfigutarionId());
            }
            setup.addAttribute("agreement", String.valueOf(agreement));
            setup.setName("setupResponse");

            setupResponse = setup.asXML();
        } catch (DocumentException | IOException e) {
            Log.warn("Exception while trying to process a 'setup' from a client.", e);
            return null;
        }
        return setupResponse;
    }


    /**
     * Generates a canonical schema out of the schemas' namespaces sent in the <setup> stanza during EXI compression negotiation.
     * Once the server makes sure that it has all schemas needed, it creates a specific canonical schema for the connection being negotiated.
     * It takes the location from a general canonical schema which includes all the schemas contained in a given folder.
     *
     * @param schemasStanzas
     * @throws IOException
     */
    private String createCanonicalSchema(Element setup, Element serverSchemas) throws IOException
    {
        final Map<String, String> serverSchemaLocationsByNamespace = new HashMap<>();
        final Iterator<Element> schema1 = serverSchemas.elementIterator("schema");
        while (schema1.hasNext()) {
            Element next = schema1.next();
            serverSchemaLocationsByNamespace.put(next.attributeValue("ns"), next.attributeValue("schemaLocation"));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version='1.0' encoding='UTF-8'?>"
            + "\n\n<xs:schema "
            + "\n\txmlns:xs='http://www.w3.org/2001/XMLSchema'"
            + "\n\txmlns:stream='http://etherx.jabber.org/streams'"
            + "\n\txmlns:exi='http://jabber.org/protocol/compress/exi'"
            + "\n\ttargetNamespace='urn:xmpp:exi:cs'"
            + "\n\telementFormDefault='qualified'>");

        Element schema;
        for (Iterator<Element> i = setup.elementIterator("schema"); i.hasNext(); ) {
            schema = i.next();
            final String namespace = schema.attributeValue("ns");
            final String schemaLocation = serverSchemaLocationsByNamespace.get(namespace);

            sb.append("\n\t<xs:import namespace='").append(namespace).append("'");
            if (schemaLocation != null) {
                sb.append(" schemaLocation='").append(schemaLocation.replace("/home/guus/SourceCode/IgniteRealtime/openfire-plugins/openfire-exi-plugin/classes/", "../")).append("'");
            }
            sb.append("/>");
        }
        sb.append("\n</xs:schema>");

        String content = sb.toString();
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            Log.warn("Exception while trying to create canonical schema from a 'setup' received from a client.", e);
            return null;
        }
        String schemaId = EXIUtils.bytesToHex(md.digest(content.getBytes()));
        String fileName = EXIUtils.exiFolder + schemaId + ".xsd";

        BufferedWriter newCanonicalSchemaWriter = new BufferedWriter(new FileWriter(fileName));
        newCanonicalSchemaWriter.write(content);
        newCanonicalSchemaWriter.close();
        return schemaId;
    }

    /** Compress **/

    /**
     * Associates an EXIDecoder and an EXIEncoder to this user's session.
     *
     * @param session IoSession associated to the user's socket
     * @return
     */
    EXIProcessor createExiProcessor(IoSession session)
    {
        EXIProcessor exiProcessor = null;
        if (session.containsAttribute(EXIUtils.EXI_CONFIG)) {
            try {
                EXISetupConfiguration exiConfig = (EXISetupConfiguration) session.getAttribute(EXIUtils.EXI_CONFIG);
                exiProcessor = new EXIProcessor(exiConfig);
            } catch (EXIException e) {
                Log.warn("Exception while trying to create an EXI processor.", e);
                return null;
            }
        } else {
            throw new IllegalStateException("Unable to create EXI Processor: no config on session!");
        }
        return exiProcessor;
    }

    /**
     * Adds an EXIEncoder as well as an EXIDecoder to the given IoSession. Also removes EXIFilter and EXIAlternativeBindingFilter from this session
     * if they are contained.
     *
     * @param session the IoSession where the EXI encoder and decoder will be added to.
     */
    void addCodec(IoSession session)
    {
        IoFilterChain fc = session.getFilterChain();
        fc.addBefore("xmpp", "exiCodec", new EXICodecFilter());
        if (fc.contains(EXIFilter.filterName))
            session.getFilterChain().remove(EXIFilter.filterName);
        if (fc.contains(EXIAlternativeBindingFilter.filterName))
            session.getFilterChain().remove(EXIAlternativeBindingFilter.filterName);
        if (fc.contains(UploadSchemaFilter.filterName))
            session.getFilterChain().remove(UploadSchemaFilter.filterName);
        return;
    }


/** uploadSchema **/

    /**
     * Saves a new schema file on the server, which is sent using a Base64 encoding by an EXI client.
     * The name of the file is related to the time when the file was saved.
     *
     * @param content the content of the uploaded schema file (base64 encoded)
     * @return The absolute pathname string denoting the newly created schema file.
     * @throws IOException              while trying to decode the file content using Base64
     * @throws DocumentException
     * @throws NoSuchAlgorithmException
     * @throws TransformerException
     * @throws SAXException
     * @throws EXIException
     */
    void uploadMissingSchema(String content, IoSession session)
        throws IOException, NoSuchAlgorithmException, DocumentException, EXIException, SAXException, TransformerException
    {
        String filePath = EXIUtils.schemasFolder + Calendar.getInstance().getTimeInMillis() + ".xsd";
        OutputStream out = new FileOutputStream(filePath);

        content = content.substring(content.indexOf('>') + 1, content.indexOf("</"));
        byte[] outputBytes = content.getBytes();

        outputBytes = Base64.decode(content);
        out.write(outputBytes);
        out.close();

        String ns = addNewSchemaToSchemasFile(filePath, null, null);
        addNewSchemaToCanonicalSchema(filePath, ns, session);
    }

    void uploadCompressedMissingSchema(byte[] content, String contentType, String md5Hash, String bytes, IoSession session)
        throws IOException, NoSuchAlgorithmException, DocumentException, EXIException, SAXException, TransformerException
    {
        String filePath = EXIUtils.schemasFolder + Calendar.getInstance().getTimeInMillis() + ".xsd";

        if (!"text".equals(contentType) && md5Hash != null && bytes != null) {
            String xml = "";
            if (contentType.equals("ExiDocument")) {
                xml = EXIProcessor.decodeSchemaless(content);

            } else if (contentType.equals("ExiBody")) {
                xml = EXIProcessor.decodeExiBodySchemaless(content);
            }
            EXIUtils.writeFile(filePath, xml);
        }

        String ns = addNewSchemaToSchemasFile(filePath, md5Hash, bytes);
        addNewSchemaToCanonicalSchema(filePath, ns, session);
    }

    /**
     * Saves an uploaded schema to a file. It also processes its md5Hash value and the length in bytes when those parameters are null (for base64 encoded files).
     *
     * @param fileLocation
     * @param md5Hash      md5Hash for the file content for compressed files or null for base64 files
     * @param bytes        number of the file's bytes for compressed files or null for base64 files
     * @return the namespace of the schema being saved
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws DocumentException
     */
    static String addNewSchemaToSchemasFile(String fileLocation, String md5Hash, String bytes) throws NoSuchAlgorithmException, IOException, DocumentException
    {
        MessageDigest md = MessageDigest.getInstance("MD5");
        File file = new File(fileLocation);
        if (md5Hash == null || bytes == null) {
            md5Hash = EXIUtils.bytesToHex(md.digest(FileUtils.readFileToByteArray(file)));
        }
        String ns = EXIUtils.getAttributeValue(EXIUtils.readFile(fileLocation), "targetNamespace");

        // obtener el schemas File del servidor y transformarlo a un elemento XML
        Element serverSchemas;

        if (!new File(EXIUtils.schemasFileLocation).exists()) {    // no more schemas (only the new one)
            EXIUtils.writeFile(EXIUtils.schemasFileLocation, "<setupResponse>\n"
                + "<schema ns='" + ns + "' bytes='" + ((bytes == null) ? file.length() : bytes) + "' md5Hash='" + md5Hash + "' schemaLocation='" + fileLocation + "'/>"
                + "</setupResponse>");
        }

        BufferedReader br = new BufferedReader(new FileReader(EXIUtils.schemasFileLocation));
        StringBuilder sb = new StringBuilder();
        String line = br.readLine();

        while (line != null) {
            sb.append(line);
            line = br.readLine();
        }
        br.close();

        serverSchemas = DocumentHelper.parseText(sb.toString()).getRootElement();

        Element auxSchema;
        @SuppressWarnings("unchecked")
        Iterator<Element> j = serverSchemas.elementIterator("schema");
        int i = 0;    // index where new schema should be (within the list of schemas)
        while (j.hasNext()) {
            auxSchema = j.next();
            if (ns.compareToIgnoreCase(auxSchema.attributeValue("ns")) < 0) {
                // should be placed in this position
                break;
            }
            i++;    // should raise its position only if it is greater than the lastly compared schema
        }

        int i2 = 0; // index where new schema should be (within the file)
        for (int k = -1; k < i; k++) {
            i2 = sb.indexOf("<schema ", i2 + 1);
        }

        String schema = "<schema ns='" + ns + "' bytes='" + ((bytes == null) ? file.length() : bytes) + "' md5Hash='" + md5Hash + "' schemaLocation='" + fileLocation + "'/>";
        if (i2 == -1) {    // should be placed last (no more schemas were found)
            sb.insert(sb.indexOf("</setup"), schema);
        } else {    // should be placed before one found in i2
            sb.insert(i2, schema);
        }


        BufferedWriter schemaWriter = new BufferedWriter(new FileWriter(EXIUtils.schemasFileLocation));
        schemaWriter.write(sb.toString());
        schemaWriter.close();

        return ns;
    }

    static void addNewSchemaToCanonicalSchema(String fileLocation, String ns, IoSession session) throws IOException
    {
        // obtener el schemas File del servidor y transformarlo a un elemento XML
        String canonicalSchemaStr = EXIUtils.readFile(EXIUtils.exiFolder + session.getAttribute(EXIUtils.SCHEMA_ID) + ".xsd");
        StringBuilder canonicalSchemaStrBuilder = new StringBuilder();
        if (canonicalSchemaStr != null && canonicalSchemaStr.indexOf("namespace") != -1) {
            canonicalSchemaStrBuilder = new StringBuilder(canonicalSchemaStr);
            String aux = canonicalSchemaStrBuilder.toString(), importedNamespace = ">";    // importedNamespace makes it possible to start right before 'xs:import' elements
            int index;
            do {
                aux = aux.substring(aux.indexOf(importedNamespace) + importedNamespace.length());
                importedNamespace = EXIUtils.getAttributeValue(aux, "namespace");
            } while (importedNamespace != null && ns.compareTo(importedNamespace) > 0 && aux.indexOf("<xs:import ") != -1);
            index = canonicalSchemaStrBuilder.indexOf(aux.substring(aux.indexOf('>') + 1));
            canonicalSchemaStrBuilder.insert(index, "\n\t<xs:import namespace='" + ns + "' schemaLocation='" + fileLocation + "'/>");
        } else {
            canonicalSchemaStrBuilder = new StringBuilder();
            canonicalSchemaStrBuilder.append("<?xml version='1.0' encoding='UTF-8'?> \n\n<xs:schema \n\txmlns:xs='http://www.w3.org/2001/XMLSchema' \n\ttargetNamespace='urn:xmpp:exi:cs' \n\txmlns='urn:xmpp:exi:cs' \n\telementFormDefault='qualified'>\n");
            canonicalSchemaStrBuilder.append("\n\t<xs:import namespace='" + ns + "' schemaLocation='" + fileLocation + "'/>");
            canonicalSchemaStrBuilder.append("\n</xs:schema>");
        }

        File canonicalSchema = new File(EXIUtils.exiFolder + session.getAttribute(EXIUtils.SCHEMA_ID) + ".xsd");
        BufferedWriter canonicalSchemaWriter = new BufferedWriter(new FileWriter(canonicalSchema));
        canonicalSchemaWriter.write(canonicalSchemaStrBuilder.toString());
        canonicalSchemaWriter.close();

        session.setAttribute(EXIUtils.CANONICAL_SCHEMA_LOCATION, canonicalSchema.getAbsolutePath());
    }

    /* downloadSchema */

    /**
     * @param schema
     * @param session
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws DocumentException
     **/
    private void saveDownloadedSchema(String content, IoSession session) throws NoSuchAlgorithmException, IOException, DocumentException
    {
        String filePath = EXIUtils.schemasFolder + Calendar.getInstance().getTimeInMillis() + ".xsd";

        OutputStream out = new FileOutputStream(filePath);
        out.write(content.getBytes());
        out.close();

        String ns = addNewSchemaToSchemasFile(filePath, null, null);
        addNewSchemaToCanonicalSchema(filePath, ns, session);
    }
}
