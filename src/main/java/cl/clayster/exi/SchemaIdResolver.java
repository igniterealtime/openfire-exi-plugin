package cl.clayster.exi;

import com.siemens.ct.exi.core.exceptions.EXIException;
import com.siemens.ct.exi.core.grammars.Grammars;
import com.siemens.ct.exi.grammars.GrammarFactory;

import java.io.File;
import java.io.IOException;

public class SchemaIdResolver implements com.siemens.ct.exi.core.SchemaIdResolver
{
	
	@Override
	public Grammars resolveSchemaId(String schemaId) throws EXIException
    {
		/*
		if (schemaId == null || "".equals(schemaId)) {
			return GrammarFactory.newInstance().createGrammars(EXIUtils.defaultCanonicalSchemaLocation);
		} else {
		*/
		Grammars g = null;
		File f = new File(EXIUtils.exiFolder + schemaId + ".xsd");
		if(f.exists()){
			try {
				g = GrammarFactory.newInstance().createGrammars(f.getAbsolutePath(), new SchemaResolver());
				g.setSchemaId(schemaId);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		else{
			g = GrammarFactory.newInstance().createSchemaLessGrammars();
		}
		if(g == null)	throw new EXIException("schema not found. Id: " + schemaId);
		return g;
		//}
	}

}
