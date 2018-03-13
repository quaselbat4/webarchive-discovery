/**
 * 
 */
package uk.bl.wa.analyser.payload;

/*
 * #%L
 * warc-indexer
 * %%
 * Copyright (C) 2013 - 2014 The UK Web Archive
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

import com.typesafe.config.Config;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.archive.io.ArchiveRecordHeader;
import org.json.JSONObject;
import uk.bl.wa.solr.SolrFields;
import uk.bl.wa.solr.SolrRecord;
import uk.bl.wa.util.Instrument;
import uk.bl.wa.util.JSONExtractor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analyzer to Jodels harvested by API and packed as WARC by https://github.com/netarchivesuite/so-me.
 */
@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
public class JodelAnalyser extends AbstractPayloadAnalyser {
	private static Log log = LogFactory.getLog( JodelAnalyser.class );

	private static final JSONExtractor extractor = new JSONExtractor();
	static {
	    extractor.add(SolrFields.SOLR_EXTRACTED_TEXT, true, ".details.message");
	    extractor.add(SolrFields.SOLR_EXTRACTED_TEXT, false, ".replies[].message");
	    // TODO: Extract links & images
    }

	public JodelAnalyser(Config conf ) {
//	    this.extractLinks = conf.getBoolean( "warc.index.extract.linked.resources" );
		// TODO: Add setup
	}

    // content is guaranteed to be a Jodel thread in JSON
	// https://github.com/netarchivesuite/so-me
	@Override
    public void analyse(ArchiveRecordHeader header, InputStream content, SolrRecord solr) {
        final long start = System.nanoTime();
        log.debug("Performing Jodel post analysation, including replies");

        String jodel;
        try {
            jodel = IOUtils.toString(content, "UTF-8");
        } catch (IOException e) {
            log.error("Error converting InputStream to String", e);
            solr.addParseException("Error converting InputStream to String", e);
            return;
        }
        try {
            analyse(jodel, solr);
        } catch (Exception e) {
            log.error("Error analysing Jodel post", e);
            solr.addParseException("Error analysing Jodel post", e);
            return;
        }
        Instrument.timeRel("WARCPayloadAnalyzers.analyze#total", "JodelAnalyzer.analyze#total", start);
    }

    void analyse(String jodel, SolrRecord solr) {
        JSONObject jodelPost = new JSONObject(jodel);
        if (!extractor.applyRules(jodelPost, solr)) {
            log.warn("Jodel analysing finished without output");
        }
    }
}
