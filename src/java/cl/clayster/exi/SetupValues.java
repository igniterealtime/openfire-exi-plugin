package cl.clayster.exi;

import com.siemens.ct.exi.CodingMode;
import com.siemens.ct.exi.FidelityOptions;

public class SetupValues {

	final static String ALIGNMENT = "alignment";
	final static String COMPRESSION = "compression";
	final static String STRICT = "strict";
	final static String PRESERVE_COMMENTS = "preserveComments";
	final static String PRESERVE_PIS = "preservePIs";
	final static String PRESERVE_DTD = "preserveDTD";
	final static String PRESERVE_PREFIXES = "preservePrefixes";
	final static String PRESERVE_LEXICAL = "preserveLexical";
	final static String SELF_CONTAINED = "selfContained";
	final static String BLOCK_SIZE = "blockSize";
	final static String VALUE_MAX_LENGTH = "valueMaxLength";
	final static String VALUE_PARTITION_CAPACITY = "valuePartitionCapacity";
	final static String WIDE_BUFFERS = "sessionWideBuffers";
	
	static CodingMode getCodingMode(String alignment){
		CodingMode cm = CodingMode.BIT_PACKED;
		if(alignment.equals("byte-packed")){
			cm = CodingMode.BYTE_PACKED;
		}
		else if(alignment.equals("pre-compression")){
			cm = CodingMode.PRE_COMPRESSION;
		}
		else if(alignment.equals("compression")){
			cm = CodingMode.COMPRESSION;
		}
		return cm;
	}
	
	static String getFidelityOptionString(String fidelity){
		String preserve = "";
		if(fidelity.equals(FidelityOptions.FEATURE_COMMENT)){
			preserve = "preserveComments";
		}
		else if(fidelity.equals(FidelityOptions.FEATURE_DTD)){
			preserve = "preserveDTD";
		}
		else if(fidelity.equals(FidelityOptions.FEATURE_LEXICAL_VALUE)){
			preserve = "preserveLexical";
		}
		else if(fidelity.equals(FidelityOptions.FEATURE_PI)){
			preserve = "preservePIs";
		}
		else if(fidelity.equals(FidelityOptions.FEATURE_PREFIX)){
			preserve = "preservePrefixes";
		}
		else if(fidelity.equals(FidelityOptions.FEATURE_SC)){
			preserve = "selfContained";
		}
		else if(fidelity.equals(FidelityOptions.FEATURE_STRICT)){
			preserve = "strict";
		}
		return preserve;
	}
	
}
