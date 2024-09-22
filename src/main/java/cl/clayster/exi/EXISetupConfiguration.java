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

import com.siemens.ct.exi.core.CodingMode;
import com.siemens.ct.exi.core.FidelityOptions;
import com.siemens.ct.exi.core.exceptions.UnsupportedOption;
import com.siemens.ct.exi.core.helpers.DefaultEXIFactory;
import org.dom4j.Attribute;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

import static com.siemens.ct.exi.core.CodingMode.*;
import static com.siemens.ct.exi.core.FidelityOptions.*;

/**
 * Contains all relevant values to set up an EXI compression, in order to propose them to the server.
 *
 * @author Javier Placencio
 */
public class EXISetupConfiguration extends DefaultEXIFactory
{
    private static final Logger Log = LoggerFactory.getLogger(EXISetupConfiguration.class);

    protected String configurationId;
    protected String schemaId;
    protected boolean sessionWideBuffers = false;

    /**
     * Constructs a new EXISetupConfigurations and initializes it with Default Values.
     *
     * @param quickSetup indicates whether to try quick EXI configuration setup first
     */
    public EXISetupConfiguration(boolean quickSetup)
    {
        setDefaultValues();
    }

    /**
     * Constructs a new EXISetupConfigurations and initializes it with Default Values.
     */
    public EXISetupConfiguration()
    {
        setDefaultValues();
    }

    protected void setDefaultValues()
    {
        setSchemaIdResolver(new SchemaIdResolver());

        setDefaultValues(this);

        try {
            getFidelityOptions().setFidelity(FEATURE_PREFIX, true);
        } catch (UnsupportedOption e) {
            Log.warn("Exception while trying to set default values.", e);
        }

        setValueMaxLength(64);
        setValuePartitionCapacity(64);

        setLocalValuePartitions(false);
        //setMaximumNumberOfBuiltInElementGrammars(0);
        //setMaximumNumberOfBuiltInProductions(0);
    }

    public String getSchemaId()
    {
        return schemaId == null ? "urn:xmpp:exi:default" : schemaId;
    }

    public void setSchemaId(String schemaId)
    {
        this.schemaId = schemaId;
    }

    public int getAlignmentCode()
    {
        CodingMode cm = getCodingMode();
        return (cm.equals(BIT_PACKED)) ? 0 :
            (cm.equals(BYTE_PACKED)) ? 1 :
                (cm.equals(PRE_COMPRESSION)) ? 2 :
                    (cm.equals(COMPRESSION)) ? 3 : 0;
    }

    public String getAlignmentString()
    {
        CodingMode cm = getCodingMode();
        return (cm.equals(BIT_PACKED)) ? "bit-packed" :
            (cm.equals(BYTE_PACKED)) ? "byte-packed" :
                (cm.equals(PRE_COMPRESSION)) ? "pre-compression" :
                    (cm.equals(COMPRESSION)) ? "compression" : "bit-packed";
    }

    public void setSessionWideBuffers(boolean sessionWideBuffers)
    {
        this.sessionWideBuffers = sessionWideBuffers;
    }

    public boolean isSessionWideBuffers()
    {
        return this.sessionWideBuffers;
    }

    public Path getCanonicalSchemaLocation()
    {
        if (schemaId != null) {
            return EXIUtils.getExiFolder().resolve(schemaId + ".xsd");
        } else {
            return EXIUtils.getDefaultCanonicalSchemaLocation();
        }
    }

    public String getConfigurationId()
    {
        return configurationId;
    }

    public void setConfigurationId(String configurationId)
    {
        this.configurationId = configurationId;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<setup schemaId='").append(getSchemaId()).append("'");

        EXISetupConfiguration def = new EXISetupConfiguration();
        // as attributes
        if (!getCodingMode().equals(def.getCodingMode())) {
            if (getCodingMode().equals(COMPRESSION)) {
                sb.append(" compression='true'");
            } else {
                sb.append(" alignment='").append(getAlignmentString()).append("'");
            }
        }
        if (!getFidelityOptions().equals(def.getFidelityOptions())) {
            FidelityOptions fo = getFidelityOptions();
            if (fo.isStrict()) {
                sb.append(" strict='true'");
            } else {
                if (fo.isFidelityEnabled(FEATURE_COMMENT)) {
                    sb.append(" ").append(SetupValues.getFidelityOptionString(FEATURE_COMMENT)).append("='true'");
                }
                if (fo.isFidelityEnabled(FEATURE_DTD)) {
                    sb.append(" ").append(SetupValues.getFidelityOptionString(FEATURE_DTD)).append("='true'");
                }
                if (fo.isFidelityEnabled(FEATURE_LEXICAL_VALUE)) {
                    sb.append(" ").append(SetupValues.getFidelityOptionString(FEATURE_LEXICAL_VALUE)).append("='true'");
                }
                if (fo.isFidelityEnabled(FEATURE_PI)) {
                    sb.append(" ").append(SetupValues.getFidelityOptionString(FEATURE_PI)).append("='true'");
                }
                if (fo.isFidelityEnabled(FEATURE_PREFIX)) {
                    sb.append(" ").append(SetupValues.getFidelityOptionString(FEATURE_PREFIX)).append("='true'");
                }
                if (fo.isFidelityEnabled(FEATURE_SC) &&
                    !(getCodingMode().equals(PRE_COMPRESSION) || getCodingMode().equals(COMPRESSION))) {
                    sb.append(" ").append(SetupValues.getFidelityOptionString(FEATURE_SC)).append("='true'");
                }
            }
        }
        if (getBlockSize() != def.getBlockSize()) {
            sb.append(" blockSize='").append(getBlockSize()).append("'");
        }
        if (getValueMaxLength() != def.getValueMaxLength()) {
            sb.append(" valueMaxLength='").append(getValueMaxLength()).append("'");
        }
        if (getValuePartitionCapacity() != def.getValuePartitionCapacity()) {
            sb.append(" valuePartitionCapacity='").append(getValuePartitionCapacity()).append("'");
        }
        if (isSessionWideBuffers()) {
            sb.append(" sessionWideBuffers='true'");
        }
        sb.append(">");

        sb.append("</setup>");
        return sb.toString();
    }

