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

import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SchemaResolver implements XMLEntityResolver
{
    private static final Logger Log = LoggerFactory.getLogger(SchemaResolver.class);

    private final Map<String, String> namespaceToPath;

    public SchemaResolver() throws ParserConfigurationException, IOException, SAXException
    {
        namespaceToPath = new HashMap<>();

        // Iterate over all files to record a namespace-to-path mapping.
        final File folder = new File(EXIUtils.schemasFolder);
        if (!folder.isDirectory()) {
            throw new IllegalStateException("Configured schema folder is not a directory: " + EXIUtils.schemasFolder);
        }
        final File[] listOfFiles = folder.listFiles();
        if (listOfFiles == null) {
            throw new IllegalStateException("Configured schema folder contains no files: " + EXIUtils.schemasFolder);
        }

        final DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

        for (final File file : listOfFiles) {
            if (file.isFile() && file.getName().endsWith(".xsd")) {
                final String fileLocation = file.getAbsolutePath();

                final Document doc = builder.parse(file);
                doc.getDocumentElement().normalize();
                final String namespace = doc.getDocumentElement().getAttribute("targetNamespace");

                Log.debug("Found namespace '{}' in file: {}", namespace, fileLocation);
                this.namespaceToPath.put(namespace, fileLocation);
            }
        }

        // Do not use the schema that is defining the schema as content of the schema!
        namespaceToPath.remove("urn:xmpp:exi:cs");

        // Add DTDs
        this.namespaceToPath.put("-//W3C//DTD XMLSCHEMA 200102//EN", new File(EXIUtils.schemasFolder + "XMLSchema.dtd").getAbsolutePath());
        this.namespaceToPath.put("datatypes", new File(EXIUtils.schemasFolder + "datatypes.dtd").getAbsolutePath());
    }

    public XMLInputSource resolveEntity(XMLResourceIdentifier resourceIdentifier) throws XNIException, IOException
    {
        String needle = resourceIdentifier.getNamespace();
        if (needle == null) {
            needle = resourceIdentifier.getPublicId();
        }
        XMLInputSource result = null;
        if (needle != null) {
            if (this.namespaceToPath.containsKey(needle)) {
                String location = this.namespaceToPath.get(needle);
                result = new XMLInputSource(resourceIdentifier.getPublicId(), location, resourceIdentifier.getBaseSystemId());
                //Log.trace("Resolved namespace: '{}' to: {}", needle, result.getSystemId());
            } else {
                //Log.debug("Unable to resolved namespace: '{}'", needle);
            }
        } else {
            //Log.trace("Skipping no-namespace lookup for resource identifier: {}", resourceIdentifier);
        }
        return result;
    }
}
