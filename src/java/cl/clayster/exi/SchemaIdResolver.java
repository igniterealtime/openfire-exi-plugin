package cl.clayster.exi;

import java.io.File;
import java.io.IOException;

import org.jivesoftware.util.JiveGlobals;

import com.siemens.ct.exi.GrammarFactory;
import com.siemens.ct.exi.exceptions.EXIException;
import com.siemens.ct.exi.grammars.Grammars;

public class SchemaIdResolver implements com.siemens.ct.exi.SchemaIdResolver {
	
	@Override
	public Grammars resolveSchemaId(String schemaId) throws EXIException {
		Grammars g = null;
		File f = new File(JiveGlobals.getHomeDirectory() + EXIUtils.exiSchemasFolder + schemaId + ".xsd");
		if(f.exists()){
			try {
				g = GrammarFactory.newInstance().createGrammars(f.getAbsolutePath(), new SchemaResolver(JiveGlobals.getHomeDirectory() + EXIUtils.schemasFolder));
				g.setSchemaId(schemaId);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return g;
	}

}
