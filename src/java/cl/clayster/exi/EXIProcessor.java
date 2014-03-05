package cl.clayster.exi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.mina.common.ByteBuffer;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.jivesoftware.util.JiveGlobals;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import com.siemens.ct.exi.CodingMode;
import com.siemens.ct.exi.Constants;
import com.siemens.ct.exi.EXIFactory;
import com.siemens.ct.exi.EncodingOptions;
import com.siemens.ct.exi.FidelityOptions;
import com.siemens.ct.exi.GrammarFactory;
import com.siemens.ct.exi.api.sax.EXIResult;
import com.siemens.ct.exi.api.sax.EXISource;
import com.siemens.ct.exi.api.sax.SAXDecoder;
import com.siemens.ct.exi.exceptions.EXIException;
import com.siemens.ct.exi.grammars.Grammars;
import com.siemens.ct.exi.helpers.DefaultEXIFactory;

public class EXIProcessor {
	
	EXIFactory exiFactory;
	EXIResult exiResult;
	SAXSource exiSource;
	
	static final int defaultAlignmentCode = 0;	// 0:bit-packed, 1:byte-packed, 2:pre-compression, 3:compression
	static final CodingMode defaultCodingMode = CodingMode.BIT_PACKED;
	static final FidelityOptions defaultFidelityOptions = FidelityOptions.createDefault();
	static final boolean defaultIsFragmet = false;
	static final int defaultBlockSize = 1000000;
	static final boolean defaultStrict = false;
	static final int defaultValueMaxLength = -1;
	static final int defaultValuePartitionCapacity = -1;
	
	
	/**
	 * Constructs an EXI Processor using <b>xsdLocation</b> as the Canonical Schema and <b>default values</b> for its configuration.
	 * @param xsdLocation
	 * @throws EXIException
	 */
	public EXIProcessor(String xsdLocation) throws EXIException{
		// create default factory and EXI grammar for schema
		exiFactory = DefaultEXIFactory.newInstance();
		defaultFidelityOptions.setFidelity(FidelityOptions.FEATURE_PREFIX, true);
		exiFactory.setFidelityOptions(defaultStrict ? FidelityOptions.createStrict() : defaultFidelityOptions);
		exiFactory.setCodingMode(CodingMode.BIT_PACKED);
		exiFactory.setBlockSize(defaultBlockSize);
		exiFactory.setValueMaxLength(defaultValueMaxLength);
		exiFactory.setValuePartitionCapacity(defaultValuePartitionCapacity);
		
		if(xsdLocation != null && new File(xsdLocation).isFile()){
			try{
				GrammarFactory grammarFactory = GrammarFactory.newInstance();
				Grammars g = grammarFactory.createGrammars(xsdLocation, (XMLEntityResolver)new SchemaResolver(JiveGlobals.getHomeDirectory() + EXIUtils.schemasFolder));
				exiFactory.setGrammars(g);
			} catch (IOException e){
				e.printStackTrace();
				throw new EXIException("Error while creating Grammars.");
			}
		}
		else{
			String message = "Invalid Canonical Schema file location: " + xsdLocation;
System.err.println(message);
			throw new EXIException(message);
		}
	}
	
	/**
	 * Constructs an EXI Processor using <b>xsdLocation</b> as the Canonical Schema and the respective parameters in exiConfig for its configuration.
	 * @param xsdLocation	location of the Canonical schema file
	 * @param exiConfig	EXISetupConfiguration instance with the necessary EXI options
	 * @throws EXIException
	 */
	public EXIProcessor(String xsdLocation, EXISetupConfiguration exiConfig) throws EXIException{
		if(exiConfig == null)	exiConfig = new EXISetupConfiguration();
		// create factory and EXI grammar for given schema
		exiFactory = DefaultEXIFactory.newInstance();
		exiFactory.setCodingMode(exiConfig.getAlignment());
		exiFactory.setBlockSize(exiConfig.getBlockSize());
		exiFactory.setFidelityOptions(exiConfig.getFo());
		exiFactory.setValueMaxLength(exiConfig.getValueMaxLength());
		exiFactory.setValuePartitionCapacity(exiConfig.getValuePartitionCapacity());
		
		if(xsdLocation != null && new File(xsdLocation).isFile()){
			try{
				GrammarFactory grammarFactory = GrammarFactory.newInstance();
				Grammars g = grammarFactory.createGrammars(xsdLocation, (XMLEntityResolver)new SchemaResolver(JiveGlobals.getHomeDirectory() + EXIUtils.schemasFolder));
				exiFactory.setGrammars(g);
			} catch (IOException e){
				e.printStackTrace();
				throw new EXIException("Error while creating Grammars.");
			}
		}
		else{
			String message = "Invalid Canonical Schema file location: " + xsdLocation;
System.err.println(message);
			throw new EXIException(message);
		}
	}
	
	
	/**
	 * Encodes an XML String into an EXI Body byte array using no schema files and default {@link EncodingOptions} and {@link FidelityOptions}.
	 * 
	 * @param xml the String to be encoded
	 * @return a byte array containing the EXI Body
	 * @throws IOException
	 * @throws EXIException
	 * @throws SAXException
	 */
	public static byte[] encodeEXIBody(String xml) throws EXIException, IOException, SAXException{
		byte[] exi = encodeSchemaless(xml, false);
		// With default options, the header is only 1 byte long: Distinguishing bits (10), Presence of EXI Options (0) and EXI Version (00000)
		System.arraycopy(exi, 1, exi, 0, exi.length - 1);
		return exi;
	}
	
