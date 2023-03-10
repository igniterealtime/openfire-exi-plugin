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

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.DefaultWriteRequest;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.filter.FilterEvent;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Unit tests that verify the implementation of EXICodecFilter.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@RunWith(MockitoJUnitRunner.class)
public class EXICodedFilterTest
{
    private final static Path oldSchemasFolder = EXIUtils.schemasFolder;
    private final static Path oldSchemasFileLocation = EXIUtils.schemasFileLocation;
    private final static Path oldExiFolder = EXIUtils.exiFolder;
    private final static Path oldDefaultCanonicalSchemaLocation = EXIUtils.defaultCanonicalSchemaLocation;

    @BeforeClass
    public static void mockFolders() throws Exception {
        EXIUtils.schemasFolder = Files.createTempDirectory("unit-test-classes-");

        // Copy all content to temp folder
        try (final Stream<Path> stream = Files.walk(Paths.get("classes"))) {
            stream.forEach(source -> {
                Path destination = EXIUtils.schemasFolder.resolve(source.getFileName());
                try {
                    Files.copy(source, destination);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        EXIUtils.schemasFileLocation = EXIUtils.schemasFolder.resolve("schemas.xml");
        EXIUtils.exiFolder = EXIUtils.schemasFolder.resolve("canonicalSchemas");
        EXIUtils.defaultCanonicalSchemaLocation = EXIUtils.exiFolder.resolve("defaultSchema.xsd");

        EXIUtils.generateSchemasFile();
        EXIUtils.generateDefaultCanonicalSchema();
    }

    @AfterClass
    public static void restoreFolders() {
        EXIUtils.schemasFolder = oldSchemasFolder;
        EXIUtils.schemasFileLocation = oldSchemasFileLocation;
        EXIUtils.exiFolder = oldExiFolder;
        EXIUtils.defaultCanonicalSchemaLocation = oldDefaultCanonicalSchemaLocation;
    }

    /**
     * Asserts that a stanza that is first going out of the filter (being EXI-encoded in the process) and then flows
     * back in (causing the EXI data to be decoded again) is structurally intact.
     */
    @Test
    public void testRoundTrip() throws Exception
    {
        // Setup test fixture.
        final EXISetupConfiguration exiSetupConfiguration = new EXISetupConfiguration();
        final EXIProcessor exiProcessor = new EXIProcessor(exiSetupConfiguration);

        final Message stanza = new Message();
        stanza.setBody("A message used by unit testing as implemented by " + EXICodedFilterTest.class);
        stanza.setFrom(new JID("john", "example.org", "mobile"));
        stanza.setTo(new JID("jane", "example.com", "desktop"));

        final String testData = stanza.toXML();

        final EXICodecFilter filter = new EXICodecFilter();
        final WriteRequest writeRequest = new DefaultWriteRequest(IoBuffer.wrap(testData.getBytes()));
        final TestNextFilter nextFilter = new TestNextFilter();
        final IoSession ioSession = Mockito.mock(IoSession.class);
        Mockito.when(ioSession.getAttribute(EXIUtils.EXI_PROCESSOR)).thenReturn(exiProcessor);

        // Execute system under test.
        filter.filterWrite(nextFilter, ioSession, writeRequest);
        filter.messageReceived(nextFilter, ioSession, writeRequest.getMessage());

        // Verify results.
        assertNotNull(nextFilter.getMessageReceived());
        assertEquals(testData, nextFilter.getMessageReceived());
    }

    public static class TestNextFilter implements IoFilter.NextFilter
    {
        private Object messageReceived;

        @Override public void sessionCreated(IoSession session) {}

        @Override public void sessionOpened(IoSession session) {}

        @Override public void sessionClosed(IoSession session) {}

        @Override public void sessionIdle(IoSession session, IdleStatus status) {}

        @Override public void exceptionCaught(IoSession session, Throwable cause) {}

        @Override public void inputClosed(IoSession session) {}

        @Override public void messageReceived(IoSession session, Object message) {
            this.messageReceived = message;
        }

        public Object getMessageReceived() {
            return messageReceived;
        }

        @Override public void messageSent(IoSession session, WriteRequest writeRequest) {}

        @Override public void filterWrite(IoSession session, WriteRequest writeRequest) {}
        @Override public void filterClose(IoSession session) {}

        @Override public void event(IoSession session, FilterEvent event) {}
    }
}
