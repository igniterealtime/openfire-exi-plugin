package cl.clayster.exi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.mina.common.ByteBuffer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

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
	Transformer transformer;
	
	/**
	 * Constructs an EXI Processor using <b>xsdLocation</b> as the Canonical Schema and the respective parameters in exiConfig for its configuration.
	 * @param xsdLocation	location of the Canonical schema file
	 * @param exiConfig	EXISetupConfiguration instance with the necessary EXI options. Default options are used when null
	 * @throws EXIException
	 */
	public EXIProcessor(EXISetupConfiguration exiConfig) throws EXIException{
		if(exiConfig == null)	exiConfig = new EXISetupConfiguration();
		// create factory and EXI grammar for given schema
		exiFactory = exiConfig;
		
		try{
			GrammarFactory grammarFactory = GrammarFactory.newInstance();
			Grammars g = grammarFactory.createGrammars(exiConfig.getCanonicalSchemaLocation(), new SchemaResolver());
			exiFactory.setGrammars(g);
			TransformerFactory tf = TransformerFactory.newInstance();
			transformer = tf.newTransformer();
		    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		} catch (IOException e){
			e.printStackTrace();
			throw new EXIException("Error while creating Grammars.");
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
		}
	}
	
	
	public EXIProcessor(EXIFactory ef) {
		exiFactory = ef;
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
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		
		EXIFactory factory = new EXISetupConfiguration();
		factory.getFidelityOptions().setFidelity(FidelityOptions.FEATURE_PREFIX, true);
		
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

		return xmlDecoded.toString();
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
		EXIFactory factory = new EXISetupConfiguration();
		factory.getFidelityOptions().setFidelity(FidelityOptions.FEATURE_PREFIX, true);
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
		EXIFactory factory = new EXISetupConfiguration();
		// EXI configurations setup
		if(eo != null){
			factory.setEncodingOptions(eo);
		}
		if(fo != null){
			factory.setFidelityOptions(fo);
			factory.getFidelityOptions().setFidelity(FidelityOptions.FEATURE_PREFIX, true);
		}
		
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
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		
		EXIFactory factory = DefaultEXIFactory.newInstance();
		if(!isEXI(exi[0]))	factory.getEncodingOptions().setOption(EncodingOptions.INCLUDE_COOKIE);
		
		SAXSource exiSource = new SAXSource(new InputSource(new ByteArrayInputStream(exi)));
		exiSource.setXMLReader(factory.createEXIReader());

		ByteArrayOutputStream xmlDecoded = new ByteArrayOutputStream();
		transformer.transform(exiSource, new StreamResult(xmlDecoded));

		return xmlDecoded.toString();
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
        System.arraycopy(bba, 0, ba, 0, bba.length >= 4 ? 4 : bba.length);
		return "$EXI".equals(new String(ba));
	}
	
	public ByteBuffer encodeByteBuffer(String xml, boolean cookie) throws IOException, EXIException, SAXException, TransformerException{
		// encoding
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		exiResult = new EXIResult(exiFactory);		
		exiResult.setOutputStream(baos);
		
		XMLReader xmlReader = XMLReaderFactory.createXMLReader();
		xmlReader.setContentHandler(exiResult.getHandler());
		xmlReader.parse(new InputSource(new StringReader(xml)));
		
		byte[] exi = baos.toByteArray();
		if(cookie){
			byte[] c = "$EXI".getBytes();
			byte[] aux = new byte[exi.length + c.length]; 
			System.arraycopy(exi, 0, aux, c.length, exi.length);
			System.arraycopy(c, 0, aux, 0, c.length);
			exi = aux;
		}
		return ByteBuffer.wrap(exi);
	}
	
	public ByteBuffer encodeByteBuffer(String xml) throws IOException, EXIException, SAXException, TransformerException{
		return encodeByteBuffer(xml, false);
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
		
		InputStream exiIS = new ByteArrayInputStream(exiBytes);
		exiSource = new SAXSource(new InputSource(exiIS));
		exiSource.setXMLReader(exiReader);
	
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		transformer.transform(exiSource, new StreamResult(baos));		
		
		return baos.toString();
	}
	
	/**
     * <p>Decodes a String from EXI to XML</p>
     *
     * @param in <code>InputStream</code> to read from.
     * @return a character array containing the XML characters
     * @throws EXIException if it is a not well formed EXI document
     */
	protected String decodeByteArray(ByteArrayInputStream exiStream) throws IOException, EXIException, TransformerException{
		// decoding		
		exiSource = new EXISource(exiFactory);
		XMLReader exiReader = exiSource.getXMLReader();
		
		exiSource = new SAXSource(new InputSource(exiStream));
		exiSource.setXMLReader(exiReader);
	
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		transformer.transform(exiSource, new StreamResult(baos));		
		
		return baos.toString();
	}
}
