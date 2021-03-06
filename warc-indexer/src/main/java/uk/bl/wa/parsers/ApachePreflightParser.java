/**
 * 
 */
package uk.bl.wa.parsers;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.activation.FileDataSource;

import org.apache.pdfbox.preflight.PreflightDocument;
import org.apache.pdfbox.preflight.ValidationResult;
import org.apache.pdfbox.preflight.ValidationResult.ValidationError;
import org.apache.pdfbox.preflight.exception.SyntaxValidationException;
import org.apache.pdfbox.preflight.parser.PreflightParser;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import uk.bl.wa.util.InputStreamDataSource;

/**
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class ApachePreflightParser extends AbstractParser {

    /** */
	private static final long serialVersionUID = 710873621129254338L;

	/** */
	private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                  MediaType.application("pdf")
            )));

	public static final Property PDF_PREFLIGHT_VALID = Property.internalBoolean("PDF-A-PREFLIGHT-VALID");

	public static final Property PDF_PREFLIGHT_ERRORS = Property.internalTextBag("PDF-A-PREFLIGHT-ERRORS");

	
	
	/* (non-Javadoc)
	 * @see org.apache.tika.parser.Parser#getSupportedTypes(org.apache.tika.parser.ParseContext)
	 */
	@Override
	public Set<MediaType> getSupportedTypes(ParseContext context) {
		return SUPPORTED_TYPES;
	}

	/* (non-Javadoc)
	 * @see org.apache.tika.parser.Parser#parse(java.io.InputStream, org.xml.sax.ContentHandler, org.apache.tika.metadata.Metadata, org.apache.tika.parser.ParseContext)
	 */
	@Override
	public void parse(InputStream stream, ContentHandler handler,
			Metadata metadata, ParseContext context) throws IOException,
			SAXException, TikaException {
		
		ValidationResult result = null;

		InputStreamDataSource isds = new InputStreamDataSource(stream);
		PreflightParser parser = new PreflightParser(isds);
		try {

		  /* Parse the PDF file with PreflightParser that inherits from the NonSequentialParser.
		   * Some additional controls are present to check a set of PDF/A requirements. 
		   * (Stream length consistency, EOL after some Keyword...)
		   */
		  parser.parse();

		  /* Once the syntax validation is done, 
		   * the parser can provide a PreflightDocument 
		   * (that inherits from PDDocument) 
		   * This document process the end of PDF/A validation.
		   */
		  PreflightDocument document = parser.getPreflightDocument();
		  document.validate();

		  // Get validation result
		  result = document.getResult();
		  document.close();

		} catch (SyntaxValidationException e) {
		  /* the parse method can throw a SyntaxValidationException 
		   *if the PDF file can't be parsed.
		   *
		   *In this case, the exception contains an instance of ValidationResult
		   */
		  result = e.getResult();
		}

		// display validation result
		Set<String> rs = new HashSet<String>();
		if (result.isValid()) {
		  System.out.println("The resource is not a valid PDF/A-1b file");
		  metadata.set( PDF_PREFLIGHT_VALID, Boolean.TRUE.toString() );
		} else {
		  System.out.println("The resource is not valid, error(s) :");
		  metadata.set( PDF_PREFLIGHT_VALID, Boolean.FALSE.toString() );
		  for (ValidationError error : result.getErrorsList()) {
		    System.out.println(error.getErrorCode() + " : " + error.getDetails());
		    rs.add(error.getErrorCode() + " : " + error.getDetails());
		  }
		}

	    metadata.set( PDF_PREFLIGHT_ERRORS , rs.toArray( new String[] {} ));

	}

}
