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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class SchemaResolver implements XMLEntityResolver {
	
	HashMap<String, String> canonicalPaths = new HashMap<String, String>();
	HashMap<String, String> names = new HashMap<String, String>();
	

	public SchemaResolver () throws IOException{
		File folder = new File(EXIUtils.schemasFolder);
		if(!folder.isDirectory()){
			return;
		}
        File[] listOfFiles = folder.listFiles();
        File file;
        String fileLocation;
		
		String targetNamespaceStr = "targetNamespace=", namespace = null;
		int t;
		
        for (int i = 0; i < listOfFiles.length; i++) {
        	file = listOfFiles[i];
        	if (file.isFile() && file.getName().endsWith(".xsd")) {	
        		fileLocation = file.getAbsolutePath();
				if(fileLocation == null)	break;
				
				// buscar el namespace del schema
				namespace = EXIUtils.readFile(fileLocation);
				t = namespace.indexOf(targetNamespaceStr);
				if(t != -1){
					// obtener el namespace
					namespace = namespace.substring(t + targetNamespaceStr.length());
					//namespace = namespace.substring(0, namespace.indexOf(namespace.codePointAt(0), 1) + 1);	// CON comillas
					namespace = namespace.substring(1, namespace.indexOf(namespace.codePointAt(0), 1));	// SIN comillas
					}
				this.canonicalPaths.put(namespace, file.getCanonicalPath());
				this.names.put(namespace, file.getName());
        	}
		}
        this.canonicalPaths.put("-//W3C//DTD XMLSCHEMA 200102//EN", new File(EXIUtils.schemasFolder + "XMLSchema.dtd").getAbsolutePath());
        this.canonicalPaths.put("datatypes", new File(EXIUtils.schemasFolder + "datatypes.dtd").getAbsolutePath());
	}
	
	@Override
	public XMLInputSource resolveEntity(XMLResourceIdentifier resourceIdentifier) throws XNIException, IOException {
		
		String namespace = resourceIdentifier.getNamespace();
		if(namespace != null){
			if(this.canonicalPaths.containsKey(namespace)){				
				String location = this.canonicalPaths.get(namespace);
			return new XMLInputSource(resourceIdentifier.getPublicId(), location, resourceIdentifier.getBaseSystemId());
			}
		}	
		return null;
	}

}