	/**
	 * Decodes an EXI body byte array using no schema files.
	 * 
	 * @param exi the EXI stanza to be decoded
	 * @return a String containing the decoded XML
	 * @throws IOException
	 * @throws EXIException
	 * @throws UnsupportedEncodingException 
	 * @throws SAXException
	 */
	public static String decodeExiBodySchemaless(byte[] exi) throws TransformerException, EXIException, UnsupportedEncodingException{
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		
		EXIFactory factory = DefaultEXIFactory.newInstance();
		defaultFidelityOptions.setFidelity(FidelityOptions.FEATURE_PREFIX, true);
		factory.setFidelityOptions(defaultFidelityOptions);
		factory.setCodingMode(defaultCodingMode);
		factory.setFragment(defaultIsFragmet);
		factory.setBlockSize(defaultBlockSize);
		
		SAXSource exiSource = new SAXSource(new InputSource(new ByteArrayInputStream(exi)));
		SAXDecoder saxDecoder = (SAXDecoder) factory.createEXIReader();
		try {
			saxDecoder.setFeature(Constants.W3C_EXI_FEATURE_BODY_ONLY, Boolean.TRUE);
		} catch (SAXNotRecognizedException e) {
			e.printStackTrace();
		} catch (SAXNotSupportedException e) {
			e.printStackTrace();
		}
		exiSource.setXMLReader(saxDecoder);

		ByteArrayOutputStream xmlDecoded = new ByteArrayOutputStream();
		transformer.transform(exiSource, new StreamResult(xmlDecoded));

		return xmlDecoded.toString("UTF-8");
	}
	
	/**
	 * Encodes an XML String into an EXI byte array using no schema files and default {@link EncodingOptions} and {@link FidelityOptions}.
	 * 
	 * @param xml the String to be encoded
	 * @return a byte array containing the encoded bytes
	 * @param cookie if the encoding should include EXI Cookie or not
	 * @throws IOException
	 * @throws EXIException
	 * @throws SAXException
	 */
	public static byte[] encodeSchemaless(String xml, boolean cookie) throws IOException, EXIException, SAXException{
		ByteArrayOutputStream osEXI = new ByteArrayOutputStream();
		// start encoding process
		EXIFactory factory = DefaultEXIFactory.newInstance();
		defaultFidelityOptions.setFidelity(FidelityOptions.FEATURE_PREFIX, true);
		factory.setFidelityOptions(defaultFidelityOptions);
		factory.setCodingMode(defaultCodingMode);
		factory.setFragment(defaultIsFragmet);
		factory.setBlockSize(defaultBlockSize);
		if(cookie)	factory.getEncodingOptions().setOption(EncodingOptions.INCLUDE_COOKIE);
		
		XMLReader xmlReader = XMLReaderFactory.createXMLReader();
		EXIResult exiResult = new EXIResult(factory);
		
		exiResult.setOutputStream(osEXI);
		xmlReader.setContentHandler(exiResult.getHandler());
		xmlReader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", Boolean.FALSE);	// ignorar DTD externos
		
		xmlReader.parse(new InputSource(new StringReader(xml)));
		
		return osEXI.toByteArray();
	}
	
