import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import controllers.StartChat;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;
import models.ChatList;
import models.ChatRoom;
import models.Message;
import models.SqLite;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;



@TestMethodOrder(OrderAnnotation.class) 
public class StartChatTest {

  /**
  * Runs only once before the testing starts.
  */
  @BeforeAll
  public static void init() {

    System.out.println("Before All");
  }

  /**
  * This method starts a new game before every test run. It will run every time before a test.
  */
  @BeforeEach
  public void startAllChats() {
    // Test if server is running. You need to have an endpoint /
    // If you do not wish to have this end point, it is okay to not have anything in this method.
    // Create HTTP request and get response
    StartChat.main(null);
    HttpResponse<String> response = Unirest.get("http://localhost:8080/chatrooms/").asString();
    assertEquals(200, response.getStatus());
    
    SqLite db = StartChat.getDb();
    
    String sessionId = db.getLatestSession();
    
    db.insertUserWithSession("testing1", sessionId);
    db.commit();
    
    System.out.println("Before Each");
  }

  /**
  * This is a test case to evaluate the chatrooms endpoint.
  */
  @Test
  @Order(1)
  public void chatroomsTest() {

    
    HttpResponse<String> response = Unirest.get("http://localhost:8080/chatrooms/").asString();
    
    //assertEquals(200, response.getStatus());

    // --------------------------- JSONObject Parsing ----------------------------------

    System.out.println("/chatrooms Response: " + response.getBody());

    // Parse the response to JSON object
    JSONObject jsonObject = new JSONObject(response.getBody());

    // ---------------------------- GSON Parsing -------------------------

    // GSON use to parse data to object
    Gson gson = new Gson();
    ChatList chatlist = gson.fromJson(jsonObject.toString(), ChatList.class);
    
    // Check if player type is correct
    assertEquals(6, chatlist.size());
  
    System.out.println("Test /chatrooms");
  }

  /**
  * This is a test case to evaluate the chatrooms endpoint with restart.
  */
  
  @Test
  @Order(2)
  public void chatroomsRestartTest() {
    StartChat.stop();
    StartChat.main(null);

    // Create HTTP request and get response
    HttpResponse<String> response = Unirest.get("http://localhost:8080/chatrooms/").asString();
    assertEquals(200, response.getStatus());
    
    // sessionId is new after restarting, need to log again
    SqLite db = StartChat.getDb();
    String sessionId = db.getLatestSession();
    db.insertUserWithSession("testing2", sessionId);
    db.commit();
    
    response = Unirest.get("http://localhost:8080/chatrooms/").asString();

    assertEquals(200, response.getStatus());

    // --------------------------- JSONObject Parsing ----------------------------------

    System.out.println("/chatrooms Response: " + response.getBody());

    // Parse the response to JSON object
    JSONObject jsonObject = new JSONObject(response.getBody());

    // ---------------------------- GSON Parsing -------------------------

    // GSON use to parse data to object
    Gson gson = new Gson();
    ChatList chatlist = gson.fromJson(jsonObject.toString(), ChatList.class);
    
    // Check if player type is correct
    assertEquals(6, chatlist.size());
  
    System.out.println("Test /chatrooms");
  }

  /**
  * This is a test case to evaluate the joinroom endpoint.
  */
  @Test
  @Order(3)
  public void joinRoomTest() {

    HttpResponse<String> response = Unirest.post("http://localhost:8080/joinroom/blues/").body("username=ben").asString();
    response = Unirest.get("http://localhost:8080/blues/?user=ben").asString();

    assertEquals(200, response.getStatus());

    // --------------------------- JSONObject Parsing ----------------------------------

    System.out.println("/[chatroom]/[user] Response: " + response.getBody());

    // Parse the response to JSON object
    JSONObject jsonObject = new JSONObject(response.getBody());

    // ---------------------------- GSON Parsing -------------------------

    // GSON use to parse data to object
    Gson gson = new Gson();
    ChatRoom chatroom = gson.fromJson(jsonObject.toString(), ChatRoom.class);
    
    // Check if player type is correct
    assertEquals(1, chatroom.getParticipant().size());
  
    System.out.println("Test join chatroom");
  }

