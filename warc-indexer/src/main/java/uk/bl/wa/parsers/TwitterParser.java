package uk.bl.wa.parsers;

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
    String text =  full.getString("full_text");
    this.content=text;
    
    JSONObject entities = full.getJSONObject("entities");
    JSONArray hashTags = entities.getJSONArray("hashtags");
    
    for (int i = 0;i<hashTags.length();i++){ //keywords
      String tag =  ((JSONObject) hashTags.get(i)).getString("text");
      hashTagsList.add(tag);       
    }
    
    JSONArray media = entities.getJSONArray("media");
    
    for (int i = 0;i<media.length();i++){  //images
      JSONObject medie= media.getJSONObject(i);
      
      String type =  medie.getString("type");
      if ("photo".equals(type)){
        String imageUrl =  medie.getString("media_url");
        imageUrlsList.add(imageUrl);        
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
      //set language/content_type            
    }   
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