	/**
	 * Encodes an XML String into an EXI byte array using no schema files.
	 * 
	 * @param xml the String to be encoded
	 * @param eo Encoding Options (if null, default will be used)
	 * @param fo Fidelity Options (if null, default will be used)
	 * @return a byte array containing the encoded bytes
	 * @throws IOException
	 * @throws EXIException
	 * @throws SAXException
	 */
	public static byte[] encodeSchemaless(String xml, EncodingOptions eo, FidelityOptions fo) throws IOException, EXIException, SAXException{
		ByteArrayOutputStream osEXI = new ByteArrayOutputStream();
		// start encoding process
		EXIFactory factory = DefaultEXIFactory.newInstance();
		// EXI configurations setup
		if(eo != null){
			factory.setEncodingOptions(eo);
		}
		if(fo != null){
			defaultFidelityOptions.setFidelity(FidelityOptions.FEATURE_PREFIX, true);
			factory.setFidelityOptions(defaultFidelityOptions);
		}
		factory.setCodingMode(defaultCodingMode);
		factory.setFragment(defaultIsFragmet);
		factory.setBlockSize(defaultBlockSize);
		
		XMLReader xmlReader = XMLReaderFactory.createXMLReader();
		EXIResult exiResult = new EXIResult(factory);
		
		exiResult.setOutputStream(osEXI);
		xmlReader.setContentHandler(exiResult.getHandler());
		xmlReader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", Boolean.FALSE);	// ignorar DTD externos
		
		xmlReader.parse(new InputSource(new StringReader(xml)));
		
		return osEXI.toByteArray();
	}
	
	/**
	 * Decodes an EXI byte array using no schema files.
	 * 
	 * @param exi the EXI stanza to be decoded
	 * @return a String containing the decoded XML
	 * @throws IOException
	 * @throws EXIException
	 * @throws UnsupportedEncodingException 
	 * @throws SAXException
	 */
	public static String decodeSchemaless(byte[] exi) throws TransformerException, EXIException, UnsupportedEncodingException{
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		
		EXIFactory factory = DefaultEXIFactory.newInstance();
		defaultFidelityOptions.setFidelity(FidelityOptions.FEATURE_PREFIX, true);
		factory.setFidelityOptions(defaultFidelityOptions);
		factory.setCodingMode(defaultCodingMode);
		factory.setFragment(defaultIsFragmet);
		factory.setBlockSize(defaultBlockSize);
		if(!isEXI(exi[0]))	factory.getEncodingOptions().setOption(EncodingOptions.INCLUDE_COOKIE);
		
		SAXSource exiSource = new SAXSource(new InputSource(new ByteArrayInputStream(exi)));
		exiSource.setXMLReader(factory.createEXIReader());

		ByteArrayOutputStream xmlDecoded = new ByteArrayOutputStream();
		transformer.transform(exiSource, new StreamResult(xmlDecoded));

		return xmlDecoded.toString("UTF-8");
	}
	
	/**
	 * Uses distinguishing bits (10) to recognize EXI stanzas.
	 * 
	 * @param b the first byte of the EXI stanza to be evaluated
	 * @return <b>true</b> if the byte starts with distinguishing bits, <b>false</b> otherwise
	 */
	public static boolean isEXI(byte b){
		byte distinguishingBits = -128;
		byte aux = (byte) (b & distinguishingBits);
		return aux == distinguishingBits;
	}
	
	public static boolean hasEXICookie(byte[] bba){
		byte[] ba = new byte[4];
        System.arraycopy(bba, 0, ba, 0, 4);
		return "$EXI".equals(new String(ba));
	}
	
	public ByteBuffer encodeByteBuffer(String xml) throws IOException, EXIException, SAXException, TransformerException{
		// encoding
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		exiResult = new EXIResult(exiFactory);		
		exiResult.setOutputStream(baos);
		
		XMLReader xmlReader = XMLReaderFactory.createXMLReader();
		xmlReader.setContentHandler(exiResult.getHandler());
		xmlReader.parse(new InputSource(new StringReader(xml)));
		return ByteBuffer.wrap(baos.toByteArray());
	}
	
	
	/**
     * <p>Decodes a String from EXI to XML</p>
     *
     * @param in <code>InputStream</code> to read from.
     * @return a character array containing the XML characters
     * @throws EXIException if it is a not well formed EXI document
     */
	protected String decodeByteArray(byte[] exiBytes) throws IOException, EXIException, TransformerException{
		// decoding		
		exiSource = new EXISource(exiFactory);
		XMLReader exiReader = exiSource.getXMLReader();
	
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();		
		
		InputStream exiIS = new ByteArrayInputStream(exiBytes);
		exiSource = new SAXSource(new InputSource(exiIS));
		exiSource.setXMLReader(exiReader);
	
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		transformer.transform(exiSource, new StreamResult(baos));		
		return baos.toString("UTF-8");
	}
}