  /**
  * This is a test case to evaluate the joinroom endpoint.
  */
  @Test
  @Order(4)
  public void rejoinRoomTest() {

    // Create HTTP request and get response
    HttpResponse<String> response = Unirest.post("http://localhost:8080/joinroom/jazz/").body("username=ben").asString();
    response = Unirest.post("http://localhost:8080/joinroom/jazz/").body("username=mary").asString();
    response = Unirest.post("http://localhost:8080/joinroom/jazz/").body("username=ben").asString();
    response = Unirest.get("http://localhost:8080/jazz/?user=ben").asString();

    assertEquals(200, response.getStatus());

    // --------------------------- JSONObject Parsing ----------------------------------

    System.out.println("/joinroom Response: " + response.getBody());

    // Parse the response to JSON object
    JSONObject jsonObject = new JSONObject(response.getBody());

    // ---------------------------- GSON Parsing -------------------------

    // GSON use to parse data to object
    Gson gson = new Gson();
    ChatRoom chatroom = gson.fromJson(jsonObject.toString(), ChatRoom.class);
    
    // Check if player type is correct
    System.out.println(chatroom.getParticipant().get("ben"));
    System.out.println(chatroom.getParticipant().get("mary"));
    assertEquals(2, chatroom.getParticipant().size());
  
    System.out.println("Test rejoining chatroom");
  }

  /**
  * This is a test case to evaluate the joinroom endpoint.
  */
  @Test
  @Order(5)
  public void invalidJoinRoomTest() {

    // Create HTTP request and get response
    HttpResponse<String> response = Unirest.post("http://localhost:8080/joinroom/metal").body("username=ben").asString();
    
    assertEquals("Invalid Room", response.getBody());
  
    System.out.println("Test invalid join chatroom");
  }

  /**
  * This is a test case to evaluate the chatroom/genre endpoint.
  */
  @Test
  @Order(6)
  public void invalidChatroomGenreTest() {

    // Create HTTP request and get response
    HttpResponse<String> response = Unirest.get("http://localhost:8080/metal/?user=ben").asString();

    assertEquals(200, response.getStatus());
    assertEquals("Invalid Room", response.getBody());
  
    System.out.println("Test invalid chatroom genre");
  }
 
  /**
  * This is a test case to evaluate the leaveroom endpoint.
  */
  @Test
  @Order(7)
  public void leaveRoomTest() {

    // Create HTTP request and get response
    HttpResponse<String> response = Unirest.post("http://localhost:8080/joinroom/country").body("username=sean").asString();
    response = Unirest.get("http://localhost:8080/country/?user=sean&skip_auth_testing=true").asString();  
    assertEquals(200, response.getStatus());

    JSONObject jsonObject = new JSONObject(response.getBody());
    Gson gson = new Gson();
    ChatRoom chatroom = gson.fromJson(jsonObject.toString(), ChatRoom.class);
    
    // Check if user is present after joinroom
    assertEquals(true, chatroom.getParticipant().containsKey("sean"));
	
    response = Unirest.delete("http://localhost:8080/leaveroom/country").body("username=sean").asString();
    response = Unirest.get("http://localhost:8080/country/?user=sean").asString(); 
    assertEquals(200, response.getStatus());
    System.out.println("/leaveroom/[user] Response: " + response.getBody());

    jsonObject = new JSONObject(response.getBody());
    chatroom = gson.fromJson(jsonObject.toString(), ChatRoom.class);
    
    // Check if user is not present after leaveroom
    assertEquals(false, chatroom.getParticipant().containsKey("sean"));
  
    System.out.println("Test leave chatroom");
  }

  /**
  * This is a test case to evaluate the leaveroom endpoint.
  */
  @Test
  @Order(8)
  public void invalidLeaveRoomTest() {

    // Create HTTP request and get response
    HttpResponse<String> response = Unirest.delete("http://localhost:8080/leaveroom/pop/").body("username=taylor").asString();
	
    assertEquals(200, response.getStatus());
    assertEquals("You are not in the room", response.getBody());
  
    System.out.println("Test invalid leave chatroom");
  }

  /**
  * This is a test case to evaluate the process_auth endpoint.
  */
  @Test
  @Order(9)
  public void processAuthTest() {

    // Create HTTP request and get response
    HttpResponse<String> response = Unirest.get("http://localhost:8080/process_auth?code=123").asString();
	
    assertEquals(200, response.getStatus());
	
    System.out.println("/process-auth Response: " + response.getBody());
	
    // Parse the response to JSON object
    JSONObject jsonObject = new JSONObject(response.getBody());

    // ---------------------------- GSON Parsing -------------------------

    // GSON use to parse data to object
    Gson gson = new Gson();
    ChatList chatlist = gson.fromJson(jsonObject.toString(), ChatList.class);
    
    // Check if player type is correct
    assertEquals(6, chatlist.size());
	
    StartChat.stop();
    StartChat.main(null);
    
    response = Unirest.get("http://localhost:8080/process_auth?code=123").asString();
    
    System.out.println("/process-auth Response: " + response.getBody());
    
    assertEquals(200, response.getStatus());
    
    response = Unirest.get("http://localhost:8080/process_auth").asString();
    
    System.out.println("/process-auth Response: " + response.getBody());
    
    assertEquals(200, response.getStatus());
  
    System.out.println("Test process authorization");
    
    System.out.println("/process-auth Response: " + response.getBody());
  }
  
