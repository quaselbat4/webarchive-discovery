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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.archive.io.ArchiveRecordHeader;
import uk.bl.wa.solr.SolrRecord;
import uk.bl.wa.util.Instrument;

import java.io.InputStream;

public class TwitterAnalyser extends AbstractPayloadAnalyser {
	private static Log log = LogFactory.getLog( TwitterAnalyser.class );

	public TwitterAnalyser(Config conf ) {
//	    this.extractLinks = conf.getBoolean( "warc.index.extract.linked.resources" );
		// TODO: Add setup
	}

	@Override
    public void analyse(ArchiveRecordHeader header, InputStream content, SolrRecord solr) {
        final long start = System.nanoTime();
        log.debug("Performing Twitter tweed analyzing");
		// TODO: Map Twitter-fields to Solr equivalents
		// content is guaranteed to be a Twitter tweet in JSON
		// https://developer.twitter.com/en/docs/tweets/data-dictionary/overview/intro-to-tweet-json

//		solr.addField( SolrFields.SOLR_LINKS_IMAGES, urlNorm);
        Instrument.timeRel("WARCPayloadAnalyzers.analyze#total", "TwitterAnalyzer.analyze#total", start);
    }
	
}
