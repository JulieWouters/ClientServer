import java.io.*;
import java.net.*;

import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.Date;
// added to parse response
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;

class HTTPServer
{
   public static void main(String argv[]) throws Exception
   {
//      Socket sSocket = new Socket("localhost", 6789);
      ServerSocket sSocket = new ServerSocket(6789);
      while (true) {
         Socket connectionSocket = sSocket.accept();
         if (connectionSocket != null) {
            Handler h = new Handler (connectionSocket);
            Thread thread = new Thread(h);
            thread.start();
         }
      }
   }
}

class Handler implements Runnable
{
   Socket socket;
   public Handler(Socket socket)
       { this.socket = socket; }

   @Override
   public void run()
   {
      boolean run = true;
      while (run) {
         BufferedReader inFromClient = null;
         DataOutputStream outToClient = null;

         try {
           inFromClient = new BufferedReader(new InputStreamReader(socket.getInputStream()));
         } catch (IOException e) {
         }
         try {
           outToClient = new DataOutputStream(socket.getOutputStream());
         } catch (IOException e) {
         }
   
         String clientRequest = "";
         // read first line of request
         try {
            clientRequest = inFromClient.readLine();
         } catch (IOException e) {
            // stop this thread
            System.out.println("ERR: failed to read from client");
            run = false;
            continue;
//            System.exit(1);
         }
         System.out.println("Received: " + clientRequest);

         if (clientRequest == null) {
            // stop this thread
            System.out.println("ERR: connection closed by client");
            run = false;
            continue;
//            System.exit(1);
         }

         String[] requestParts = clientRequest.split(" ");
         // validate command line arguments
         if (requestParts.length != 3) {
             writeResponse(500, "HTTP/1.1", "", outToClient);
             continue;
         }
   
         String httpMethod = requestParts[0];
         String URI = requestParts[1];
         String httpVersion = requestParts[2];

         // read Request headers
         String requestHeaders = "";
         try {
            while (clientRequest != null && ! clientRequest.equals("")) {
               clientRequest = inFromClient.readLine();
               requestHeaders += "\r\n";
               requestHeaders += clientRequest;
            }
         } catch (IOException e) {
         }

         System.out.println("=== Received request headers from client:\r\n" + requestHeaders + "\r\n");

         // TO DO
         // parse request headers
         // Host:  (required for HTTP 1.1)
         // Connection: close    if yes server needs to terminate connection

 
         // validate HTTP command
         if (validateCommand(httpMethod) != 0) {
             writeResponse(500, httpVersion, "", outToClient);
             continue;
         }
   
         // validate HTTP version
         if (validateVersion(httpVersion) != 0) {
             writeResponse(500, httpVersion, "", outToClient);
             continue;
         }

         String extraArg = "";
         if (httpMethod.equals("PUT") || httpMethod.equals("POST")) {
// TO DO later
//            System.out.println(httpMethod + " method requires an argument:");
//            extraArg = inFromUser.readLine();
//            System.out.println("=== Extra argument:\n" + extraArg);
         }

         // TO DO: construct response
         int statuscode = 404;
         String fileContent = "";
         String fileName = URI;
         if (fileName.substring(0,1).equals("/")) {
            fileName = URI.substring(1);
         }
         System.out.println("Filename = " + fileName);
         File file = new File(fileName);
         try {
            System.out.println("=== Reading contents of file: " + fileName);
            FileReader r = new FileReader(fileName);
            char[] chars = new char[(int) file.length()];
            r.read(chars);
            fileContent = new String(chars);
            r.close();
            System.out.println("=== File content: " + fileContent);
            System.out.println("=== Length of file: " + fileContent.length());
            statuscode = 200;
         } catch(IOException e) {
            statuscode = 404;
         }

         String headersContent = "";
         if (httpVersion.equals("HTTP/1.1")) {
            headersContent += "Content-Type: text/html\r\n";
            int contentLength = 0;
            if (statuscode == 200) {
               contentLength = fileContent.length();
            } else if (statuscode == 404) {
               fileContent = "404 Not found\r\n";
               contentLength = fileContent.length();
            }
            headersContent += "Content-Length: " + contentLength + "\r\n";
            //String date = new SimpleDateFormat("YYYY-MM-dd").format(new Date());
            String date = "TO DO";
            headersContent += "Date: " + date + "\r\n";
         }

         String headersPlusContent = headersContent + "\r\n" + fileContent;

         // write reponse to client
         writeResponse(statuscode, httpVersion, headersPlusContent, outToClient);

      }
   }

   public static int validateVersion(String ver) {
      // validate HTTP version
      if (ver.equals("HTTP/1.0")) {
          System.out.println("=== OK HTTP/1.0 request");
      } else if (ver.equals("HTTP/1.1")) {
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

   public static void writeResponse(int code, String ver, String content, DataOutputStream dos) {
      // write response to client
      String response = ver + " " + code;
      if (code == 500) {
         response = response + " Server error\r\n";
         System.out.println("=== ERR: Invalid request");
      } else if (code == 400) {
         response = response + " Bad request\r\n";
         System.out.println("=== ERR: Invalid request");
      } else if (code == 404) {
         response = response + " Not found" + "\r\n";
         response = response + content + "\r\n";
      } else if (code == 200) {
         response = response + " OK\n";
         response = response + content + "\r\n";
      }
      try {
         System.out.println("sending data to client:\r\n" + response);
         dos.writeBytes(response);
      } catch (IOException e) {
      }
   }

}
