/**
 * Copyright 2023 Ignite Realtime Foundation
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

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.Test;
import org.xmlunit.matchers.CompareMatcher;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Unit tests that verify the functionality as implemented by {@link EXIUtils}.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class EXIUtilsTest
{
    @Test
    public void testGenerateStreamStart() throws Exception
    {
        // Setup test fixture.
        final String id = null;
        final String xmppDomain = "example.org";

        // Execute system under test.
        final Element result = EXIUtils.generateStreamStart(id, xmppDomain, false);

        // Verify results.
        assertEquals("streamStart", result.getName());
        assertEquals("http://jabber.org/protocol/compress/exi", result.getNamespace().getURI());
        assertEquals(xmppDomain, result.attributeValue("from"));

        // TODO assertXmlIsSchemaValid(result);
        final String expectedXML =
            "<exi:streamStart xmlns:exi='http://jabber.org/protocol/compress/exi'" +
            " version='1.0' from='"+xmppDomain+"' xml:lang='en' xmlns:xml='http://www.w3.org/XML/1998/namespace'" +
            "><exi:xmlns prefix='stream' namespace='http://etherx.jabber.org/streams'/>" +
            "<exi:xmlns prefix='' namespace='jabber:client'/>" +
            "<exi:xmlns prefix='xml' namespace='http://www.w3.org/XML/1998/namespace'/>" +
            "</exi:streamStart>";

        // If this fails, first read https://github.com/xmlunit/user-guide/wiki/XMLUnit-with-Java-9-and-above
        assertThat(result.asXML(), CompareMatcher.isIdenticalTo(expectedXML).ignoreWhitespace());
    }

    @Test
    public void testGenerateStreamStartWithId() throws Exception
    {
        // Setup test fixture.
        final String id = "abc";
        final String xmppDomain = "example.org";

        // Execute system under test.
        final Element result = EXIUtils.generateStreamStart(id, xmppDomain, false);

        // Verify results.
        assertEquals("streamStart", result.getName());
        assertEquals("http://jabber.org/protocol/compress/exi", result.getNamespace().getURI());
        assertEquals(xmppDomain, result.attributeValue("from"));

        // TODO assertXmlIsSchemaValid(result);
        final String expectedXML =
            "<exi:streamStart xmlns:exi='http://jabber.org/protocol/compress/exi'" +
                " version='1.0' from='"+xmppDomain+"' xml:lang='en' id='"+id+"'" +
                "><exi:xmlns prefix='stream' namespace='http://etherx.jabber.org/streams'/>" +
                "<exi:xmlns prefix='' namespace='jabber:client'/>" +
                "<exi:xmlns prefix='xml' namespace='http://www.w3.org/XML/1998/namespace'/>" +
                "</exi:streamStart>";

        // If this fails, first read https://github.com/xmlunit/user-guide/wiki/XMLUnit-with-Java-9-and-above
        assertThat(result.asXML(), CompareMatcher.isIdenticalTo(expectedXML).ignoreWhitespace());
    }


    @Test
    public void testGenerateStreamStartWithXmlPrefix() throws Exception
    {
        // Setup test fixture.
        final String id = null;
        final String xmppDomain = "example.org";

        // Execute system under test.
        final Element result = EXIUtils.generateStreamStart(id, xmppDomain, true);

        // Verify results.
        assertEquals("streamStart", result.getName());
        assertEquals("http://jabber.org/protocol/compress/exi", result.getNamespace().getURI());
        assertEquals(xmppDomain, result.attributeValue("from"));

        // TODO assertXmlIsSchemaValid(result);
        final String expectedXML =
            "<exi:streamStart xmlns:exi='http://jabber.org/protocol/compress/exi' xmlns:xml='http://www.w3.org/XML/1998/namespace'" +
                " version='1.0' from='"+xmppDomain+"' xml:lang='en' " +
                "><exi:xmlns prefix='stream' namespace='http://etherx.jabber.org/streams'/>" +
                "<exi:xmlns prefix='' namespace='jabber:client'/>" +
                "<exi:xmlns prefix='xml' namespace='http://www.w3.org/XML/1998/namespace'/>" +
                "</exi:streamStart>";

        // If this fails, first read https://github.com/xmlunit/user-guide/wiki/XMLUnit-with-Java-9-and-above
        assertThat(result.asXML(), CompareMatcher.isIdenticalTo(expectedXML).ignoreWhitespace());
    }

    @Test
    public void testConvertStreamStart() throws Exception
    {
        // Setup test fixture.
        final String rawInput =
            "<exi:streamStart xmlns:exi='http://jabber.org/protocol/compress/exi'" +
                " version='1.0' from='unittest@example.org' to='example.com' xml:lang='en'" +
                "><exi:xmlns prefix='stream' namespace='http://etherx.jabber.org/streams'/>" +
                "<exi:xmlns prefix='' namespace='jabber:client'/>" +
                "<exi:xmlns prefix='xml' namespace='http://www.w3.org/XML/1998/namespace'/>" +
                "</exi:streamStart>";
        final Element input = DocumentHelper.parseText(rawInput).getRootElement();

        // Execute system under test.
        final String result = EXIUtils.convertStreamStart(input);

        // Verify results.
        final String expectedXML = "<stream:stream xmlns:stream=\"http://etherx.jabber.org/streams\" xmlns=\"jabber:client\" version=\"1.0\" to=\"example.com\" from=\"unittest@example.org\" xml:lang=\"en\"></stream:stream>";

        // Close the element for dom4j to be able to parse it as valid XML.
        final Element resultModified = DocumentHelper.parseText(result.replaceAll(">", "/>")).getRootElement();

        // If this fails, first read https://github.com/xmlunit/user-guide/wiki/XMLUnit-with-Java-9-and-above
        assertThat(resultModified.asXML(), CompareMatcher.isIdenticalTo(expectedXML).ignoreWhitespace());
    }

    @Test
    public void testAddNewSchemaToCanonicalSchema() throws Exception
    {
        // Setup test fixture.
        final Path canonicalSchema = Files.createTempFile("unit-test-" + getClass().getSimpleName() + "-canonical-", "xsd");
        String csContent = "<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:xmpp:exi:cs' xmlns='urn:xmpp:exi:cs' elementFormDefault='qualified'>\n" +
            "  <xs:import namespace='http://example.org/unit-testing/canonical-schema' schemaLocation='foobar.xsd'/>\n" +
            "</xs:schema>";
        Files.write(canonicalSchema, csContent.getBytes());

        final Path newSchema = Files.createTempFile("unit-test-" + getClass().getSimpleName() + "-newschema-", "xsd");
        final String nsContent = "<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<xs:schema \n" +
            "    xmlns:xs='http://www.w3.org/2001/XMLSchema'\n" +
            "    targetNamespace='http://example.org/unit-test/new-schema'\n" +
            "    elementFormDefault='qualified'>\n" +
            "</xs:schema>";
        Files.write(newSchema, nsContent.getBytes());

        // Execute system under test.
        EXIUtils.addNewSchemaToCanonicalSchema(canonicalSchema, newSchema);

        // Verify results.
        final String expectedXML = "<?xml version='1.0' encoding='UTF-8'?>\n\n" +
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:xmpp:exi:cs' xmlns='urn:xmpp:exi:cs' elementFormDefault='qualified'>\n" +
            "  <xs:import namespace='http://example.org/unit-testing/canonical-schema' schemaLocation='foobar.xsd'/>\n" +
            "  <xs:import namespace='http://example.org/unit-test/new-schema' schemaLocation='"+newSchema.toAbsolutePath()+"'/>\n" +
            "</xs:schema>";

        // If this fails, first read https://github.com/xmlunit/user-guide/wiki/XMLUnit-with-Java-9-and-above
        assertThat(EXIUtils.readFile(canonicalSchema), CompareMatcher.isIdenticalTo(expectedXML).ignoreWhitespace());
    }
}
