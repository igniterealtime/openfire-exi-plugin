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
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SchemaResolver implements XMLEntityResolver
{
    private static final Logger Log = LoggerFactory.getLogger(SchemaResolver.class);

    private final Map<String, Path> namespaceToPath;

    public SchemaResolver() throws ParserConfigurationException, IOException, SAXException, DocumentException
    {
        namespaceToPath = new HashMap<>();

        // Iterate over all files to record a namespace-to-path mapping.
        if (!Files.isDirectory(EXIUtils.getSchemasFolder())) {
            throw new IllegalStateException("Configured schema folder is not a directory: " + EXIUtils.getSchemasFolder());
        }

        final Set<Path> xsds;
        try (final Stream<Path> stream = Files.walk(EXIUtils.getSchemasFolder(), 1)) {
            xsds = stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".xsd"))
                .collect(Collectors.toSet());
        }

        if (xsds.isEmpty()) {
            throw new IllegalStateException("Configured schema folder contains no files: " + EXIUtils.getSchemasFolder());
        }

        for (final Path xsd : xsds) {
            final String content = String.join("", Files.readAllLines(xsd));
            final org.dom4j.Document doc = DocumentHelper.parseText(content);
            final String namespace = doc.getRootElement().attributeValue("targetNamespace");

            Log.debug("Found namespace '{}' in file: {}", namespace, xsd);
            this.namespaceToPath.put(namespace, xsd);
        }

        // Do not use the schema that is defining the schema as content of the schema!
        namespaceToPath.remove("urn:xmpp:exi:cs");

        // Add DTDs
        this.namespaceToPath.put("-//W3C//DTD XMLSCHEMA 200102//EN", EXIUtils.getSchemasFolder().resolve("XMLSchema.dtd"));
        this.namespaceToPath.put("datatypes", EXIUtils.getSchemasFolder().resolve("datatypes.dtd"));
    }

    public XMLInputSource resolveEntity(XMLResourceIdentifier resourceIdentifier) throws XNIException, IOException
    {
        String needle = resourceIdentifier.getNamespace();
        if (needle == null) {
            needle = resourceIdentifier.getPublicId();
        }
        XMLInputSource result = null;
        if (needle != null) {
            Path location = this.namespaceToPath.get(needle);
            if (location != null) {
                result = new XMLInputSource(resourceIdentifier.getPublicId(), location.toAbsolutePath().toString(), resourceIdentifier.getBaseSystemId());
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
