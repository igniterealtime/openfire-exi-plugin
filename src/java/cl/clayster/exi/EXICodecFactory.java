package cl.clayster.exi;

import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;

public class EXICodecFactory implements ProtocolCodecFactory {
	
	EXIEncoder encoder;
	EXIDecoder decoder;
	
	public EXICodecFactory() {
		this.encoder = new EXIEncoder();
		this.decoder = new EXIDecoder();
	}

	@Override
	public ProtocolEncoder getEncoder() throws Exception {
System.err.println("EXICoderFactory.getEncoder() called");
		return encoder;
	}
	
	@Override
	public ProtocolDecoder getDecoder() throws Exception {
System.err.println("EXICoderFactory.getDecoder() called");
		return decoder;
	}
}
