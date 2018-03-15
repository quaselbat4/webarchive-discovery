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
import uk.bl.wa.util.Normalisation;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzer to Jodels harvested by API and packed as WARC by https://github.com/netarchivesuite/so-me.
 */
@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
public class JodelAnalyser extends AbstractPayloadAnalyser implements JSONExtractor.ContentCallback {
	private static Log log = LogFactory.getLog( JodelAnalyser.class );

	public static final Pattern HASHTAG = Pattern.compile("#(\\w+)", Pattern.UNICODE_CHARACTER_CLASS);

	final JSONExtractor extractor = new JSONExtractor();
    private final boolean extractImageLinks;
    private final boolean normaliseLinks;

    { // We always do these
	    extractor.add(SolrFields.SOLR_EXTRACTED_TEXT, true, this,".details.message");
	    extractor.add(SolrFields.SOLR_EXTRACTED_TEXT, false, this,".replies[].message");
	    extractor.add(SolrFields.POSTCODE_DISTRICT, false, this, ".details.location.name", ".replies[].location.name");
    }

	public JodelAnalyser(Config conf) {
        extractImageLinks = conf.getBoolean( "warc.index.extract.linked.images" );
        normaliseLinks = conf.hasPath(uk.bl.wa.parsers.HtmlFeatureParser.CONF_LINKS_NORMALISE) ?
            conf.getBoolean(uk.bl.wa.parsers.HtmlFeatureParser.CONF_LINKS_NORMALISE) :
              uk.bl.wa.parsers.HtmlFeatureParser.DEFAULT_LINKS_NORMALISE;
        if (extractImageLinks) {
            extractor.add(SolrFields.SOLR_LINKS_IMAGES, false, this,".details.image_url", ".replies[].image_url");
        }
        // TODO: Add extraction of links (needs sample as links are uncommon in Danish jodels)
        // TODO: Get sample with images in replies to verify ".replies[].image_url" path
        // TODO: harvest-location into location?
	}

	// Adjustment of specific fields
    @Override
    public String adjust(String jsonPath, String solrField, String content, SolrRecord solrRecord) {
        switch (solrField) {
            case SolrFields.SOLR_LINKS_IMAGES: {
                content = content.startsWith("http") ? content : "https:" + content;
                return normaliseLinks ? Normalisation.canonicaliseURL(content) : content;
            }
            case SolrFields.SOLR_EXTRACTED_TEXT: {
                addKeywordsFromHashtags(content, solrRecord);
                return content;
            }
            case SolrFields.POSTCODE_DISTRICT: {
                return filterLocation(content);
            }
            default: return content;
        }
    }

    // Jodel mixes location names (city names normally) with relative distance to caller
    // TODO: When relative is used, concatenate with callers location (or just use callers location)
    // https://jodel.zendesk.com/hc/en-us/articles/360001019833-What-does-it-mean-when-a-post-is-here-very-close-close-far-or-from-the-hometown-
    private String filterLocation(String location) {
        switch(location.toLowerCase()) {
            case "far":
            case "close":
            case "very close":
            case "here":
            case "hometown":
                return null;
            default: return location; // Probably a usable location name
        }
    }

    // Simple matching for hashtags in text content
    private void addKeywordsFromHashtags(String content, SolrRecord solrRecord) {
        Matcher m = HASHTAG.matcher(content);
        while (m.find()) {
            solrRecord.addField(SolrFields.SOLR_KEYWORDS, m.group(1));
        }
    }

    // content is guaranteed to be a Jodel thread in JSON as produced by
	// https://github.com/netarchivesuite/so-me
	@Override
    public void analyse(ArchiveRecordHeader header, InputStream jodelJson, SolrRecord solr) {
        final long start = System.nanoTime();
        log.debug("Performing Jodel post analysation, including replies");
        solr.removeField(SolrFields.SOLR_EXTRACTED_TEXT); // Clear any existing content
        try {
            if (!extractor.applyRules(jodelJson, solr)) {
                log.warn("Jodel analysing finished without output");
            }
        } catch (Exception e) {
            log.error("Error analysing Jodel post", e);
            solr.addParseException("Error analysing Jodel post", e);
        }
        solr.makeFieldSingleStringValued(SolrFields.SOLR_EXTRACTED_TEXT);
        Instrument.timeRel("WARCPayloadAnalyzers.analyze#total", "JodelAnalyzer.analyze#total", start);
    }

}
