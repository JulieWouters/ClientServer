import java.io.*;
import java.net.*;

// added to parse response
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;

class HTTPClient
{
   public static void main(String argv[]) throws Exception
   {
      // TO DO: get command from the user via command line instead
      BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));

      // validate command line arguments
      if (argv.length != 4) {
          System.out.println("=== ERR: Invalid request");
          System.exit(1);
      }

      String httpMethod = argv[0];
      String URI = argv[1];
      int port = 0;
      try{
          port = Integer.parseInt(argv[2]);
      }
      catch (NumberFormatException e){
          System.out.println("=== ERR: Failed to convert port to int");
          System.exit(1);
      }
      String httpVersion = argv[3];

      // validate HTTP command
      if (validateCommand(httpMethod) != 0) {
          System.out.println("=== ERR: Invalid HTTP command");
          System.exit(1);
      }
      
      // validate TCP port
      if (port <= 0 || port > 65535) {
          System.out.println("=== ERR: Invalid TCP port number");
          System.exit(1);
      }
      
      // validate HTTP version
      if (validateVersion(httpVersion) != 0) {
          System.out.println("=== ERR: Invalid HTTP version");
          System.exit(1);
      }

      String extraArg = "";
      if (httpMethod.equals("PUT") || httpMethod.equals("POST")) {
         System.out.println(httpMethod + " method requires an argument:");
         extraArg = inFromUser.readLine();
         System.out.println("=== Extra argument:\n" + extraArg);
      }
      
      // parse URI
      // we might add more checks for the URL format
      // get variables behind the ? sign
      String[] uriPlusVars = URI.split("\\?");
      String uriVars = "";
      if (uriPlusVars.length == 1) {
          // no variables
      } else if (uriPlusVars.length == 2) {
          uriVars = uriPlusVars[1];
      } else {
          System.out.println("=== ERR: Invalid URI");
      }
      String uriWithoutVars = uriPlusVars[0];
      String[] uriParts = uriWithoutVars.split("/");
      if (uriParts.length < 3) {
          System.out.println("=== ERR: Invalid URI");
          System.exit(1);
      }
      String protocol = uriParts[0];
      // check protocol
      if (protocol.equals("http:")) {
          System.out.println("=== OK http URI");
      } else {
          System.out.println("=== ERR: Protocol in URI is invalid or not supported");
          System.exit(1);
      }
      String host = uriParts[2];
      
      // getting what we want from the URI
      // example http://www.kuleuven.be/kuleuven/index.html
      // baseUri = /kuleuven/
      // finalUri = /kuleuven/index.html
      // XXX still not clear whether URI is file or directory if it does not end in /
      
      // get last part of the URI (anything after host)
      int uriPos = protocol.length() + 2 + host.length();
      String finalUri = uriWithoutVars.substring(uriPos);
      
      // get the base dir of finalUri
      int uriNoElements = uriParts.length;
      String baseUri = finalUri;
      if (uriNoElements > 3 && ! uriWithoutVars.substring(uriWithoutVars.length()-1).equals("/")) {
          // last character is not a /
          int finalUriLength = finalUri.length();
          int lastUriPartLength = uriParts[uriNoElements-1].length();
          baseUri = finalUri.substring(0,finalUriLength-lastUriPartLength);
      }
      // make sure this ends in /
      if (baseUri.length() > 0 && ! baseUri.substring(baseUri.length()-1).equals("/")) {
              baseUri = baseUri + "/";
      }
      // debug
      System.out.println("=== baseUri = " + baseUri);
      System.out.println("=== finalUri = " + finalUri);
      // get / if the rest of the URI is empty
      if (finalUri.equals("")) {
          finalUri = "/";
      }

      // set up a TCP connection
      Socket clientSocket = new Socket(host, port);
      if (clientSocket != null){
          System.out.println("==> Connection established!");
      } else {
          // TO DO: handle exceptions, for example unknown host? with try/catch?
          System.out.println("==> ERR: Connection failure!");
          System.exit(1);
      }
      
      DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
      InputStream isFromServer = clientSocket.getInputStream();
