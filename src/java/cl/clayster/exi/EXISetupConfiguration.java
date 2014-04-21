package cl.clayster.exi;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

import org.dom4j.Attribute;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import com.siemens.ct.exi.CodingMode;
import com.siemens.ct.exi.EncodingOptions;
import com.siemens.ct.exi.FidelityOptions;
import com.siemens.ct.exi.exceptions.UnsupportedOption;
import com.siemens.ct.exi.helpers.DefaultEXIFactory;

/**
 * Contains all relevant values to setup an EXI compression, in order to propose them to the server.
 * @author Javier Placencio
 *
 */
public class EXISetupConfiguration extends DefaultEXIFactory{
	
	protected String configurationId;
	protected String schemaId;


	/**
	 * Constructs a new EXISetupConfigurations and initializates it with Default Values.
	 * @param quickSetup indicates whether or not to try quick EXI configuration setup first
	 */
	public EXISetupConfiguration(boolean quickSetup){
		setDefaultValues();
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
		
		//setFidelityOptions(FidelityOptions.createStrict());
		try {
			getFidelityOptions().setFidelity(FidelityOptions.FEATURE_PREFIX, true);
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
		return schemaId == null ? "urn:xmpp:exi:default" : schemaId;
	}

	public void setSchemaId(String schemaId) {
		this.schemaId = schemaId;
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
		if(schemaId != null){
			return EXIUtils.exiFolder + schemaId + ".xsd";
		}
		else{
			return EXIUtils.defaultCanonicalSchemaLocation;
		}
	}
	
	public String getConfigutarionId() {
		return configurationId;
	}

	public void setConfigurationId(String configurationId) {
		this.configurationId = configurationId;
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append("<configuration schemaId='").append(getSchemaId()).append("'");
		
		EXISetupConfiguration def = new EXISetupConfiguration();
		// as attributes
		if(isFragment() ^ def.isFragment()){
			sb.append(" isFragment='").append(isFragment()).append("'");
		}
		if(!getCodingMode().equals(def.getCodingMode())){
			sb.append(" codingMode='").append(getCodingMode()).append("'");
		}
		if(getBlockSize() != def.getBlockSize()){
			sb.append(" blockSize='").append(getBlockSize()).append("'");
		}
		if(getValueMaxLength() != def.getValueMaxLength()){
			sb.append(" valueMaxLength='").append(getValueMaxLength()).append("'");
		}
		if(getValuePartitionCapacity() != def.getValuePartitionCapacity()){
			sb.append(" valuePartitionCapacity='").append(getValuePartitionCapacity()).append("'");
		}
		if(isLocalValuePartitions() ^ def.isLocalValuePartitions()){
			sb.append(" isLocalValuePartitions='").append(isLocalValuePartitions()).append("'");
		}
		if(getMaximumNumberOfBuiltInElementGrammars() != def.getMaximumNumberOfBuiltInElementGrammars()){
			sb.append(" maximumNumberOfBuiltInElementGrammars='").append(getMaximumNumberOfBuiltInElementGrammars()).append("'");
		}
		if(getMaximumNumberOfBuiltInProductions() != def.getMaximumNumberOfBuiltInProductions()){
			sb.append(" maximumNumberOfBuiltInProductions='").append(getMaximumNumberOfBuiltInProductions()).append("'");
		}
		sb.append(">");
		
		// as elements
		if(!getFidelityOptions().equals(def.getFidelityOptions())){
			if(getFidelityOptions().isStrict()){
				sb.append("<fidelityOptions " + FidelityOptions.FEATURE_STRICT + "='true'/>");
			}
			else{
				sb.append("<fidelityOptions");
				String fo = getFidelityOptions().toString();
				int i = fo.indexOf('['); 
				if(i != -1){
					fo = fo.substring(i + 1);
				}
				i = fo.indexOf(']');
				if(i != -1){
					fo = fo.substring(0, i);
				}
				String[] lista = fo.split(", ");
				for(String s : lista){
					if(s != null && !s.equals("")){
						sb.append(" " + s + "='true'");
					}
				}
				sb.append("/>");
			}
		}
		if(!getEncodingOptions().equals(def.getEncodingOptions())){
			sb.append("<encodingOptions");
			String fo = getFidelityOptions().toString();
			int i = fo.indexOf('['); 
			if(i != -1){
				fo = fo.substring(i + 1);
			}
			i = fo.indexOf(']');
			if(i != -1){
				fo = fo.substring(0, i);
			}
			String[] lista = fo.split(", ");
			for(String s : lista){
				if(s != null && !s.equals("")){
					sb.append(" " + s + "='true'");
				}
			}
			sb.append("/>");
		}
		sb.append("</configuration>");
		return sb.toString();
	}
	
	/**
	 * Saves this EXI configurations to a file, unless the same configurations have been saved already
	 * @return true if this configurations are saved, false otherwise 
	 * @throws IOException
	 */
	public boolean saveConfiguration() throws IOException {
		String content = this.toString();
        
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			String configId = EXIUtils.bytesToHex(md.digest(content.getBytes()));
			setConfigurationId(configId);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}		
		
		String fileName = EXIUtils.exiFolder + configurationId + ".xml";
		if(new File(fileName).exists()){
			return true;
		}
		else{
			if(EXIUtils.writeFile(fileName, content)){
				return true;
			}
			else{
				System.err.println("Error while trying to save the file. Configurations were not saved.");
				return false;
			} 
		}
	}