    /**
     * Saves this EXI configuration to a file, unless the same configuration has been saved already
     *
     * @return true if this configuration is saved, false otherwise
     */
    public boolean saveConfiguration() throws IOException
    {
        String content = this.toString();

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String configId = EXIUtils.bytesToHex(md.digest(content.getBytes()));
            setConfigurationId(configId);
        } catch (NoSuchAlgorithmException e) {
            Log.warn("Exception while trying to save configuration.", e);
        }

        Path fileName = EXIUtils.getExiFolder().resolve(configurationId + ".xml");
        if (Files.exists(fileName)) {
            return true;
        } else {
            if (EXIUtils.writeFile(fileName, content)) {
                return true;
            } else {
                Log.warn("Error while trying to save the file. Configurations were not saved.");
                return false;
            }
        }
    }

    /**
     * Looks a saved EXI configuration with the given configuration ID
     *
     * @param configId the configuration ID to look for
     * @return an EXISetupConfiguration instance if the configuration exists. <i>null</i> otherwise
     */
    public static EXISetupConfiguration parseQuickConfigId(String configId) throws DocumentException
    {
        Path fileLocation = EXIUtils.getExiFolder().resolve(configId + ".xml");
        String content = EXIUtils.readFile(fileLocation);
        if (content == null)
            return null;

        Element configElement = DocumentHelper.parseText(content).getRootElement();

        EXISetupConfiguration exiConfig = new EXISetupConfiguration();
        exiConfig.setConfigurationId(configId);
        // iterate through attributes of root
        for (Iterator<Attribute> i = configElement.attributeIterator(); i.hasNext(); ) {
            Attribute att = i.next();
            if (att.getName().equals("schemaId")) {
                exiConfig.setSchemaId(att.getValue());
                if (!Files.exists(exiConfig.getCanonicalSchemaLocation())) {
                    return null;
                }
            } else if (att.getName().equals("alignment")) {
                exiConfig.setCodingMode(SetupValues.getCodingMode(att.getValue()));
            } else if (att.getName().equals("compression")) {
                if (Boolean.parseBoolean(att.getValue())) {
                    exiConfig.setCodingMode(COMPRESSION);
                }
            } else if (att.getName().equals("blockSize")) {
                exiConfig.setBlockSize(Integer.parseInt(att.getValue()));
            } else if (att.getName().equals("valueMaxLength")) {
                exiConfig.setValueMaxLength(Integer.parseInt(att.getValue()));
            } else if (att.getName().equals("valuePartitionCapacity")) {
                exiConfig.setValuePartitionCapacity(Integer.parseInt(att.getValue()));
            } else if (att.getName().equals("sessionWideBuffers")) {
                exiConfig.setSessionWideBuffers(true);
            } else if (att.getName().equals("strict")) {
                if (Boolean.parseBoolean(att.getValue())) {
                    exiConfig.setFidelityOptions(FidelityOptions.createStrict());
                }
            } else
                try {
                    if (att.getName().equals(SetupValues.getFidelityOptionString(FEATURE_COMMENT)) && Boolean.parseBoolean(att.getValue())) {
                        exiConfig.getFidelityOptions().setFidelity(FEATURE_COMMENT, true);
                    } else if (att.getName().equals(SetupValues.getFidelityOptionString(FEATURE_DTD)) && Boolean.parseBoolean(att.getValue())) {
                        exiConfig.getFidelityOptions().setFidelity(FEATURE_DTD, true);
                    } else if (att.getName().equals(SetupValues.getFidelityOptionString(FEATURE_LEXICAL_VALUE)) && Boolean.parseBoolean(att.getValue())) {
                        exiConfig.getFidelityOptions().setFidelity(FEATURE_LEXICAL_VALUE, true);
                    } else if (att.getName().equals(SetupValues.getFidelityOptionString(FEATURE_PI)) && Boolean.parseBoolean(att.getValue())) {
                        exiConfig.getFidelityOptions().setFidelity(FEATURE_PI, true);
                    } else if (att.getName().equals(SetupValues.getFidelityOptionString(FEATURE_PREFIX)) && Boolean.parseBoolean(att.getValue())) {
                        exiConfig.getFidelityOptions().setFidelity(FEATURE_PREFIX, true);
                    } else if (att.getName().equals(SetupValues.getFidelityOptionString(FEATURE_SC)) && Boolean.parseBoolean(att.getValue())) {
                        exiConfig.getFidelityOptions().setFidelity(FEATURE_SC, true);
                    }
                } catch (UnsupportedOption e) {
                    Log.warn("Exception while trying to parse quick config ID '{}'", configId, e);
                }
        }
        return exiConfig;
    }
}
