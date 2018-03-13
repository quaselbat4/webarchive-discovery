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

import uk.bl.wa.parsers.TwitterParser;
import uk.bl.wa.solr.SolrFields;
import uk.bl.wa.solr.SolrRecord;
import uk.bl.wa.util.Instrument;
import uk.bl.wa.util.Normalisation;

import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

/**
 * Analyzer to Twitter tweets harvested by API and packed as WARC by https://github.com/netarchivesuite/so-me.
 */
public class TwitterAnalyser extends AbstractPayloadAnalyser {
  private static Log log = LogFactory.getLog( TwitterAnalyser.class );
  private boolean normaliseLinks;
  private boolean extractImageLinks;

  public TwitterAnalyser(Config conf ) {      
    this.extractImageLinks = conf.getBoolean( "warc.index.extract.linked.images" );
    this.normaliseLinks = conf.hasPath(uk.bl.wa.parsers.HtmlFeatureParser.CONF_LINKS_NORMALISE) ?
        conf.getBoolean(uk.bl.wa.parsers.HtmlFeatureParser.CONF_LINKS_NORMALISE) :
          uk.bl.wa.parsers.HtmlFeatureParser.DEFAULT_LINKS_NORMALISE;
  }

  @Override
  public void analyse(ArchiveRecordHeader header, InputStream content, SolrRecord solr) {
    final long start = System.nanoTime();
    log.debug("Performing Twitter tweet analyzing");
    // TODO: Map Twitter-fields to Solr equivalents
    // content is guaranteed to be a Twitter tweet in JSON
    // https://developer.twitter.com/en/docs/tweets/data-dictionary/overview/intro-to-tweet-json

    String json = null;
    try{
      json = IOUtils.toString(content, "UTF-8");
      TwitterParser parser = new TwitterParser(json);  //All fields mapped to java
      solr.addField(SolrFields.SOLR_AUTHOR, parser.getAuthor());
      Date modified = parser.getCreateDate();                

      int year = Integer.parseInt(new SimpleDateFormat("yyyy").format(modified)); //date.getYear() deprecated
      solr.addField( SolrFields.LAST_MODIFIED_YEAR, ""+year);                 
      solr.addField(SolrFields.LAST_MODIFIED, getSolrTimeStamp(modified));
      //solr.addField(SolrFields.SOLR_EXTRACTED_TEXT,parser.getContent()); //already set from warc-indexer 

      if (extractImageLinks){

        ArrayList<String> imageUrlFormatted = formatUrls(parser.getImageUrlsList());
        for (String urlFormated : imageUrlFormatted){           
          solr.addField( SolrFields.SOLR_LINKS_IMAGES, urlFormated);
        }
      }

      for (String hashTag : parser.getHashTagsList()){                 
        solr.addField( SolrFields.SOLR_KEYWORDS,hashTag);
      }


    }
    catch(Exception e){
      log.error("Error parsing twitter Json: " + e.getMessage());
      solr.addParseException("Error parsing twitter Json", e);
      return;
    }


    Instrument.timeRel("WARCPayloadAnalyzers.analyze#total", "TwitterAnalyzer.analyze#total", start);
  }


  private  ArrayList<String> formatUrls(ArrayList<String > urls){	    
    if (!normaliseLinks){ //Do nothing. Not recommended, but has to follow the config.
      return urls;
    }
    else{
      ArrayList<String> urlNorms = new ArrayList<String>();
      for (String url :urls){
        String urlNorm =  Normalisation.canonicaliseURL(url);	            
        urlNorms.add(urlNorm);
      }	      	      
      return urlNorms;
    }
  }

  private String getSolrTimeStamp(Date date){
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"); //not thread safe, so create new         
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));    
    return dateFormat.format(date)+"Z";

  }


}
