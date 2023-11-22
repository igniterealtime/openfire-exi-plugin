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
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.apache.xerces.impl.dv.util.Base64;
import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.transform.TransformerException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

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
    private final String setupReceived = "setupReceived";

    public EXIFilter()
    {
    }

    @Override
    public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception
    {
        //"<failure xmlns='http://jabber.org/protocol/compress'><unsupported-method/></failure>"
        if (writeRequest.getMessage() instanceof IoBuffer) {
            int currentPos = ((IoBuffer) writeRequest.getMessage()).position();
            String msg = StandardCharsets.UTF_8.decode(((IoBuffer) writeRequest.getMessage()).buf()).toString();
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
     * Identifies EXI sessions (based on distinguishing bits -> should be based on Negotiation) and adds an EXIEncoder and EXIDecoder to that session
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
                    IoBuffer bb = IoBuffer.wrap("<failure xmlns='http://jabber.org/protocol/compress'><setup-failed/></failure>".getBytes());
                    session.write(bb);
                }
                return;
            }
        }
        super.messageReceived(nextFilter, session, message);
    }


    /**
     * Parses a <setup> stanza sent by the client and generates a corresponding <setupResponse> stanza. It also creates a Configuration ID, which is
     * a UUID followed by '-'; 1 or 0 depending on the configuration being <b>strict</b> or not; and the <b>block size</b> for the EXI Options to use.
     *
     * @param setup   A <code>String</code> containing the setup stanza
     * @param session the IoSession that represents the connection to the client
     * @return a setupResponse element
     */
    String setupResponse(Element setup, IoSession session) throws IOException
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
            String schemasFileContent = EXIUtils.readFile(EXIUtils.getSchemasFileLocation());
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
        } catch (DocumentException | IOException | NoSuchAlgorithmException e) {
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
     * @param setup         The setup stanza as received from a peer
     * @param serverSchemas An XML document representing the schemas currently recognized by the server.
     */
    private String createCanonicalSchema(Element setup, Element serverSchemas) throws IOException, NoSuchAlgorithmException
    {
        final Map<String, String> serverSchemaLocationsByNamespace = new HashMap<>();
        final Iterator<Element> schema1 = serverSchemas.elementIterator("schema");
        while (schema1.hasNext()) {
            Element next = schema1.next();
            serverSchemaLocationsByNamespace.put(next.attributeValue("ns"), next.attributeValue("schemaLocation"));
        }

        final Document canonicalSchema = DocumentHelper.createDocument();
        final Element root = canonicalSchema.addElement(QName.get("schema", "http://www.w3.org/2001/XMLSchema"));
        root.addNamespace("stream", "http://etherx.jabber.org/streams");
        root.addNamespace("exi", "http://jabber.org/protocol/compress/exi");
        root.addAttribute("targetNamespace", "urn:xmpp:exi:cs");
        root.addAttribute("elementFormDefault", "qualified");

        Element schema;
        for (Iterator<Element> i = setup.elementIterator("schema"); i.hasNext(); ) {
            schema = i.next();
            final String namespace = schema.attributeValue("ns");
            final String schemaLocation = serverSchemaLocationsByNamespace.get(namespace);

            final Element importElement = root.addElement("import", "http://www.w3.org/2001/XMLSchema");
            importElement.addAttribute("namespace", namespace);
            if (schemaLocation != null) {
                importElement.addAttribute("schemaLocation", schemaLocation);
            }
        }

        final MessageDigest md = MessageDigest.getInstance("MD5");
        final String schemaId = EXIUtils.bytesToHex(md.digest(canonicalSchema.asXML().getBytes()));

        final Path fileName = EXIUtils.getExiFolder().resolve(schemaId + ".xsd");
        try (final FileWriter fileWriter = new FileWriter(fileName.toFile()))
        {
            final XMLWriter writer = new XMLWriter(fileWriter, OutputFormat.createPrettyPrint());
            writer.write(canonicalSchema);
            writer.close();
        }

        return schemaId;
    }

    /* Compress **/

    /**
     * Associates an EXIDecoder and an EXIEncoder to this user's session.
     *
     * @param session IoSession associated to the user's socket
     * @return The processor associated to the user's socket.
     */
    EXIProcessor createExiProcessor(IoSession session)
    {
        EXIProcessor exiProcessor;
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
    }


    /* uploadSchema **/

    /**
     * Saves a new schema file on the server, which is sent using a Base64 encoding by an EXI client.
     * The name of the file is related to the time when the file was saved.
     *
     * @param content the content of the uploaded schema file (base64 encoded)
     * @throws IOException while trying to decode the file content using Base64
     */
    void uploadMissingSchema(String content, IoSession session)
        throws IOException, NoSuchAlgorithmException, DocumentException, EXIException, SAXException, TransformerException
    {
        content = content.substring(content.indexOf('>') + 1, content.indexOf("</"));
        byte[] outputBytes = Base64.decode(content);

        final Path filePath = EXIUtils.getSchemasFolder().resolve(Calendar.getInstance().getTimeInMillis() + ".xsd");
        try (final OutputStream out = Files.newOutputStream(filePath)) {
            out.write(outputBytes);
        }

        addNewSchemaToSchemasFile(filePath, null, null);
        addNewSchemaToCanonicalSchema(filePath, session);
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

        addNewSchemaToSchemasFile(filePath, md5Hash, bytes);
        addNewSchemaToCanonicalSchema(filePath, session);
    }

    /**
     * Saves an uploaded schema to a file. It also processes its md5Hash value and the length in bytes when those parameters are null (for base64 encoded files).
     *
     * @param fileLocation Location of the file
     * @param md5Hash      md5Hash for the file content for compressed files or null for base64 files
     * @param bytes        number of the file's bytes for compressed files or null for base64 files
     */
    static void addNewSchemaToSchemasFile(Path fileLocation, String md5Hash, String bytes) throws NoSuchAlgorithmException, IOException, DocumentException
    {
        if (md5Hash == null || bytes == null) {
            final byte[] data = Files.readAllBytes(fileLocation);
            final byte[] hash = MessageDigest.getInstance("MD5").digest(data);
            md5Hash = EXIUtils.bytesToHex(hash);
        }
        String ns = EXIUtils.getAttributeValue(EXIUtils.readFile(fileLocation), "targetNamespace");

        // Create schemas file if it does not exist yet.
        final Document document;
        if (!Files.exists(EXIUtils.getSchemasFileLocation())) {    // no more schemas (only the new one)
            document = DocumentHelper.createDocument();
            document.addElement("setupResponse");
        } else {
            // obtener el schemas File del servidor y transformarlo a un elemento XML
            final String content = String.join("", Files.readAllLines(EXIUtils.getSchemasFileLocation()));
            document = DocumentHelper.parseText(content);
        }

        final Element newSchema = DocumentHelper.createElement("schema")
            .addAttribute("ns", ns)
            .addAttribute("bytes", bytes == null ? String.valueOf(Files.size(fileLocation)) : bytes)
            .addAttribute("md5Hash", md5Hash)
            .addAttribute("schemaLocation", fileLocation.toString());

        // Add new schema to collection of schema's that's in the file.
        final Element root = document.getRootElement();
        final List<Element> schemas = root.elements("schema");
        schemas.add(newSchema);

        // XEP-0322 wants canonical schemas to be ordered by namespace.
        schemas.sort(Comparator.comparing(element -> element.attributeValue("ns")));

        try (final FileWriter fileWriter = new FileWriter(EXIUtils.getSchemasFileLocation().toFile()))
        {
            final XMLWriter writer = new XMLWriter(fileWriter, OutputFormat.createPrettyPrint());
            writer.write(document);
            writer.close();
        }
    }

    static void addNewSchemaToCanonicalSchema(Path fileLocation, IoSession session) throws IOException, DocumentException
    {
        // obtener el schemas File del servidor y transformarlo a un elemento XML
        final Path canonicalSchema = EXIUtils.getExiFolder().resolve(session.getAttribute(EXIUtils.SCHEMA_ID) + ".xsd");
        EXIUtils.addNewSchemaToCanonicalSchema(canonicalSchema, fileLocation);
        session.setAttribute(EXIUtils.CANONICAL_SCHEMA_LOCATION, canonicalSchema.toAbsolutePath().toString());
    }

    /* downloadSchema */

    private void saveDownloadedSchema(String content, IoSession session) throws NoSuchAlgorithmException, IOException, DocumentException
    {
        Path filePath = EXIUtils.getSchemasFolder().resolve(Calendar.getInstance().getTimeInMillis() + ".xsd");

        OutputStream out = Files.newOutputStream(filePath);
        out.write(content.getBytes());
        out.close();

        addNewSchemaToSchemasFile(filePath, null, null);
        addNewSchemaToCanonicalSchema(filePath, session);
    }
}
