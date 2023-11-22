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
import com.siemens.ct.exi.core.grammars.Grammars;
import com.siemens.ct.exi.grammars.GrammarFactory;
import org.dom4j.DocumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SchemaIdResolver implements com.siemens.ct.exi.core.SchemaIdResolver
{
    private static final Logger Log = LoggerFactory.getLogger(SchemaIdResolver.class);

    @Override
    public Grammars resolveSchemaId(String schemaId) throws EXIException
    {
		/*
		if (schemaId == null || "".equals(schemaId)) {
			return GrammarFactory.newInstance().createGrammars(EXIUtils.defaultCanonicalSchemaLocation);
		} else {
		*/
        final Path schemaIdPath = EXIUtils.getExiFolder().resolve(schemaId + ".xsd");
        if (Files.exists(schemaIdPath)) {
            try {
                Log.trace("Found schema file for schema ID '{}'. Using it to populate grammar.", schemaId);
                final Grammars result = GrammarFactory.newInstance().createGrammars(schemaIdPath.toAbsolutePath().toString(), new SchemaResolver());
                result.setSchemaId(schemaId);
                return result;
            } catch (IOException | ParserConfigurationException | SAXException | DocumentException e) {
                Log.warn("Exception while trying to resolve schema ID '{}'.", schemaId, e);
            }
        } else {
            Log.trace("Unable to find schema file for schema ID '{}'. Using schema-less grammar.", schemaId);
            return GrammarFactory.newInstance().createSchemaLessGrammars();
        }
        throw new EXIException("schema not found. Id: " + schemaId);
        //}
    }
}
