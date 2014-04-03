package cl.clayster.exi;

import org.jivesoftware.util.JiveGlobals;

import com.siemens.ct.exi.CodingMode;
import com.siemens.ct.exi.FidelityOptions;
import com.siemens.ct.exi.exceptions.UnsupportedOption;
import com.siemens.ct.exi.helpers.DefaultEXIFactory;

/**
 * Contains all relevant values to setup an EXI compression, in order to propose them to the server.
 * @author Javier Placencio
 *
 */
public class EXISetupConfiguration extends DefaultEXIFactory{
	
	protected String schemaId = "urn:xmpp:exi:default"; 	
	protected boolean quickSetup = false;
	private String canonicalSchemaLocation = JiveGlobals.getHomeDirectory() + EXIUtils.defaultCanonicalSchemaLocation;


	/**
	 * Constructs a new EXISetupConfigurations and initializates it with Default Values.
	 * @param quickSetup indicates whether or not to try quick EXI configuration setup first
	 */
	public EXISetupConfiguration(boolean quickSetup){
		setDefaultValues();
		this.quickSetup = quickSetup;
	}
	
	/**
	 * Constructs a new EXISetupConfigurations and initializates it with Default Values.
	 */
	public EXISetupConfiguration(){
		setDefaultValues();
	}
	
	protected void setDefaultValues() {
		setSchemaIdResolver(new SchemaIdResolver());
		
		setDefaultValues(this);
		setFidelityOptions(FidelityOptions.createStrict());
		
		try {
			fidelityOptions.setFidelity(FidelityOptions.FEATURE_PREFIX, true);
		} catch (UnsupportedOption e) {
			e.printStackTrace();
		}
		
		setValueMaxLength(64);
		setValuePartitionCapacity(64);
		
		setLocalValuePartitions(false);
		//setMaximumNumberOfBuiltInElementGrammars(0);
		//setMaximumNumberOfBuiltInProductions(0);
	}
	
	public String getSchemaId() {
		return schemaId;
	}

	public void setSchemaId(String schemaId) {
		this.schemaId = schemaId;
	}
		
	public boolean isQuickSetup() {
		return quickSetup;
	}

	public void setQuickSetup(boolean quickSetup) {
		this.quickSetup = quickSetup;
	}
	
	public int getAlignmentCode() {
		CodingMode cm = getCodingMode();
		int alignment = (cm.equals(CodingMode.BIT_PACKED)) ? 0 :
        	(cm.equals(CodingMode.BYTE_PACKED)) ? 1 :
        		(cm.equals(CodingMode.PRE_COMPRESSION)) ? 2 :
        			(cm.equals(CodingMode.COMPRESSION)) ? 3: 0;
		return alignment;
	}
	
	public String getAlignmentString() {
		CodingMode cm = getCodingMode();
		String alignment = (cm.equals(CodingMode.BIT_PACKED)) ? "bit-packed" :
        	(cm.equals(CodingMode.BYTE_PACKED)) ? "byte-packed" :
        		(cm.equals(CodingMode.PRE_COMPRESSION)) ? "pre-compression" :
        			(cm.equals(CodingMode.COMPRESSION)) ? "compression": "bit-packed";
		return alignment;
	}
	
	public String getCanonicalSchemaLocation() {
		return canonicalSchemaLocation;
	}

	public void setCanonicalSchemaLocation(String canonicalSchemaLocation) {
		this.canonicalSchemaLocation = canonicalSchemaLocation;
	}
	
	
}
