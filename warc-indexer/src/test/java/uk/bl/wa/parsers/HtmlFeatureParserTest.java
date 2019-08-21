/**
 * 
 */
package uk.bl.wa.parsers;

/*
 * #%L
 * warc-indexer
 * %%
 * Copyright (C) 2013 - 2018 The webarchive-discovery project contributors
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

import com.typesafe.config.ConfigFactory;
import junit.framework.Assert;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.ParseError;
import org.jsoup.parser.Parser;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class HtmlFeatureParserTest {
    private static Log log = LogFactory.getLog(HtmlFeatureParserTest.class);

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void JSoupElementsAddedTest() throws Exception {
        // Browsers handle JavaScript with less than signs well, when it is inside script-tags.
        // Tika treats the content of script-elements as HTML and treats all containing '<something' as tags
        String testHtml = "<b>test<p></b><script> if (3<a) console.log('something');</script>";
        String baseUri = "http://example.com/dummy.html";
        //
        ByteArrayInputStream stream = new ByteArrayInputStream(
                testHtml.getBytes());
        stream.mark(100000);
        Parser parser = Parser.xmlParser();
        parser.setTrackErrors(1000);
        Document doc = Jsoup.parse(stream, null, baseUri, parser);
        System.out.println("Parsed as:\n" + doc);
        System.out.println("Got " + parser.getErrors().size()
                + " under isTrackErrors = " + parser.isTrackErrors());
        for (ParseError err : parser.getErrors()) {
            System.out.println("Got: " + err);
        }

        // Also run in through the handler class:
        stream.reset();
        innerBasicParseTest(stream, baseUri, 3);
    }

    @Test
    public void testNormalise() {
        final String[][] TESTS = new String[][] { // Expected, input
//                {"http://example.org", "http://www.example.org"},
                {"http://example.org/foo", "http://www.example.org/foo"},
                {"http://example.org/", "http://example.org"},
                {"http://example.org/", "http://example.org?"},
                //{"http://example.org", "https://example.org?"},
                {"http://example.org/", "http://user@example.org"},
                {"http://example.org/foo", "http://user@www.example.org/foo"},
//                {"http://example.org", "http://user@www.example.org"},
                {"http://example.org/", "http://eXample.org"},
                {"http://example.org/", "http://example.ORG"},
//                {"http://example.org", "http://example.org/"},
//                {"http://example.org", "http://example.org/index.html"}
        };
        Map<String, String> normMap = new HashMap<String, String>();
        normMap.put(HtmlFeatureParser.CONF_LINKS_NORMALISE, "true");
        HtmlFeatureParser normParser = new HtmlFeatureParser(ConfigFactory.parseMap(normMap));

        normMap.put(HtmlFeatureParser.CONF_LINKS_NORMALISE, "false");
        HtmlFeatureParser skipParser = new HtmlFeatureParser(ConfigFactory.parseMap(normMap));

        for (String[] test: TESTS) {
            Assert.assertEquals("Normalisation of '" + test[1] + "'",
                                test[0], normParser.normaliseLink(test[1]));
            Assert.assertEquals("Non-normalisation of '" + test[1] + "'",
                                test[1], skipParser.normaliseLink(test[1]));
        }
    }

    private static void printMetadata(Metadata metadata) {
        for (String name : metadata.names()) {
            for (String value : metadata.getValues(name)) {
                System.out.println(name + ": " + value);
            }
        }
    }

    /**
     * Tests that HtmlFeatureParser falls back to parsing UTF-8, even if the user's default charset is not UTF-8.
     */
    @Test
    public void testCharsetUTF8() throws IOException, URISyntaxException, TikaException, SAXException {
        final String PARAGRAPH = "Æblegrød";
        final String HTML = "<html><head><title>No charset specified</title></head>\n" +
                            "<body><h1>No explicit charset</h1><p>" + PARAGRAPH + "</p></body></html>";
        final File FILE = File.createTempFile("locale_test_", ".html");

        /*if ("UTF-8".equals(Charset.defaultCharset().toString())) {
            log.warn("The unit test testCharset is running with 'UTF-8', which always works. " +
                     "Add the JVM option -Dfile.encoding=\"ascii\" to test properly. " +
                     "Unfortunately this cannot be set reliably at runtime");
        }*/

        Files.write(FILE.toPath(), HTML.getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE);
        FILE.deleteOnExit();
        URL url = FILE.toURI().toURL();

        HtmlFeatureParser hfp = new HtmlFeatureParser();
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, url.toString());
        hfp.parse(url.openStream(), null, metadata, null);
        String extracted = metadata.get("FirstParagraph");
        Assert.assertEquals("The extracted paragraph should match the original. " +
                            "JVM defaultCharset == '" + Charset.defaultCharset() + "'",
                            PARAGRAPH, extracted);
    }

    private void innerBasicParseTest(InputStream stream, String uri,
            int numElements)
            throws IOException, SAXException, TikaException {
        HtmlFeatureParser hfp = new HtmlFeatureParser();
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, uri);
        hfp.parse(stream, null, metadata, null);
        printMetadata(metadata);
        // for (ParseError err : hfp.getParseErrors()) {
        // System.out.println("PARSE-ERROR: " + err);
        // }
        Assert.assertEquals("Number of distinct elements was wrong,",
                numElements,
                metadata.getValues(HtmlFeatureParser.DISTINCT_ELEMENTS).length );
    }

    /**
     * Test method for
     * {@link uk.bl.wa.parsers.HtmlFeatureParser#parse(java.io.InputStream, org.xml.sax.ContentHandler, org.apache.tika.metadata.Metadata, org.apache.tika.parser.ParseContext)}
     * .
     * 
     * @throws Exception
     */
    @Test
    public void testParseInputStreamContentHandlerMetadataParseContext()
            throws Exception {
        String baseUri = "http://en.wikipedia.org/wiki/Mona_Lisa";
        File ml = new File(
                "src/test/resources/wikipedia-mona-lisa/Mona_Lisa.html");
        URL url = ml.toURI().toURL();
        if (!ml.exists()) {
            System.err.println("testParseInputStreamContentHandlerMetadataParseContext: Unable to locate Mona Lisa test file. Skipping test");
            return;
        }
        innerBasicParseTest(url.openStream(), baseUri, 43);
    }

}