  /**
  * This is a test case to evaluate the before endpoint.
  */
  @Test
  @Order(10)
  public void LoginRedirectionTest() {

    // reset sessionId to enable redirection
    StartChat.stop();
    StartChat.main(null);
    
    HttpResponse<String> response = Unirest.get("http://localhost:8080/").asString();
    assertEquals(200, response.getStatus());
	
    System.out.println("/ Response: " + response.getBody());
    System.out.println("Test Login Redirection");
  }

  /**
  * This is a test case to evaluate the / endpoint.
  */
  @Test
  @Order(11)
  public void frontPageTest() {

    // Create HTTP request and get response

    HttpResponse<String> response = Unirest.get("http://localhost:8080/").asString();
    
    assertEquals(200, response.getStatus());
    
    System.out.println("/chatrooms Response: " + response.getBody());

    // Parse the response to JSON object
    JSONObject jsonObject = new JSONObject(response.getBody());

    // ---------------------------- GSON Parsing -------------------------

    // GSON use to parse data to object
    Gson gson = new Gson();
    ChatList chatlist = gson.fromJson(jsonObject.toString(), ChatList.class);
    
    // Check if player type is correct
    assertEquals(6, chatlist.size());
  
    System.out.println("Test front page");
  }

  /**
  * This is a test case to evaluate initializeChatlist function.
  */
  @Test
  @Order(12)
  public void initializeChatListTest() {

    SqLite db = StartChat.getDb();
    db.clear();
    db.commit();
    ChatList chatlist = new ChatList();
    assertEquals(0, chatlist.size());
    StartChat.setChatlist(chatlist);
    StartChat.initializeChatlist();
    chatlist = StartChat.getChatlist();
    
    assertEquals(6, chatlist.size());
  
    System.out.println("Test initializeChatlist function");
  }
  
  @Test
  @Order(13)
  public void sendMessageTest() {
    HttpResponse<String> response = Unirest.post("http://localhost:8080/joinroom/blues/").body("username=ben").asString();
    response = Unirest.get("http://localhost:8080/blues/?user=ben").asString();
    assertEquals(200, response.getStatus());

    HttpResponse<String> response1 = Unirest.post("http://localhost:8080/send/ben/").body("text=hello").asString();
    response1 = Unirest.get("http://localhost:8080/blues/?user=ben").asString();
    System.out.println("/[chatroom]/[user] Response: " + response1.getBody());
    // Parse the response to JSON object
    JSONObject jsonObject = new JSONObject(response1.getBody());

    // GSON use to parse data to object
    Gson gson = new Gson();
    ChatRoom chatroom = gson.fromJson(jsonObject.toString(), ChatRoom.class);
    List<Message> msgList = chatroom.getChat();
    assertEquals(1, msgList.size());
    Message msg = msgList.get(0);
    assertEquals("ben", msg.getUsername());
    assertEquals("hello", msg.getMessage());
  }
  
  @Test
  @Order(14)
  public void webSocketTest() {
	
    WebSocketClient client = new WebSocketClient();
    SimpleWebSocket socket = new SimpleWebSocket();
    try {
      client.start();
      URI uri = new URI("ws://localhost:8080/gameboard");
      //ClientUpgradeRequest request = new ClientUpgradeRequest();
      Future<Session> future = client.connect(socket, uri);
      System.out.printf("Connecting to : %s%n", uri);
      Session session = future.get();
      session.getRemote().sendString("hi");
      session.close();

      // wait for closed socket connection.
      socket.awaitClose(5, TimeUnit.SECONDS);
    } catch (Throwable t) {
      t.printStackTrace();
    } finally {
      try {
        client.stop();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
  
  /**
  * This will run every time after a test has finished.
  */
  @AfterEach
  public void finishChat() {
    SqLite db = StartChat.getDb();
    db.clear();
    db.commit();
    StartChat.stop();
    System.out.println("After Each");
  }

  /**
   * This method runs only once after all the test cases have been executed.
   */
  @AfterAll
  public static void close() {
    System.out.println("After All");
  }
}
