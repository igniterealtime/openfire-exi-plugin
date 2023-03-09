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

import com.siemens.ct.exi.core.Constants;
import com.siemens.ct.exi.core.EXIFactory;
import com.siemens.ct.exi.core.EncodingOptions;
import com.siemens.ct.exi.core.FidelityOptions;
import com.siemens.ct.exi.core.exceptions.EXIException;
import com.siemens.ct.exi.core.grammars.Grammars;
import com.siemens.ct.exi.core.helpers.DefaultEXIFactory;
import com.siemens.ct.exi.grammars.GrammarFactory;
import com.siemens.ct.exi.main.api.sax.EXIResult;
import com.siemens.ct.exi.main.api.sax.EXISource;
import com.siemens.ct.exi.main.api.sax.SAXDecoder;
import org.apache.mina.core.buffer.IoBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;

public class EXIProcessor {

    private static final Logger Log = LoggerFactory.getLogger(EXIProcessor.class);

	protected EXIFactory exiFactory;
	protected EXIResult exiResult;
	protected SAXSource exiSource;
	protected Transformer transformer;
	protected XMLReader exiReader, xmlReader;
	
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
			
			exiResult = new EXIResult(exiFactory);
	        xmlReader = XMLReaderFactory.createXMLReader();
	        xmlReader.setContentHandler(exiResult.getHandler());
			
			exiSource = new EXISource(exiFactory);
	        exiReader = exiSource.getXMLReader();
		} catch (IOException | ParserConfigurationException e){
			throw new EXIException("Error while creating Grammars.", e);
		} catch (TransformerConfigurationException e) {
			throw new EXIException("Error while creating Transformer.", e);
		} catch (SAXException e) {
			throw new EXIException("Error while creating XML reader.", e);
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
		SAXDecoder saxDecoder = new SAXDecoder(factory);
		try {
			saxDecoder.setFeature(Constants.W3C_EXI_FEATURE_BODY_ONLY, Boolean.TRUE);
		} catch (SAXNotRecognizedException | SAXNotSupportedException e) {
            Log.warn("Exception while trying to decode EXI body using no schema files.", e);
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
		exiSource.setXMLReader(new SAXDecoder(factory));

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
        System.arraycopy(bba, 0, ba, 0, bba.length >= 4 ? 4 : bba.length);
		return "$EXI".equals(new String(ba));
	}
	
	public IoBuffer encodeByteBuffer(String xml, boolean cookie) throws IOException, EXIException, SAXException, TransformerException{
		// encoding
		ByteArrayOutputStream baos = new ByteArrayOutputStream();		
		exiResult.setOutputStream(baos);
		
		xmlReader.parse(new InputSource(new StringReader(xml)));
		
		byte[] exi = baos.toByteArray();
		if(cookie){
			byte[] c = "$EXI".getBytes();
			byte[] aux = new byte[exi.length + c.length]; 
			System.arraycopy(exi, 0, aux, c.length, exi.length);
			System.arraycopy(c, 0, aux, 0, c.length);
			exi = aux;
		}
		return IoBuffer.wrap(exi);
	}
	
	public IoBuffer encodeByteBuffer(String xml) throws IOException, EXIException, SAXException, TransformerException{
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
		InputStream exiIS = new ByteArrayInputStream(exiBytes);
		exiSource = new SAXSource(new InputSource(exiIS));
		exiSource.setXMLReader(exiReader);
	
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		transformer.transform(exiSource, new StreamResult(baos));		
		
		return baos.toString("UTF-8");
	}
	
	/**
     * <p>Decodes a String from EXI to XML</p>
     *
     * @param in <code>InputStream</code> to read from.
     * @return a character array containing the XML characters
     * @throws EXIException if it is a not well formed EXI document
     */
	protected String decode(InputStream exiIS) throws IOException, EXIException, TransformerException{
		exiSource = new SAXSource(new InputSource(exiIS));
		exiSource.setXMLReader(exiReader);
	
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		transformer.transform(exiSource, new StreamResult(baos));		
		
		return baos.toString("UTF-8");
	}
	
	/**
     * <p>Decodes a String from EXI to XML</p>
     *
     * @param in <code>InputStream</code> to read from.
     * @return a character array containing the XML characters
     * @throws EXIException if it is a not well formed EXI document
     */
	protected String decodeByteArray(ByteArrayInputStream exiStream) throws IOException, EXIException, TransformerException{
		exiSource = new SAXSource(new InputSource(exiStream));
		exiSource.setXMLReader(exiReader);
	
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		transformer.transform(exiSource, new StreamResult(baos));		
		
		return baos.toString("UTF-8");
	}
}
