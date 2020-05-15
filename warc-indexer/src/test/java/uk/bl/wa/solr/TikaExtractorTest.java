/**
 * 
 */
package uk.bl.wa.solr;

/*
 * #%L
 * warc-indexer
 * %%
 * Copyright (C) 2013 - 2020 The webarchive-discovery project contributors
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

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URL;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;

import uk.bl.wa.analyser.payload.TikaPayloadAnalyser;

/**
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class TikaExtractorTest {
    private static Log log = LogFactory.getLog(TikaExtractorTest.class);

    private TikaPayloadAnalyser tika;

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        tika = new TikaPayloadAnalyser();
        tika.configure(ConfigFactory.load());
    }

    @Test
    public void testMonaLisa() throws Exception {
        File ml = new File("src/test/resources/wikipedia-mona-lisa/Mona_Lisa.html");
        if (!ml.exists()) {
            log.error("The Mona Lisa test file '" + ml + "' does not exist");
            return;
        }
        URL url = ml.toURI().toURL();
        SolrRecord solr = SolrRecordFactory.createFactory(null).createRecord();
        tika.extract(ml.getPath(), solr, url.openStream(), url.toString());
        System.out.println("SOLR " + solr.getSolrDocument().toString());
        String text = (String) solr.getField(SolrFields.SOLR_EXTRACTED_TEXT)
                .getValue();
        assertTrue("Text should contain this string!",
                text.contains("Mona Lisa"));
        assertFalse(
                "Text should NOT contain this string! (implies bad newline handling)",
                text.contains("encyclopediaMona"));
    }

    @Test
    public void testJSON() throws Exception {
        final String EXPECTED = "Text with non-ASCII-compatible Unicode characteres represented as UTF-8:" +
                                " ☃︎(snowman, Unicode 9731), ★ (Black Star, Unicode 9733)";
        File ml = new File("src/test/resources/simple_json.json");
        if (!ml.exists()) {
            log.error("The test file '" + ml + "' does not exist");
            return;
        }
        URL url = ml.toURI().toURL();
        SolrRecord solr = SolrRecordFactory.createFactory(null).createRecord();
        tika.extract(ml.getPath(), solr, url.openStream(), url.toString());

        String content = solr.getField(SolrFields.SOLR_EXTRACTED_TEXT).getValue().toString();
        String encoding = solr.getField(SolrFields.CONTENT_ENCODING).getValue().toString();
        String type = solr.getField(SolrFields.SOLR_CONTENT_TYPE).getValue().toString();

        assertTrue("Content should contain '" + EXPECTED + "', but was\n" + content,
                   content != null && content.contains(EXPECTED));

        assertEquals("Encoding should be as expected", "UTF-8", encoding);
        assertEquals("Content-Type should be as expected", "application/json; charset=UTF-8", type);
    }

}
