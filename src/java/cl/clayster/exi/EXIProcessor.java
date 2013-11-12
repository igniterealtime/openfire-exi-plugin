package cl.clayster.exi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.mina.common.ByteBuffer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import com.siemens.ct.exi.CodingMode;
import com.siemens.ct.exi.EXIFactory;
import com.siemens.ct.exi.FidelityOptions;
import com.siemens.ct.exi.GrammarFactory;
import com.siemens.ct.exi.api.sax.EXIResult;
import com.siemens.ct.exi.api.sax.EXISource;
import com.siemens.ct.exi.exceptions.EXIException;
import com.siemens.ct.exi.grammars.Grammars;
import com.siemens.ct.exi.helpers.DefaultEXIFactory;

public class EXIProcessor {
	
	public static final String CHARSET = "ISO-8859-1";
	
	static EXIFactory exiFactory;
	static EXIResult exiResult;
	static SAXSource exiSource;
	
	public EXIProcessor(String xsdLocation) throws EXIException{
		// TODO: eliminar la siguiente linea
		xsdLocation = EXIUtils.schemasFolder + "canonicalSchema.xsd";
		
		// create default factory and EXI grammar for schema
		exiFactory = DefaultEXIFactory.newInstance();
		exiFactory.setFidelityOptions(FidelityOptions.createAll());
		exiFactory.setCodingMode(CodingMode.BIT_PACKED);
		GrammarFactory grammarFactory = GrammarFactory.newInstance();
		Grammars g = grammarFactory.createGrammars(xsdLocation);
		exiFactory.setGrammars(g);
	}
	
	public static String decodeSchemaless(String exi) throws IOException, EXIException, SAXException, TransformerException{		
		// create default factory and EXI grammar for schema
		EXIFactory exiFactory = DefaultEXIFactory.newInstance();
		exiFactory.setFidelityOptions(FidelityOptions.createAll());
		
		// decoding		
		SAXSource exiSource = new EXISource(exiFactory);
		XMLReader exiReader = exiSource.getXMLReader();
	
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();

		byte[] exiBytes = exi.getBytes(EXIProcessor.CHARSET);		
		
		InputStream exiIS = new ByteArrayInputStream(exiBytes);
		exiSource = new SAXSource(new InputSource(exiIS));
		exiSource.setXMLReader(exiReader);
	
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		transformer.transform(exiSource, new StreamResult(baos));		
		return baos.toString();
	}
	
	public ByteBuffer encodeByteBuffer(String xml) throws IOException, EXIException, SAXException, TransformerException{
		// encoding
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		EXIResult exiResult = new EXIResult(exiFactory);		
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
	public String decodeBytes(byte[] exiBytes) throws SAXException, TransformerException, EXIException, IOException{
		// decoding		
		SAXSource exiSource = new EXISource(exiFactory);
		XMLReader exiReader = exiSource.getXMLReader();
	
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();		
		
		InputStream exiIS = new ByteArrayInputStream(exiBytes);
		exiSource = new SAXSource(new InputSource(exiIS));
		exiSource.setXMLReader(exiReader);
	
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		transformer.transform(exiSource, new StreamResult(baos));		
		
		return baos.toString();
	}

	
	/**
     * <p>Uses Distinguishing bits to know if it is an EXI message or an XML message.</p>
     *
     * @param b first byte of the EXI Header containing the two first.
     * @return <code>true</code> if the first two bits are 1 and 0, respectively. <code>false</code> otherwise. 
     */
	
	public static boolean isEXI(byte b){
		byte distinguishingBits = -128;
		byte aux = (byte) (b & distinguishingBits);
		return aux == distinguishingBits;
	}
}