	/**
	 * Looks a saved EXI configuration with the given configuration ID
	 * @param configId the configuration ID to look for
	 * @return an EXISetupConfiguration instance if the configuration exists. <i>null</i> otherwise
	 * @throws DocumentException
	 */
	public static EXISetupConfiguration parseQuickConfigId(String configId) throws DocumentException {
		String fileLocation = EXIUtils.exiFolder + configId + ".xml";
		String content = EXIUtils.readFile(fileLocation);
		if(content == null)
			return null;
		
		Element configElement = DocumentHelper.parseText(content).getRootElement();
		
		EXISetupConfiguration exiConfig = new EXISetupConfiguration();
		exiConfig.setConfigurationId(configId);
		// iterate through attributes of root 
        for (@SuppressWarnings("unchecked") Iterator<Attribute> i = configElement.attributeIterator(); i.hasNext(); ) {
            Attribute att = (Attribute) i.next();
            if(att.getName().equals("schemaId")){
            	exiConfig.setSchemaId(att.getValue());
            	if(!new File(exiConfig.getCanonicalSchemaLocation()).exists()){
            		return null;
            	}	
            }
            else if(att.getName().equals("isFragment")){
            	exiConfig.setFragment(att.getValue().equals("true"));
            }
            else if(att.getName().equals("codingMode")){
            	exiConfig.setCodingMode(parseCodingMode(att.getValue()));
            }
            else if(att.getName().equals("blockSize")){
            	exiConfig.setBlockSize(Integer.valueOf(att.getValue()));
            }
            else if(att.getName().equals("valueMaxLength")){
            	exiConfig.setValueMaxLength(Integer.valueOf(att.getValue()));
            }
            else if(att.getName().equals("valuePartitionCapacity")){
            	exiConfig.setValuePartitionCapacity(Integer.valueOf(att.getValue()));
            }
            else if(att.getName().equals("isLocalValuePartitions")){
            	exiConfig.setLocalValuePartitions(att.getValue().equals("true"));
            }
            else if(att.getName().equals("maximumNumberOfBuiltInElementGrammars")){
            	exiConfig.setMaximumNumberOfBuiltInElementGrammars(Integer.valueOf(att.getValue()));
            }
            else if(att.getName().equals("maximumNumberOfBuiltInProductions")){
            	exiConfig.setMaximumNumberOfBuiltInProductions(Integer.valueOf(att.getValue()));
            }
        }
        // iterate through child elements of root
        for ( @SuppressWarnings("unchecked")Iterator<Element> i = configElement.elementIterator(); i.hasNext(); ) {
            Element element = (Element) i.next();
            if(element.getName().equals("fidelityOptions")){
            	FidelityOptions fo = FidelityOptions.createDefault();
            	for (@SuppressWarnings("unchecked") Iterator<Attribute> i1 = element.attributeIterator(); i1.hasNext(); ) {
                    Attribute att = (Attribute) i1.next();
                    try {
						fo.setFidelity(att.getName(), true);
					} catch (UnsupportedOption e) {
						e.printStackTrace();
					}
            	}
            	exiConfig.setFidelityOptions(fo);
            }
            else if(element.getName().equals("encodingOptions")){
            	EncodingOptions eo = EncodingOptions.createDefault();
            	for (@SuppressWarnings("unchecked") Iterator<Attribute> i1 = element.attributeIterator(); i1.hasNext(); ) {
                    Attribute att = (Attribute) i1.next();
                    try {
						eo.setOption(att.getName());
					} catch (UnsupportedOption e) {
						e.printStackTrace();
					}
            	}
            	exiConfig.setEncodingOptions(eo);
            }
        }
		return exiConfig;
	}
	
	private static CodingMode parseCodingMode(String cm){
		if(cm.equalsIgnoreCase("BIT_PACKED")){
			return CodingMode.BIT_PACKED;
		}
		else if(cm.equalsIgnoreCase("BYTE_PACKED")){
			return CodingMode.BYTE_PACKED;
		}
		else if(cm.equalsIgnoreCase("PRE_COMPRESSION")){
			return CodingMode.PRE_COMPRESSION;
		}
		else {
			return CodingMode.COMPRESSION;
		}
	}
}
