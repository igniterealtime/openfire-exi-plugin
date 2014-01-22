package cl.clayster.exi;

import com.siemens.ct.exi.CodingMode;
import com.siemens.ct.exi.FidelityOptions;
import com.siemens.ct.exi.exceptions.UnsupportedOption;

/**
 * Contains all relevant values to setup an EXI compression, in order to propose them to the server.
 * @author Javier Placencio
 *
 */
public class EXISetupConfiguration {
	
	private String id;

	private CodingMode alignment;
	private boolean strict;
	private boolean fragment;
	private FidelityOptions fo;
	
	private int blockSize;
	private int valueMaxLength;
	private int valuePartitionCapacity;
	
	/**
	 * Constructs a new EXISetupConfigurations and initializates it with Default Values.
	 */
	public EXISetupConfiguration(){
		setDefaultValues();
	}
	
	public void setDefaultValues(){
		setAlignment(CodingMode.BIT_PACKED);
		setStrict(false);
		setFragment(false);
		setFo(FidelityOptions.createDefault());
		setBlockSize(1000000);
		setValueMaxLength(-1);
		setValuePartitionCapacity(-1);
	}
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
	
	public int getAlignmentCode() {
		int alignment = (this.alignment.equals(CodingMode.BIT_PACKED)) ? 0 :
        	(this.alignment.equals(CodingMode.BYTE_PACKED)) ? 1 :
        		(this.alignment.equals(CodingMode.PRE_COMPRESSION)) ? 2 :
        			(this.alignment.equals(CodingMode.COMPRESSION)) ? 3: 0;
		return alignment;
	}
	
	public String getAlignmentString() {
		String alignment = (this.alignment.equals(CodingMode.BIT_PACKED)) ? "bit-packed" :
        	(this.alignment.equals(CodingMode.BYTE_PACKED)) ? "byte-packed" :
        		(this.alignment.equals(CodingMode.PRE_COMPRESSION)) ? "pre-compression" :
        			(this.alignment.equals(CodingMode.COMPRESSION)) ? "compression": "bit-packed";
		return alignment;
	}
	
	public CodingMode getAlignment() {
		return alignment;
	}
	public void setAlignment(CodingMode alignment) {
		this.alignment = alignment;
	}
	public boolean isStrict() {
		return strict;
	}
	public void setStrict(boolean strict) {
		this.strict = strict;
	}
	public boolean isFragment() {
		return fragment;
	}
	public void setFragment(boolean fragment) {
		this.fragment = fragment;
	}
	public FidelityOptions getFo() {
		return fo;
	}
	public void setFo(FidelityOptions fo) {
		try {
			fo.setFidelity(FidelityOptions.FEATURE_PREFIX, true);
		} catch (UnsupportedOption e) {
			e.printStackTrace();
		}
		this.fo = fo;
	}
	public int getBlockSize() {
		return blockSize;
	}
	public void setBlockSize(int blockSize) {
		this.blockSize = blockSize;
	}
	public int getValueMaxLength() {
		return valueMaxLength;
	}
	public void setValueMaxLength(int valueMaxLength) {
		this.valueMaxLength = valueMaxLength;
	}
	public int getValuePartitionCapacity() {
		return valuePartitionCapacity;
	}
	public void setValuePartitionCapacity(int valuePartitionCapacity) {
		this.valuePartitionCapacity = valuePartitionCapacity;
	}
	
	@Override
	public String toString(){
		return "EXISetupConfiguration:"
				+ "\n\t alignment: " + getAlignmentString()
				+ "\n\t fragment: " + fragment 
				+ "\n\t blockSize: " + blockSize
				+ "\n\t valueMaxLength: " + valueMaxLength
				+ "\n\t valuePartitionCapacity: " + valuePartitionCapacity;
				
	}
}
