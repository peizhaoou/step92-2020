// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.sps.servlets;

import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobInfoFactory;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.images.ImagesService;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.images.ServingUrlOptions;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.gson.Gson;
import com.google.sps.data.BlogMessage;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Servlet that returns some example content. TODO: modify this file to handle comments data */
@WebServlet("/data")
public class DataServlet extends HttpServlet {
    
    
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int numberOfCommentsToDisplay = 5;
     // numberOfCommentsToDisplay = getNumberOfCommentsToDisplay(request);  

      if (numberOfCommentsToDisplay < 1 || numberOfCommentsToDisplay > 100) {
        response.setContentType("text/html");
        response.getWriter().println("Please enter an integer between 1 and 100.");
        return;
      }

      List<BlogMessage> messages = new ArrayList<>();
      Query query = new Query("blogMessage").addSort("time", SortDirection.DESCENDING);
      DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
      PreparedQuery results = datastore.prepare(query);

      UserService userService = UserServiceFactory.getUserService();

      for (Entity entity : results.asIterable()) {
        long messageId = entity.getKey().getId();
        long timestamp = (long) entity.getProperty("time");
        String tags = (String) entity.getProperty("tag");
        String comment = (String) entity.getProperty("text");
        String nickname = (String) entity.getProperty("nickname");
        String email = (String) userService.getCurrentUser().getEmail();
        String image = (String) entity.getProperty("imgUrl");
        ArrayList<String> messageReplies = (ArrayList) entity.getProperty("replies");
        BlogMessage message = new BlogMessage(messageId, tags, comment, image, nickname, email, messageReplies, timestamp);
        messages.add(message);
      }

      Gson gson = new Gson();
      response.setContentType("application/json;");

      List<BlogMessage> limitedMessages = new ArrayList<>();
      if(numberOfCommentsToDisplay == 0){
        response.getWriter().println(gson.toJson(messages));
        return;
      }
      for(int i = 0; i < numberOfCommentsToDisplay; i++){
        limitedMessages.add(messages.get(i));
      }
     // response.setContentType("text/html;");
      response.getWriter().println(gson.toJson(limitedMessages));

    }
    

    /**
   * Converts a ServerStats instance into a JSON string using the Gson library
   */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
      String message = request.getParameter("text-input");

      String sender = getParameter(request, "sender", "Steven");

      String commentType = getParameter(request, "tags", "Default");

      String imageUrl = getUploadedFileUrl(request, "image");

      String messageRepliesString = getParameter(request, "replies", "");
	    String messageRepliesArray[] = messageRepliesString.split(",");
	    List<String> messageReplies = new ArrayList<String>();
	    messageReplies = Arrays.asList(messageRepliesArray);
      
      long timestamp = System.currentTimeMillis();

      Entity blogMessageEntity = new Entity("blogMessage");
      blogMessageEntity.setProperty("sender", sender);
      blogMessageEntity.setProperty("text", message);
      blogMessageEntity.setProperty("imgUrl", imageUrl);
      blogMessageEntity.setProperty("time", timestamp);
      blogMessageEntity.setProperty("tag", commentType);
      blogMessageEntity.setProperty("replies", messageReplies);
      DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
      datastore.put(blogMessageEntity);

      response.sendRedirect("/index.html");
    }

  /**
   * @return the request parameter, or the default value if the parameter
   *         was not specified by the client.
   */
    private String getParameter(HttpServletRequest request, String name, String defaultValue) {
      String value = request.getParameter(name);
      if (value == null) {
        return defaultValue;
      }
      return value;
    }

    /* Returns number of comments to display */
    private int getNumberOfCommentsToDisplay(HttpServletRequest request) {
      String numberOfCommentsString = getParameter(request, "comments-choice", "0");
      int numberOfComments;
      try {
        numberOfComments = Integer.parseInt(numberOfCommentsString);
      } catch (NumberFormatException e) {
        System.err.println("Could not convert to int: " + numberOfCommentsString);
        return 1;
      }
      return numberOfComments;
    }

    /** Returns a URL that points to the uploaded file, or null if the user didn't upload a file. */
    private String getUploadedFileUrl(HttpServletRequest request, String formInputElementName) {
      BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
      Map<String, List<BlobKey>> blobs = blobstoreService.getUploads(request);
      List<BlobKey> blobKeys = blobs.get(formInputElementName);

      if (blobKeys == null || blobKeys.isEmpty()) {
        return null;
      }

      // Our form only contains a single file input, so get the first index.
      BlobKey blobKey = blobKeys.get(0);

      // User submitted form without selecting a file, so we can't get a URL. (live server)
      BlobInfo blobInfo = new BlobInfoFactory().loadBlobInfo(blobKey);
      if (blobInfo.getSize() == 0) {
        blobstoreService.delete(blobKey);
        return null;
      }

      // We could check the validity of the file here, e.g. to make sure it's an image file
      // https://stackoverflow.com/q/10779564/873165

      // Use ImagesService to get a URL that points to the uploaded file.
      ImagesService imagesService = ImagesServiceFactory.getImagesService();
      ServingUrlOptions options = ServingUrlOptions.Builder.withBlobKey(blobKey);

      // To support running in Google Cloud Shell with AppEngine's dev server, we must use the relative
      // path to the image, rather than the path returned by imagesService which contains a host.
      try {
        URL url = new URL(imagesService.getServingUrl(options));
        return url.getPath();
      } catch (MalformedURLException e) {
        return imagesService.getServingUrl(options);
      }
    }

    //Get data out of hash table.
    /*private List<BlogMessage> getInfoFromHashTable(String tag, Hashtable<keys,values> table){
      List<BlogMessage> messages = new ArrayList<>();
      Set<String> keys = table.keySet();
      for(String key: keys){
        messages.add(table.get(key));
      }
      return messages;
    }*/
}