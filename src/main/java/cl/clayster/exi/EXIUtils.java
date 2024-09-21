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

import org.apache.commons.io.FileUtils;
import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Contains useful methods to execute EXI functions needed by {@link EXIFilter} such as reading a file, getting an attribute from an XML document, among others.
 *
 * @author Javier Placencio
 */
public class EXIUtils
{
    private static final Logger Log = LoggerFactory.getLogger(EXIUtils.class);

    private static Path schemasFolder;
    private static Path schemasFileLocation ;
    private static Path exiFolder;
    private static Path defaultCanonicalSchemaLocation;
    final static String CANONICAL_SCHEMA_LOCATION = "canonicalSchemaLocation";
    final static String EXI_CONFIG = "exiConfig";
    final static String SCHEMA_ID = "schemaId";
    final static String EXI_PROCESSOR = EXIProcessor.class.getName();

    final protected static char[] hexArray = "0123456789abcdef".toCharArray();

    synchronized static Path getSchemasFolder() {
        if (schemasFolder == null) {
            schemasFolder = Paths.get(JiveGlobals.getHomeDirectory(), "plugins", "exi", "classes");
        }
        return schemasFolder;
    }

    synchronized static void setSchemasFolder(Path folder) {
        schemasFolder = folder;
        schemasFileLocation = null;
        exiFolder = null;
        defaultCanonicalSchemaLocation = null;
    }

    synchronized static Path getSchemasFileLocation() {
        if (schemasFileLocation == null) {
            schemasFileLocation = getSchemasFolder().resolve("schemas.xml");
        }
        return schemasFileLocation;
    }

    synchronized static Path getExiFolder() {
        if (exiFolder == null) {
            exiFolder = getSchemasFolder().resolve("canonicalSchemas");
        }
        return exiFolder;
    }

    synchronized static Path getDefaultCanonicalSchemaLocation() {
        if (defaultCanonicalSchemaLocation == null) {
            defaultCanonicalSchemaLocation = getExiFolder().resolve("defaultSchema.xsd");
        }
        return defaultCanonicalSchemaLocation;
    }

    /**
     * Returns a hexadecimal String representation of the given bytes.
     *
     * @param bytes an array of bytes to be represented as a hexadecimal String
     */
    public static String bytesToHex(byte[] bytes)
    {
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
	    /*
	    // remove extra 000000s in the end of the string
	    int i = hexChars.length - 1;
	    while(hexChars[i] == '0'){
	    	i--;
	    }
	    if(i % 2 == 0){
	    	i++;
	    }
	    String str = new String(hexChars);
	    str = str.substring(0, i+1);
	    return str;
	    /**/
    }

