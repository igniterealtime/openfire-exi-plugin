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
	protected boolean sessionWideBuffers = false;


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
	
	public void setSessionWideBuffers(boolean sessionWideBuffers){
		this.sessionWideBuffers = sessionWideBuffers;
	}
	
	public boolean isSessionWideBuffers(){
		return this.sessionWideBuffers;
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
		sb.append("<setup schemaId='").append(getSchemaId()).append("'");
		
		EXISetupConfiguration def = new EXISetupConfiguration();
		// as attributes
		if(!getCodingMode().equals(def.getCodingMode())){
			if(getCodingMode().equals(CodingMode.COMPRESSION)){
				sb.append(" compression='true'");
			}
			else{
				sb.append(" alignment='").append(getAlignmentString()).append("'");
			}
		}
		if(!getFidelityOptions().equals(def.getFidelityOptions())){
			FidelityOptions fo = getFidelityOptions();
			if(fo.isStrict()){
				sb.append(" strict='true'");
			}
			else{
				if(fo.isFidelityEnabled(FidelityOptions.FEATURE_COMMENT)){
					sb.append(" " + SetupValues.getFidelityOptionString(FidelityOptions.FEATURE_COMMENT) + "='true'");
				}
				if(fo.isFidelityEnabled(FidelityOptions.FEATURE_DTD)){
					sb.append(" " + SetupValues.getFidelityOptionString(FidelityOptions.FEATURE_DTD) + "='true'");
				}
				if(fo.isFidelityEnabled(FidelityOptions.FEATURE_LEXICAL_VALUE)){
					sb.append(" " + SetupValues.getFidelityOptionString(FidelityOptions.FEATURE_LEXICAL_VALUE) + "='true'");
				}
				if(fo.isFidelityEnabled(FidelityOptions.FEATURE_PI)){
					sb.append(" " + SetupValues.getFidelityOptionString(FidelityOptions.FEATURE_PI) + "='true'");
				}
				if(fo.isFidelityEnabled(FidelityOptions.FEATURE_PREFIX)){
					sb.append(" " + SetupValues.getFidelityOptionString(FidelityOptions.FEATURE_PREFIX) + "='true'");
				}
				if(fo.isFidelityEnabled(FidelityOptions.FEATURE_SC) && 
						!(getCodingMode().equals(CodingMode.PRE_COMPRESSION) || getCodingMode().equals(CodingMode.COMPRESSION))){
					sb.append(" " + SetupValues.getFidelityOptionString(FidelityOptions.FEATURE_SC) + "='true'");
				}
			}
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
		if(isSessionWideBuffers()){
			sb.append(" sessionWideBuffers='true'");
		}
		sb.append(">");

		sb.append("</setup>");
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
            else if(att.getName().equals("alignment")){
            	exiConfig.setCodingMode(SetupValues.getCodingMode(att.getValue()));
            }
            else if(att.getName().equals("compression")){
            	if("true".equals(att.getValue())){
            		exiConfig.setCodingMode(CodingMode.COMPRESSION);
            	}
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
            else if(att.getName().equals("sessionWideBuffers")){
            	exiConfig.setSessionWideBuffers(true);
            }
            else if(att.getName().equals("strict")){
            	if("true".equals(att.getValue())){
            		exiConfig.setFidelityOptions(FidelityOptions.createStrict());
            	}
            }
            else 
	        	try {
	        		if(att.getName().equals(SetupValues.getFidelityOptionString(FidelityOptions.FEATURE_COMMENT)) && "true".equals(att.getValue())){
	        			exiConfig.getFidelityOptions().setFidelity(FidelityOptions.FEATURE_COMMENT, true);
	        		}
	        		else if(att.getName().equals(SetupValues.getFidelityOptionString(FidelityOptions.FEATURE_DTD)) && "true".equals(att.getValue())){
	        			exiConfig.getFidelityOptions().setFidelity(FidelityOptions.FEATURE_DTD, true);
	        		}
	        		else if(att.getName().equals(SetupValues.getFidelityOptionString(FidelityOptions.FEATURE_LEXICAL_VALUE)) && "true".equals(att.getValue())){
	        			exiConfig.getFidelityOptions().setFidelity(FidelityOptions.FEATURE_LEXICAL_VALUE, true);
	        		}
	        		else if(att.getName().equals(SetupValues.getFidelityOptionString(FidelityOptions.FEATURE_PI)) && "true".equals(att.getValue())){
	        			exiConfig.getFidelityOptions().setFidelity(FidelityOptions.FEATURE_PI, true);
	        		}
	        		else if(att.getName().equals(SetupValues.getFidelityOptionString(FidelityOptions.FEATURE_PREFIX)) && "true".equals(att.getValue())){
	        			exiConfig.getFidelityOptions().setFidelity(FidelityOptions.FEATURE_PREFIX, true);
	        		}
	        		else if(att.getName().equals(SetupValues.getFidelityOptionString(FidelityOptions.FEATURE_SC)) && "true".equals(att.getValue())){
	        			exiConfig.getFidelityOptions().setFidelity(FidelityOptions.FEATURE_SC, true);
	        		}
				} catch (UnsupportedOption e) {
					e.printStackTrace();
				}
        }
		return exiConfig;
	}
	
}
