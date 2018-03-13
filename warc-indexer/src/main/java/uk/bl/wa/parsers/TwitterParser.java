package uk.bl.wa.parsers;

/*
 * #%L
 * warc-indexer
 * %%
 * Copyright (C) 2013 - 2018 The UK Web Archive
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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

public class TwitterParser {

  private String author;
  private Date createDate;
  private ArrayList<String> imageUrlsList;
  private ArrayList<String> hashTagsList;
  private String content;

  public TwitterParser(String json) throws Exception{
    
    imageUrlsList = new  ArrayList<String>();
    hashTagsList = new  ArrayList<String>();

    JSONObject full = new JSONObject(json);        
    String text =  "";

    if (full.has("full_text")){
      //System.out.println("fulltext case");
      text = full.getString("full_text");
    }    
    else{
      //System.out.println("text case");
      text =full.getString("text"); //legacy 
    }

    this.content=text;


    JSONObject entities; // Getting the entities require many special cases. Sometimes they are double, need to read into specification

    if (full.has("retweeted_status")) {
      //System.out.println("retweeted case");
      JSONObject retweet = full.getJSONObject("retweeted_status");
      if (retweet.has("extended_tweet")){
        entities = retweet.getJSONObject("extended_tweet").getJSONObject("entities");
      }
      else{
        entities = retweet.getJSONObject("entities");

      }           
    }
    else if (full.has("entities")){     
      entities = full.getJSONObject("entities");            
      //System.out.println("entities case");
    }
    else{
      throw new Exception("could not find entities on twitter JSON");
    }


    //media(images), not always there.
    if (entities.has("media")){
      JSONArray media = entities.getJSONArray("media");   
      for (int i = 0;i<media.length();i++){  //images
        JSONObject medie= media.getJSONObject(i);

        String type =  medie.getString("type");
        if ("photo".equals(type)){
          String imageUrl =  medie.getString("media_url");
          imageUrlsList.add(imageUrl);        
          System.out.println("found image:"+imageUrl);
        }
      }               
    }

    JSONArray hashTags = entities.getJSONArray("hashtags");

    for (int i = 0;i<hashTags.length();i++){ //keywords
      String tag =  ((JSONObject) hashTags.get(i)).getString("text");
      hashTagsList.add(tag);      
    }

    JSONObject user= full.getJSONObject("user"); 
    String author = user.getString("name");
    this.author=author;


    //Format Fri Mar 02 10:26:13 +0000 2018
    String created_at_str = full.getString("created_at");

    DateFormat df = new SimpleDateFormat("EEE MMM dd kk:mm:ss Z yyyy", Locale.ENGLISH);
    Date created_at =  df.parse(created_at_str);        
    this.createDate=created_at;

    //TODO
    //set language/content_type ?            

  }

  public String getAuthor() {
    return author;
  }

  public Date getCreateDate() {
    return createDate;
  }

  public ArrayList<String> getImageUrlsList() {
    return imageUrlsList;
  }

  public ArrayList<String> getHashTagsList() {
    return hashTagsList;
  }

  public String getContent() {
    return content;
  }


}
