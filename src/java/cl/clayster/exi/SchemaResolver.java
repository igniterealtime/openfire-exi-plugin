package cl.clayster.exi;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLInputSource;

public class SchemaResolver implements XMLEntityResolver {
	
	HashMap<String, String> absolutePaths = new HashMap<String, String>();
	HashMap<String, String> canonicalPaths = new HashMap<String, String>();
	HashMap<String, String> names = new HashMap<String, String>();

	public SchemaResolver (String folderLocation) throws IOException{
		File folder = new File(folderLocation);
        File[] listOfFiles = folder.listFiles();
        File file;
        String fileLocation;
		
		String targetNamespaceStr = "targetNamespace=", namespace = null;
		int t;
		
		// variables to write the stanzas in the right order (namepsace alfabethical order)
        for (int i = 0; i < listOfFiles.length; i++) {
        	file = listOfFiles[i];
        	if (file.isFile() && file.getName().endsWith(".xsd") && !file.getName().endsWith("canonicalSchema.xsd")) { // se hace lo siguiente para cada archivo XSD en la carpeta folder	
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
				this.absolutePaths.put(namespace, file.getAbsolutePath());
				this.canonicalPaths.put(namespace, file.getCanonicalPath());
				this.names.put(namespace, file.getName());
        	}
		}
        
        /*
        for(Entry<String, String> entry : this.absolutePaths.entrySet()){
        	System.out.println(entry.getKey() + "\n\t" + entry.getValue() 
        			+ "\n\t" + this.canonicalPaths.get(entry.getKey()) 
        			+ "\n\t" + this.names.get(entry.getKey()));   	
        }
        /**/
	}
	
	@Override
	public XMLInputSource resolveEntity(XMLResourceIdentifier resourceIdentifier)
			throws XNIException, IOException {
		
		String namespace = resourceIdentifier.getNamespace();
		String publicId = resourceIdentifier.getPublicId();

		if(namespace != null){
			if(this.canonicalPaths.containsKey(namespace)){				
				String location = this.canonicalPaths.get(namespace);
			return new XMLInputSource(resourceIdentifier.getPublicId(), location, resourceIdentifier.getBaseSystemId());
			}
		}
		/*
		else if("-//W3C//DTD XMLSCHEMA 200102//EN".equals(publicId)){
			String location = "C:/Users/Javier/workspace/Personales/ExiClient/res/XMLSchema.dtd";
System.out.println("->Resource returned: " + location);
			return new XMLInputSource(publicId, location, resourceIdentifier.getBaseSystemId());
		}
		else if("datatypes".equals(publicId)){
			String location = "C:/Users/Javier/workspace/Personales/ExiClient/res/datatypes.dtd";
System.out.println("->Resource returned: " + location);
			return new XMLInputSource(publicId, location, resourceIdentifier.getBaseSystemId());
		}
		/**/		
		return null;
	}

}