    public static String readFile(Path fileLocation)
    {
        try {
            return FileUtils.readFileToString(fileLocation.toFile());
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean writeFile(Path fileName, String content)
    {
        try {
            if (fileName != null && content != null) {
                FileOutputStream out;

                out = new FileOutputStream(fileName.toFile());
                out.write(content.getBytes());
                out.close();
                return true;
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    public static String getAttributeValue(String text, String attribute)
    {
        attribute = " " + attribute;
        if (!text.contains(attribute)) {
            return null;
        }
        text = text.substring(text.indexOf(attribute) + attribute.length());    // starting after targetNamespace
        text = text.substring(0, text.indexOf('>'));    // cut what comes after '>'
        char comilla = '\'';
        if (text.indexOf(comilla) == -1) {
            comilla = '"';
        }
        text = text.substring(text.indexOf(comilla) + 1);
        text = text.substring(0, text.indexOf(comilla));
        return text;
    }

    /***************** server only methods ****************/
    public static String downloadXml(String url)
    {
        StringBuilder sb = new StringBuilder();
        String responseContent = "<error message=''/>";
        URLConnection uConn = null;
        try {
            uConn = new URL(url).openConnection();
            // look for errors
            switch (((HttpURLConnection) uConn).getResponseCode()) {
                case -1:
                    responseContent = "<unknownError/>";
                    break;
                case 404:    // HTTP error
                    responseContent = "<httpError code='404' message='Not Found'/>";
                    break;
                case 400:case 401:case 402:case 403: case 405:case 406:case 407:case 408:case 409:case 410:case 411:case 412:case 413:case 414:case 415:
                case 416:case 417:case 418:case 419:case 420:case 421:case 422:case 423:case 424:case 425:case 426:case 427:case 428:case 429:case 430:
                case 431:case 440:case 444:case 449:case 450:case 451:case 495:case 496:case 497:case 499:
                    responseContent = "<httpError code='" + ((HttpURLConnection) uConn).getResponseCode() + "' message='Client Error'/>";
                    break;
                case 500:case 501:case 502:case 503:case 504:case 505:case 506:case 507:case 508:case 509:case 510:
                case 511:case 522:case 523:case 524:case 598:case 599:
                    responseContent = "<httpError code='" + ((HttpURLConnection) uConn).getResponseCode() + "' message='Server Error'/>";
                    break;
                default:    // SUCCESS!
                    String inputLine;
                    BufferedReader in = new BufferedReader(new InputStreamReader(uConn.getInputStream()));
                    while ((inputLine = in.readLine()) != null) {
                        sb.append(inputLine).append('\n');
                    }
                    in.close();
                    DocumentHelper.parseText(sb.toString());
                    return sb.substring(0, sb.length());
            }
        } catch (MalformedURLException e) {
            responseContent = "<invalidUrl message='Unrecognized schema.'/>";
        } catch (SocketTimeoutException e) {
            responseContent = "<timeout message='No response returned.'/>";
        } catch (DocumentException e) {
            int sc = uConn.getContentType().indexOf(';');
            String contentType = sc != -1 ? uConn.getContentType().substring(0, sc) : uConn.getContentType();
            responseContent = "<invalidContentType contentTypeReturned='" + contentType + "'/>";
        } catch (Exception e) {
            responseContent = "<error message='No free space left.'/>";
        }

        return ("<downloadSchemaResponse xmlns='http://jabber.org/protocol/compress/exi' url='" + url
            + "' result='false'>" + responseContent + "</downloadSchemaResponse>");
    }

    /**
     * Returns the index within <code>data</code> of the first occurrence of <code>pattern</code>.
     *
     * @param data    the byte[] where to look for the pattern
     * @param pattern the pattern to look for within data
     * @return the index where pattern was found or -1 if it was not found
     */
    public static int indexOf(byte[] data, byte[] pattern)
    {
        int index = -1;
        int count = 0;
        if (!(data == null || pattern == null || data.length < 1 || pattern.length < 1) && data.length >= pattern.length) {
            for (index = 0; index <= data.length - pattern.length; index++) {
                if (data[index] == pattern[0]) {
                    count = 1;
                    for (int p = 1; p < pattern.length; p++) {
                        if (data[index + p] != pattern[p]) break;
                        count++;
                    }
                    if (count == pattern.length) break;
                }
            }
            if (count < pattern.length) index = -1;
        }
        return index;
    }

    /**
     * Returns a new byte array, which is the result of concatenating a and b.
     *
     * @param a the first part of the resulting byte array
     * @param b the second part of the resulting byte array
     * @return the resulting byte array
     */
    public static byte[] concat(byte[] a, byte[] b)
    {
        if (a == null || a.length == 0) return b;
        if (b == null || b.length == 0) return a;
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    /**
     * Looks for all schema files (*.xsd) in the given folder and creates two new files:
     * a canonical schema file which imports all existing schema files;
     * and an XML file called schema.xml which contains each schema namespace, file size in bytes and its md5Hash code
     */
    static void generateSchemasFile() throws IOException
    {
        try {
            Files.createDirectories(EXIUtils.getSchemasFolder());
            Files.createDirectories(EXIUtils.getExiFolder());

            // Read all XSDs
            final Set<Path> xsds;
            try (final Stream<Path> stream = Files.walk(EXIUtils.getSchemasFolder(), 1)) {
                xsds = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".xsd"))
                    .collect(Collectors.toSet());
            }

            // Process each XSD.
            final List<Element> schemaElements = new ArrayList<>();
            for (final Path xsd : xsds) {
                final byte[] data = Files.readAllBytes(xsd);
                final String content = new String(data);
                final org.dom4j.Document doc = DocumentHelper.parseText(content);

                final byte[] hash = MessageDigest.getInstance("MD5").digest(data);
                final String md5Hash = EXIUtils.bytesToHex(hash);
                final String namespace = doc.getRootElement().attributeValue("targetNamespace");
                final String fileLocation = xsd.toAbsolutePath().toString();

                // schemasStanzas also contains schemaLocation to make it easier to generate a new canonicalSchema later.
                final Element newSchema = DocumentHelper.createElement("schema")
                    .addAttribute("ns", namespace)
                    .addAttribute("bytes", String.valueOf(data.length))
                    .addAttribute("md5Hash", md5Hash)
                    .addAttribute("schemaLocation", fileLocation);
                schemaElements.add(newSchema);
            }

            // XEP-0322 wants canonical schemas to be ordered by namespace.
            schemaElements.sort(Comparator.comparing(element -> element.attributeValue("ns")));

            // Write to file.
            final Document schemasFile = DocumentHelper.createDocument();
            final Element schemas = schemasFile.addElement("schemas");
            schemaElements.forEach(schemas::add);

            try (final FileWriter fileWriter = new FileWriter(EXIUtils.getSchemasFileLocation().toFile()))
            {
                final XMLWriter writer = new XMLWriter(fileWriter, OutputFormat.createPrettyPrint());
                writer.write(schemasFile);
                writer.close();
            }
        } catch (NoSuchAlgorithmException | DocumentException e) {
            Log.warn("Exception while trying to generate schema files.", e);
        }
    }

    /**
     * Generates XEP-0322's default canonical schema
     */
    static void generateDefaultCanonicalSchema() throws IOException
    {
        String[] schemasNeeded = {"http://etherx.jabber.org/streams", "http://jabber.org/protocol/compress/exi"};
        boolean[] schemasFound = {false, false};
        Element setup;
        try {
            setup = DocumentHelper.parseText(EXIUtils.readFile(EXIUtils.getSchemasFileLocation())).getRootElement();
        } catch (DocumentException e) {
            Log.warn("Exception while trying to generate default canonical schema.", e);
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version='1.0' encoding='UTF-8'?>"
            + "\n\n<xs:schema "
            + "\n\txmlns:xs='http://www.w3.org/2001/XMLSchema'"
            + "\n\txmlns:stream='http://etherx.jabber.org/streams'"
            + "\n\txmlns:exi='http://jabber.org/protocol/compress/exi'"
            + "\n\ttargetNamespace='urn:xmpp:exi:default'"
            + "\n\telementFormDefault='qualified'>");

        Element schema;
        for (Iterator<Element> i = setup.elementIterator("schema"); i.hasNext(); ) {
            schema = i.next();
            String ns = schema.attributeValue("ns");
            if (ns.equalsIgnoreCase(schemasNeeded[0])) {
                schemasFound[0] = true;
                if (schemasFound[1]) {
                    break;
                }
            } else if (ns.equalsIgnoreCase(schemasNeeded[1])) {
                schemasFound[1] = true;
                if (schemasFound[0]) {
                    break;
                }
            }
        }
        if (schemasFound[0] && schemasFound[1]) {
            sb.append("\n\t<xs:import namespace='").append(schemasNeeded[0]).append("'/>");
            sb.append("\n\t<xs:import namespace='").append(schemasNeeded[1]).append("'/>");
        } else {
            throw new IOException("Missing schema for default canonical schema: " + (schemasFound[0] ? schemasNeeded[0] : schemasNeeded[1]));
        }
        sb.append("\n</xs:schema>");

        String content = sb.toString();

        BufferedWriter newCanonicalSchemaWriter = new BufferedWriter(new FileWriter(EXIUtils.getDefaultCanonicalSchemaLocation().toFile()));
        newCanonicalSchemaWriter.write(content);
        newCanonicalSchemaWriter.close();
    }

    public static void addNewSchemaToCanonicalSchema(final Path canonicalSchemaPath, final Path newSchema) throws DocumentException, IOException
    {
        final Document canonicalSchema;
        if (Files.exists(canonicalSchemaPath)) {
            canonicalSchema = DocumentHelper.parseText(EXIUtils.readFile(canonicalSchemaPath));
        } else {
            canonicalSchema = DocumentHelper.createDocument();
            final Element root = canonicalSchema.addElement(QName.get("schema", "http://www.w3.org/2001/XMLSchema"));
            root.addAttribute("elementFormDefault", "qualified");
        }

        final Namespace existingNamespace = canonicalSchema.getRootElement().getNamespaceForURI("http://www.w3.org/2001/XMLSchema");

        final String namespace = EXIUtils.getAttributeValue(EXIUtils.readFile(newSchema), "targetNamespace");
        canonicalSchema.getRootElement().addElement(QName.get("import", existingNamespace))
            .addAttribute("namespace", namespace)
            .addAttribute("schemaLocation", newSchema.toString());

        canonicalSchema.normalize();

        try (final FileWriter fileWriter = new FileWriter(canonicalSchemaPath.toFile()))
        {
            final XMLWriter writer = new XMLWriter(fileWriter, OutputFormat.createPrettyPrint());
            writer.write(canonicalSchema);
            writer.close();
        }
    }

    /**
     * Generates an EXI 'streamStart' XML element.
     *
     * @param id Unknown - does not appear to be defined in the XEP.
     * @param xmppDomain the XMPP domain that's used as the 'from' attribute value.
     * @param addXmlNamespaceToRoot true to add a prefix definition for the XML namespace to the generated root element.
     * @return An XML element of the name 'streamStart'
     */
    public static Element generateStreamStart(String id, String xmppDomain, boolean addXmlNamespaceToRoot)
    {
        final Element result = DocumentHelper.createElement(QName.get("streamStart","exi", "http://jabber.org/protocol/compress/exi"));
        if (addXmlNamespaceToRoot) {
            result.addNamespace(Namespace.XML_NAMESPACE.getPrefix(), Namespace.XML_NAMESPACE.getURI());
        }
        result.addAttribute("version", "1.0");
        result.addAttribute("from", xmppDomain);
        result.addAttribute(QName.get("lang", Namespace.XML_NAMESPACE), "en");
        if (id != null) {
            result.addAttribute("id", id);
        }

        // These are the XML namespaces and their prefixes that are to be communicated as being available in the stream.
        final List<Namespace> namespaces = Arrays.asList(
            Namespace.get("stream", "http://etherx.jabber.org/streams"),
            Namespace.get("", "jabber:client"),
            Namespace.get(Namespace.XML_NAMESPACE.getPrefix(), Namespace.XML_NAMESPACE.getURI())
        );

        for (final Namespace namespace : namespaces) {
            result.addElement(QName.get("xmlns", "exi", "http://jabber.org/protocol/compress/exi"))
                .addAttribute("prefix", namespace.getPrefix())
                .addAttribute("namespace", namespace.getURI());
        }
        return result;
    }

    /**
     * Translates a 'streamStart' element to a corresponding 'stream' start element.
     *
     * The return value will be an unclosed start element.
     *
     * @param streamStart the element to convert
     * @return the converted element as an unclosed start element that is named 'stream'
     */
    static String convertStreamStart(final Element streamStart)
    {
        // Hard-code the prefix to be 'stream', as some software is rumored to depend on that.
        final Element stream = DocumentHelper.createElement(QName.get("stream","stream", "http://etherx.jabber.org/streams"));
        for (final Attribute attribute : streamStart.attributes()) {
            stream.addAttribute(attribute.getQName(), attribute.getValue());
        }

        for (final Element xmlnsElement : streamStart.elements("xmlns")) {
            final String prefix = xmlnsElement.attributeValue("prefix");
            final String namespace = xmlnsElement.attributeValue("namespace");

            if ("http://etherx.jabber.org/streams".equals(namespace)) {
                // Skip this namespace - we've hardcoded it to the 'stream' prefix in the root element.
                continue;
            }

            stream.addNamespace(prefix, namespace);
        }

        // Only return the opening element (must not be closed).
        String result = stream.asXML();
        if (result.endsWith("</stream:stream>")) {
            result = result.substring(0, result.length()-"</stream:stream>".length());
        }
        if (result.endsWith("/>")) {
            result = result.substring(0, result.length()-2) + ">";
        }
        return result;
    }
}
