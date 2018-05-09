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

import uk.bl.wa.solr.SolrFields;
import uk.bl.wa.solr.SolrRecord;
import uk.bl.wa.util.Instrument;
import uk.bl.wa.util.JSONExtractor;
import uk.bl.wa.util.Normalisation;

import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Analyzer to Twitter tweets harvested by API and packed as WARC by https://github.com/netarchivesuite/so-me.
 */
public class TwitterAnalyser extends AbstractPayloadAnalyser implements JSONExtractor.ContentCallback {
    private static Log log = LogFactory.getLog( TwitterAnalyser.class );

    // TODO: These dynamic fields does not have docValues in the current schema. Hopefully fixed when switching to the Solr 7 schema, but might require adjustments
    // TODO: Does the user-information belong in the same document as the Tweet? Shouldn't it be in a separate document (and linked somehow)?
    public static final String MENTIONS = "user_mentions_ss";
    public static final String SCREEN_NAME = "user_screen_name_ss";
    public static final String USER_URL = "user_url_ss";
    public static final String USER_ID = "user_id_tls";
    public static final String FOLLOWERS_COUNT = "user_followers_count_is";
    public static final String FRIENDS_COUNT = "user_friends_count_is";
    public static final String FAVOURITES_COUNT = "user_favourites_count_is";
    public static final String STATUSES_COUNT = "user_statuses_count_is";
    public static final String VERIFIED = "user_verified_bs";
    public static final String DESCRIPTION = "user_description_t";

    public static final String RETWEETED_COUNT = "retweeted_count_is";
    public static final String TWEET_ID = "tweet_id_tls";
    public static final String REPLY_TO_TWEET_ID = "reply_to_tweet_id_tls";
    public static final String REPLY_TO_USER_ID = "reply_to_user_id_tls";

    private final boolean normaliseLinks;

    // All encountered* sets are reset between each tweet parsing
    private final Set<String> encounteredHashtags = new HashSet<>();
    private final Set<String> encounteredLinks = new HashSet<>();
    private final Set<String> encounteredImageLinks = new HashSet<>();
    private final Set<String> encounteredMentions = new HashSet<>();

    private final JSONExtractor extractor = new JSONExtractor();
    { // We always do these
        extractor.add(SolrFields.SOLR_AUTHOR, true, this, ".user.name");
        extractor.add(SCREEN_NAME, true, this, ".user.screen_name");
        extractor.add(USER_URL, true, this, ".user.url");
        extractor.add(FOLLOWERS_COUNT, true, this, ".user.followers_count");
        extractor.add(FRIENDS_COUNT, true, this, ".user.friends_count");
        extractor.add(FAVOURITES_COUNT, true, this, ".user.favourites_count");
        extractor.add(STATUSES_COUNT, true, this, ".user.statuses_count");
        extractor.add(VERIFIED, true, this, ".user.verified");
        extractor.add(DESCRIPTION, true, this, ".user.description");
        extractor.add(USER_ID, true, this, ".user.id_str");

        extractor.add(RETWEETED_COUNT, true, this, ".retweeted_count");
        extractor.add(TWEET_ID, true, this, ".id_str");
        extractor.add(REPLY_TO_TWEET_ID, true, this, ".in_reply_to_status_id_str");
        extractor.add(REPLY_TO_USER_ID, true, this, ".in_reply_to_user_id_str");
        extractor.add(SolrFields.LAST_MODIFIED, true, this, ".created_at");
        extractor.add(SolrFields.SOLR_EXTRACTED_TEXT, true, this,
                      ".retweeted_status.extended_tweet.full_text",
                      ".retweeted_status.text",
                      ".extended_tweet.full_text",
                      ".text");
        extractor.add(SolrFields.SOLR_LINKS, false, this,
                      ".extended_tweet.entities.urls[].expanded_url",
                      ".retweeted_status.entities.urls[].expanded_url",
                      "entities.urls[].expanded_url");
        extractor.add(SolrFields.SOLR_KEYWORDS, false, this,
                      ".extended_tweet.entities.hashtags[].text",
                      ".retweeted_status.entities.hashtags[].text",
                      "entities.hashtags[].text");
        extractor.add(MENTIONS, false, this,
                      ".extended_tweet.entities.user_mentions[].screen_name",
                      ".retweeted_status.entities.user_mentions[].screen_name",
                      ".entities.user_mentions[].screen_name");
    }