//      BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
      
      // construct the complete request we send to the server
      String httpRequest = createRequest(httpMethod, finalUri, host, uriVars, httpVersion);
      System.out.println("==> Sending the following request:\n" + httpRequest);
      outToServer.writeBytes(httpRequest);
      outToServer.flush();

      // TO DO: take different action depending on httpMethod

      String responseContent = handleResponse(isFromServer, httpVersion);
      System.out.println("<== Response content from server:\n" + responseContent);

      // immediately close the TCP connection in case of HTTP 1.0
      if (httpVersion.equals("1.0")) {
          clientSocket.close();
      }
      
      if (httpMethod.equals("GET")) { 
         // get embedded images using pattern match and put them in an array list
         Pattern p = Pattern.compile("img src\\s*=\\s*([\"'])?([^ \"']*)", Pattern.CASE_INSENSITIVE);
         Matcher m = p.matcher(responseContent);
         ArrayList<String> imgSrcList = new ArrayList<String>();
         while (m.find()) {
            imgSrcList.add(m.group(2));
         }
         // the array elements are not unique... optimization issue
         
         System.out.println("=== List of embedded <img> URLs: " + imgSrcList);
         System.out.println("=== imgSrcList size = " + imgSrcList.size());
    
         // loop over the img URLs 
         for (int imgCount = 0; imgCount < imgSrcList.size(); imgCount++) {
            String imgPath = imgSrcList.get(imgCount);
            System.out.println("=== IMG URL " + imgCount + " = " + imgPath);
            if (imgPath.indexOf("http://") == 0) {
              // foreign path (starting with http://) for embedded object
              // TO DO: extract new host/port/uri in this case - create function for this outside main()?
              // in case the host/port is different, we always need a new socket
              // XXX not supported for now
              System.out.println("=== skipping foreign <img> URL: " + imgPath);
              continue; // go to next element
            } else if (imgPath.substring(0,1).equals("/")) {
              // absolute path for embedded object
                finalUri = imgPath;
            } else {
              // relative path for embedded object
                finalUri = baseUri + imgPath;
            }
            String imgFileName = "";
            int lastSlashIndex = imgPath.lastIndexOf("/");
            if (lastSlashIndex == -1) {
               imgFileName = imgPath;
            } else {
               imgFileName = imgPath.substring(lastSlashIndex);
            }

            System.out.println("=== IMG finalUri = " + finalUri);
            System.out.println("=== IMG filename = " + imgFileName);
   
            Socket clientSocketNew = null;
            // determine socket to use depending on HTTP version
            if (httpVersion.equals("1.0")) {
               // set up a new TCP connection for every object
               // TO DO: also do this if HTTP/1.1 and no persistent connection!
               // need to save connection state/properties somewhere
               clientSocketNew = new Socket(host, port);
               if(clientSocketNew != null) {
                  System.out.println("==> Connection established!");
               }
               outToServer = new DataOutputStream(clientSocketNew.getOutputStream());
//               inFromServer = new BufferedReader(new InputStreamReader(clientSocketNew.getInputStream()));
               isFromServer = clientSocketNew.getInputStream();
            } else if (httpVersion.equals("1.1")) {
               // use existing socket which is still open
            } else {
               // TO DO: handle exceptions?
               System.out.println("==> ERR: Connection failure!");
               System.exit(1);
            }
   
            // construct the complete request we send to the server (empty vars for img)
            httpRequest = createRequest("GET", finalUri, host, "", httpVersion);
            System.out.println("==> Sending the following request:\n" + httpRequest);
            outToServer.writeBytes(httpRequest);
            outToServer.flush();
   
            // TO DO parse the response - (call new function)
            String filename = "";
            String imgContent = handleResponse(isFromServer, httpVersion);

            System.out.println("<== Writing IMG " + imgCount + " to file " + imgFileName);
            DataOutputStream imgDos = null;
            try {
                imgDos = new DataOutputStream(new FileOutputStream(imgFileName));
                imgDos.write(imgContent.getBytes());
                imgDos.flush();
            } catch ( IOException e) { }
            finally
            {
                try {
                    if (imgDos != null)
                    imgDos.close( );
                } catch ( IOException e) { }
            }
   
            if (httpVersion.equals("1.0")) {
               clientSocketNew.close();
            }
         } // end for (img)
      } // end if (GET)

      // close the TCP connection after getting all that we need
      if (httpVersion.equals("1.1")) {
          clientSocket.close();
      }
   } // end main

   public static int validateVersion(String ver) {
      // validate HTTP version
      if (ver.equals("1.0")) {
          System.out.println("=== OK HTTP/1.0 request");
      } else if (ver.equals("1.1")) {
          System.out.println("=== OK HTTP/1.1 request");
      } else {
          return -1;
      }
      return 0;
   }

   public static int validateCommand(String method) {
      // validate HTTP method
      if (method.equals("GET")  ||
          method.equals("HEAD") ||
          method.equals("POST") ||
          method.equals("PUT")) {
          System.out.println("=== OK " + method + " is valid");
      } else {
          return -1;
      }
      return 0;
   }

   // we will need this several times...
   public static String createRequest(String method, String uri, String host, String content, String httpVer) {
      String req = "";
      req = method + " " + uri + " HTTP/" + httpVer + "\r\n";
      // HTTP 1.1: mandatory to put a Host: header in the request
      if (httpVer.equals("1.1")) {
         String hostHeader = "Host: " + host + "\r\n";
         req = req + hostHeader;
      }
      if (method.equals("POST")) {
         // add content: in this case URI variables but can be something else
         req = req + "Content-Length: " + content.length() + "\r\n"
                   + "\r\n"
                   + content + "\r\n";
      } else if (method.equals("PUT")) {
         // TO DO: PUT method
      } 
      req = req + "\r\n";
      return req;
   }

   public static String handleResponse(InputStream is, String httpVer) {
      // get response from server and put the content in a string rc
      String rc = "";
      String responseLine = "";
      String firstResponseLine = "";
      ArrayList<String> responseHeaders = new ArrayList<String>();
      int lineCount = 0;
      int headersFinished = 0;
      BufferedReader br = new BufferedReader(new InputStreamReader(is));
      DataInputStream dis = new DataInputStream(is);
      while(headersFinished == 0) {
          try{
             responseLine = br.readLine();
          }catch(IOException e){
             e.printStackTrace();
          }
          // save first response line which contains the HTTP return code
//          System.out.println("<== Response line:\n" + responseLine);
          if (lineCount == 0) {
             firstResponseLine = responseLine;
          }
          if (headersFinished == 0) {
             if (responseLine.equals("")) {
                headersFinished = 1;
             } else {
                responseHeaders.add(responseLine);
             }
          }
          lineCount++;
      }

      // parse HTTP return code
      String[] returnCodeParts = firstResponseLine.split(" ");
      if (returnCodeParts.length < 3) {
          System.out.println("=== ERR: Invalid response line");
          System.exit(1);
      }
      String httpResponseVersion = returnCodeParts[0]; 
      // check HTTP version
      if (httpResponseVersion.equals("HTTP/1.0")) {
          System.out.println("=== OK HTTP/1.0 response");
      } else if (httpResponseVersion.equals("HTTP/1.1")) {
          System.out.println("=== OK HTTP/1.1 response");
      } else {
          System.out.println("=== ERR: Invalid HTTP version in response");
          System.exit(1);
      }
      
      int httpResponseCode = 0;
      try{
          httpResponseCode = Integer.parseInt(returnCodeParts[1]);
      }
      catch (NumberFormatException e){
          System.out.println("=== ERR: Invalid response code");
          System.exit(1);
      }
      String httpResponseEnglishError = returnCodeParts[2];
      
      // stop when we get error code from the server
      if (httpResponseCode > 400) {
          System.out.println("=== ERR: Response code > 400");
          System.out.println(firstResponseLine);
          System.exit(1);
      }
      // continue if return code == 200? 

      // parse response headers
      // we need to know how much data to read,
      // especially for HTTP/1.1 which can keep the connection open
      // TO DO: stop reading after headers if method = HEAD
      int persistentConnection = 1;
      if (httpVer.equals("1.1")) {
         int chunked = 0;
         int contentLength = 0;
         for (int i = 0; i < responseHeaders.size(); i++) {
            String r = responseHeaders.get(i);
            if (r.indexOf("Content-Length:") != -1) {
               String cLength = r.substring(15).trim();
               try{
                  contentLength = Integer.parseInt(cLength);
               }
               catch (NumberFormatException e){
                   System.out.println("=== ERR: Failed to convert Content-Length to int");
                   System.exit(1);
               }
            } else if (r.indexOf("Transfer-Encoding:") != -1) {
               if (r.indexOf("chunked") != -1) {
                  chunked = 1;
               }
            } else if (r.indexOf("Connection:") != -1) {
               if (r.indexOf("close") != -1) {
                  persistentConnection = 0;
               }
            }
         }
         if (contentLength > 0) {
            // read message body as fixed number of bytes
            // (in a while loop the stream is blocked...)
            char responseCharArray[] = new char[contentLength];
            byte responseByteArray[] = new byte[contentLength];
            int reallyRead = 0;
            try {
//               reallyRead = is.read(responseByteArray, 0, contentLength);
//               reallyRead = br.read(responseCharArray, 0, contentLength);
               reallyRead = dis.read(responseByteArray, 0, contentLength);
            } catch(IOException e){
               e.printStackTrace();
            }
            System.out.println("reallyRead=" + reallyRead);
            rc = new String(responseByteArray);
//            rc = String.valueOf(responseCharArray);
         } else if (chunked != 0) {
            // determine chunk length, then read chunk
            int chunkLength = -1;
            while (chunkLength != 0) {
               try{
                  responseLine = br.readLine();
               }catch(IOException e){
                  e.printStackTrace();
               }
//               System.out.println("<== Response line:\n" + responseLine);
               String[] responseLineParts = responseLine.split(";");
               String chunkLen = responseLineParts[0];
               System.out.println("=== chunkLen (hex)=" + chunkLen);
               try{
                   chunkLength = Integer.parseInt(chunkLen, 16);
                   System.out.println("=== chunkLength (dec)=" + chunkLength);
               }
               catch (NumberFormatException e){
                   System.out.println("=== ERR: Failed to convert chunkLen to int");
                   // stop reading
                   break;
               }
               int toBeRead = chunkLength;
               while (toBeRead > 0) {
                  System.out.println("toBeRead=" + toBeRead);
                  char responseCharArray[] = new char[chunkLength];
                  int reallyRead = 0;
                  try{
                     reallyRead = br.read(responseCharArray, 0, toBeRead);
                     System.out.println("reallyRead=" + reallyRead);
                  }catch(IOException e){
                     e.printStackTrace();
                  }
                  rc = rc + String.valueOf(responseCharArray);
                  toBeRead = toBeRead - reallyRead;
               }
               // read CRLF characters after chunk?!
               // XXX check actual received characters...
               try{
                  br.read();
                  br.read();
               }catch(IOException e){
                  e.printStackTrace();
               }
            }
         }
      // TO DO: parse more HTTP response headers
      }

      if (httpVer.equals("1.0") || (httpVer.equals("1.1") && persistentConnection == 0)) {
         // just get the body using readline until the end
         rc = "";
         ArrayList<String> responseContentLines = new ArrayList<String>();
         responseLine = "";
         while(responseLine != null) {
             try{
                responseLine = br.readLine();
             }catch(IOException e){
                e.printStackTrace();
             }
//             System.out.println("<== Response line:\n" + responseLine);
             responseContentLines.add(responseLine);
         }
         for (int i = 0; i < responseContentLines.size(); i++) {
            String r = responseContentLines.get(i);
            rc = rc + '\n' + r;
         }
      }

      // print response (headers only)
      System.out.println("<== Response headers from server:");
      for (int i = 0; i < responseHeaders.size(); i++) {
         System.out.println(responseHeaders.get(i));
      }
      
      return rc;
   }
} // end class