    public TwitterAnalyser(Config conf ) {
        this.normaliseLinks = conf.hasPath(uk.bl.wa.parsers.HtmlFeatureParser.CONF_LINKS_NORMALISE) ?
                conf.getBoolean(uk.bl.wa.parsers.HtmlFeatureParser.CONF_LINKS_NORMALISE) :
                uk.bl.wa.parsers.HtmlFeatureParser.DEFAULT_LINKS_NORMALISE;
        if (conf.getBoolean("warc.index.extract.linked.images")) {
            extractor.add(SolrFields.SOLR_LINKS_IMAGES, false, this,
                          ".extended_tweet.entities.media[].media_url_https",
                          ".extended_tweet.entities.media[].media_url",
                          ".extended_entities.media[].media_url_https",
                          ".extended_entities.media[].media_url",
                          ".entities.media[].media_url_https",
                          ".entities.media[].media_url");
        }
    }

    @Override
    public String adjust(String jsonPath, String solrField, String content, SolrRecord solrRecord) {
        switch (solrField) {
            case SolrFields.LAST_MODIFIED: return handleDate(content, solrRecord);
            case USER_URL: return normaliseLinks ? Normalisation.canonicaliseURL(content) : content;
            case SolrFields.SOLR_AUTHOR: {
                solrRecord.addField(SolrFields.SOLR_TITLE, "Tweet by " + content);
                return content;
            }
            case SolrFields.SOLR_LINKS_IMAGES: return normaliseAndCollapse(content, encounteredImageLinks);
            case SolrFields.SOLR_LINKS: return normaliseAndCollapse(content, encounteredLinks);
            case SolrFields.SOLR_KEYWORDS: return normaliseAndCollapse(content, encounteredHashtags);
            case MENTIONS: return normaliseAndCollapse(content, encounteredMentions);
            default: return content;
        }
    }

    // Parses the Twitter date, "Thu Mar 27 15:41:37 +0000 2014", adds the year as SolrField and returns a Solr date
    private String handleDate(String content, SolrRecord solrRecord) {
        try {
            Date date = parseTwitterDate(content);
            solrRecord.setField(SolrFields.LAST_MODIFIED_YEAR, getYear(date));
            return getSolrTimeStamp(date);
        } catch (ParseException e) {
            log.warn("Unable to parse Twitter timestamp '" + content + "'");
            return null;
        }
    }

    /**
     * Normalises the URl if normaliseLinks is true, then returns null if the URL is already present in encoutered.
     * Else it is added to encountered and returned.
     */
    private String normaliseAndCollapse(String url, Set<String> encountered) {
        url = normaliseLinks ? Normalisation.canonicaliseURL(url) : url;
        return encountered.add(url) ? null : url;
    }

    // SimpleDateformat is not thread-safe so we synchronize
    private final DateFormat DF = new SimpleDateFormat("EEE MMM dd kk:mm:ss Z yyyy", Locale.ENGLISH);
    private synchronized Date parseTwitterDate(String content) throws ParseException {
        return DF.parse(content);
    }

    // content is guaranteed to be a Twitter tweet in JSON
    // https://developer.twitter.com/en/docs/tweets/data-dictionary/overview/intro-to-tweet-json
    @Override
    public void analyse(ArchiveRecordHeader header, InputStream content, SolrRecord solr) {
        final long start = System.nanoTime();
        encounteredImageLinks.clear();
        encounteredLinks.clear();
        encounteredHashtags.clear();
        encounteredMentions.clear();
        log.debug("Performing Twitter tweet analyzing");

        solr.removeField(SolrFields.SOLR_EXTRACTED_TEXT); // Clear any existing content
        try {
            if (!extractor.applyRules(content, solr)) {
                log.warn("Twitter analysing finished without output for tweet " + header.getUrl());
            }
        } catch (Exception e) {
            log.error("Error analysing Twitter tweet " + header.getUrl(), e);
            solr.addParseException("Error analysing Twitter tweet" + header.getUrl(), e);
        }
        solr.makeFieldSingleStringValued(SolrFields.SOLR_EXTRACTED_TEXT);

        Instrument.timeRel("WARCPayloadAnalyzers.analyze#total", "TwitterAnalyzer.analyze#total", start);
    }

    // All date-related fields are in UTZ
    private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private final DateFormat yearFormat = new SimpleDateFormat("yyyy");
    {
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        yearFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }
    private synchronized String getSolrTimeStamp(Date date){
        return dateFormat.format(date)+"Z";
    }
    private synchronized String getYear(Date date){
        return yearFormat.format(date);
    }
}
