package controllers;

import com.google.gson.Gson;
import io.javalin.Javalin;
import java.io.IOException;
import java.sql.Time;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import models.ChatList;
import models.ChatRoom;
import models.Genre;
import models.Login;
import models.Message;
import models.Song;
import models.SpotifyAPI;
import models.SqLite;
import models.User;
import org.eclipse.jetty.websocket.api.Session;


public final class StartChat {

  /**
   * Private constructor that prevents utility class instantiation.
   */
  private StartChat() {
  }

  /**
   * Static method that called private constructor for testing.
   * @return StartChat object
   */
  public static StartChat make() {
    return new StartChat();
  }

  /**
   * Set listening port.
   */
  private static final int PORT_NUMBER = 8080;

  /**
   * Set time interval (seconds) for refreshChatList().
   */
  private static final int INTERVAL = 30;

  /**
   * Create Javalin instance.
   */
  private static Javalin app;

  /**
   * Create database instance.
   */
  private static SqLite db = new SqLite();

  /**
   * Create SpotifyAPI instance.
   */
  private static SpotifyAPI api = new SpotifyAPI();

  /**
   * get current database.
   * @return database SqLite
   */
  public static SqLite getDb() {
    return db;
  }

  /**
   * set current database.
   * @param database SqLite
   */
  public static void setDb(final SqLite database) {
    db = database;
  }

  /**
   * Create ChatList.
   */
  private static ChatList chatlist = new ChatList();

  /**
   * get chatlist.
   * @return chatlist ChatList
   */
  public static ChatList getChatlist() {
    return chatlist;
  }

  /**
   * set chatlist.
   * @param list ChatList
   */
  public static void setChatlist(final ChatList list) {
    chatlist = list;
  }

  /**
   * set api
   * @param api SpotifyAPI
   */
  public static void setApi(final SpotifyAPI newApi) {
    api = newApi;
  }

  /**
   * Initialize Chatrooms.
   */
  public static void initializeChatlist() {
    Map<String, ChatRoom> map = new HashMap<>();
    Genre[] genres = Genre.class.getEnumConstants();
    for (Genre genre : genres) {
      ChatRoom chatroom = new ChatRoom();
      chatroom.setParticipant(new HashMap<String, User>());
      chatroom.setChat(new ArrayList<Message>());
      chatroom.setPlaylist(new ArrayList<Song>());
      chatroom.setGenre(genre);
      chatroom.setLink("/joinroom/" + genre.getGenre());
      map.put(genre.getGenre(), chatroom);
      db.insertChatRoom(chatroom.getGenre(), chatroom.getLink(),
                                "playlist-" + genre.getGenre());
      db.commit();
    }
    chatlist.setChatrooms(map);
  }

  /**
   * Start a thread that read DB periodically.
   */
  static ScheduledFuture<?> RefreshDataInterval;
  private static void refreshSongDataRepeatly() {

    if(RefreshDataInterval != null && !RefreshDataInterval.isCancelled()) {
      return;
    }

    ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1);
    RefreshDataInterval = exec.scheduleAtFixedRate(new Runnable() {
      
      public void run() {
        System.out.println("scheduled run");
        SqLite db2 = new SqLite();
        db2.connect();
        ChatList chatListData = db2.update();
        chatListData.refreshChatList();
        for (ChatRoom chatroom : chatListData.getChatrooms().values()) {
          String genre = chatroom.getGenre().getGenre();
          sendChatRoomToAllParticipants(genre,
                  new Gson().toJson(chatroom));
        }
        db2.close();
      }
    }, 0, INTERVAL, TimeUnit.SECONDS);
  }

  /** Main method of the application.
   * @param args Command line arguments
   */
  public static void main(final String[] args) {
    db.start();
    chatlist = db.update();
    db.commit();

    if (chatlist.size() == 0) {
      initializeChatlist();
    }

    app = Javalin.create(config -> {
      config.addStaticFiles("/public");
    }).start(PORT_NUMBER);

    app.get("/auth", ctx -> {
        String sessionId = (String) ctx.sessionAttribute("sessionId");
        //if /auth and code present skip rest
        if (ctx.url().contains("/process_auth")
               && ctx.queryParam("code") != null && sessionId != null) {
          return;
        }
        if (sessionId == null) {
          sessionId = UUID.randomUUID().toString();
          ctx.sessionAttribute("sessionId", sessionId);
        }
        if (db.getUserBySessionId(sessionId).getUsername() == null) {
          db.insertSession("" + System.currentTimeMillis(), sessionId);
          db.commit();
          System.out.println(Login.getSpotifyAuthUrl());
          ctx.result("{\"error\":\"not authenticated\",\"auth_url\":\"" + Login.getSpotifyAuthUrl() + "\"}");
        } else {
          ctx.result("{}");
        }
    });
    
    //authentication
    app.before(ctx -> {
      String sessionId = (String) ctx.sessionAttribute("sessionId");
      //if /auth and code present skip rest
      if (ctx.url().contains("/process_auth")
             && ctx.queryParam("code") != null && sessionId != null) {
        return;
      }
      if (sessionId == null) {
        sessionId = UUID.randomUUID().toString();
        ctx.sessionAttribute("sessionId", sessionId);
      }
      if (db.getUserBySessionId(sessionId).getUsername() == null) {
        db.insertSession("" + System.currentTimeMillis(), sessionId);
        db.commit();
        
        ctx.result("{\"error\":\"not authenticated\",\"auth_url\":\"" + Login.getSpotifyAuthUrl() + "\"}");
      }
    });

    //handle spotify authentication
    app.get("/process_auth", ctx -> {
      String sessionId = (String) ctx.sessionAttribute("sessionId");
      Map<String, String> response = api.getSpotifyTokenFromCode(
                                     ctx.queryParam("code"));
      User user = new User(response.get("access_token"), db);
      user.setSpotifyRefreshTokenDb(response.get("refresh_token"));
      user.setSessionIdDb(sessionId);
      
      ctx.redirect("http://localhost:3000"); //TODO: change to HOME constant (runtime arg or external config?)
    });

    // Front pages
    app.get("/", ctx -> {
      ctx.redirect("/home");
    });
    
    app.get("/home", ctx -> {
      ctx.redirect("index.html");
    });

    app.get("/lobby", ctx -> {
      ctx.redirect("index.html?place=lobby");
    });

    // Send Chatroom List
    app.get("/chatrooms", ctx -> {
      chatlist.setChatrooms(db.getAllChatRooms());
      ctx.result(new Gson().toJson(chatlist));
    });

    app.post("/joinroom/:genre", ctx -> {
      if (Genre.isValidGenre(ctx.pathParam("genre").toUpperCase())) {
        Genre genre = Genre.valueOf(ctx.pathParam("genre").toUpperCase());
        String username = db.getUserBySessionId(
                (String) ctx.sessionAttribute("sessionId")).getUsername();
        Map<String, User> participants =
              chatlist.getChatroomByGenre(genre).getParticipant();
        if (!participants.containsKey(username)) {
          User user = db.getUserByName(username);
          db.insertParticipant(genre, username, user.getSpotifyToken(),
              user.getSpotifyRefreshToken(), user.getSessionId());
          db.insertUserwithGenre(username, genre.getGenre());
          db.commit();
          chatlist = db.update();
          db.commit();
        }
        ChatRoom chatroom = chatlist.getChatroomByGenre(genre);
        sendChatRoomToAllParticipants(genre.getGenre(),
              new Gson().toJson(chatroom));
        ctx.result("success");
        
        refreshSongDataRepeatly();
      } else {
        ctx.result("Invalid Room");
      }
    });

    // user chatroom view
    app.get("/chatroom/:genre", ctx -> {
      if (Genre.isValidGenre(ctx.pathParam("genre").toUpperCase())) {
        Genre genre = Genre.valueOf(ctx.pathParam("genre").toUpperCase());
        ChatRoom chatroom = chatlist.getChatroomByGenre(genre);
        ctx.result(new Gson().toJson(chatroom));
      } else {
        ctx.result("Invalid Room");
      }
    });

    app.post("/send", ctx -> {
      String username = db.getUserBySessionId(
                (String) ctx.sessionAttribute("sessionId")).getUsername();
      String text = ctx.formParam("text");
      Message message = new Message();
      message.setUsername(username);
      message.setMessage(text);
      String genreStr = db.getGenreUser(username);
      if (genreStr != null) {
        Genre genre = Genre.valueOf(genreStr.toUpperCase());
        ChatRoom chatroom = chatlist.getChatroomByGenre(genre);
        chatroom.addMessage(message);
        sendChatMsgToAllParticipants(genre.getGenre(),
                new Gson().toJson(message));
        ctx.result("chat sent");
      } else {
        ctx.result("User not in any chatroom");
      }
    });

    app.post("/add", ctx -> {
      String uri = ctx.formParam("uri");
      String username = db.getUserBySessionId(
              (String) ctx.sessionAttribute("sessionId")).getUsername();
      User user = db.getUserByName(username);
      user.addToQueue(uri);
      ctx.result("song added to your queue");
    });
    
    app.post("/share", ctx -> {
      String username = db.getUserBySessionId(
                (String) ctx.sessionAttribute("sessionId")).getUsername();
      User sharer = db.getUserByName(username);
      sharer.refreshCurrentlyPlaying();
      if (sharer.getCurrentTrack() == null) {
        ctx.result("no song playing");
        return;
      }
      String genreStr = db.getGenreUser(username);
      if (genreStr == null) {
        ctx.result("User not in any chatroom");
        return;
      }
      Song song = new Song();
      song.setUsername(username);
      song.setName(sharer.getCurrentTrack().getName());
      song.setArtists(sharer.getCurrentTrack().getArtists());
      song.setUri(sharer.getCurrentTrack().getUri());
      
      Genre genre = Genre.valueOf(genreStr.toUpperCase());
      ChatRoom chatroom = chatlist.getChatroomByGenre(genre);
      // share song with participants
      for (User sharee : chatroom.getParticipant().values()) {
        if (!sharer.getUsername().equals(sharee.getUsername())) {
          sharee.addToQueue(song.getUri());
        }
      }
      // add song to group playlist
      chatroom.addSong(song);
      db.insertSong(username, Time.valueOf(LocalTime.now()),
              genre, new Gson().toJson(song));
      db.commit();
      // send message to chat
      Message message = new Message();
      message.setUsername(username);
      message.setMessage("I just shared " + song.getName()
              + " by " + song.getArtists()[0]);
      sendChatMsgToAllParticipants(genre.getGenre(),
              new Gson().toJson(message));
      ctx.result("song shared");
    });

    app.delete("/leaveroom/:genre", ctx -> {
      Genre genre = Genre.valueOf(ctx.pathParam("genre").toUpperCase());
      String username = db.getUserBySessionId(
              (String) ctx.sessionAttribute("sessionId")).getUsername();
      ChatRoom chatroom = chatlist.getChatroomByGenre(genre);
      if (chatroom.getParticipant().containsKey(username)) {
        db.removeUserGenre(username);
        db.removeParticipant(genre, username);
        db.commit();
        chatlist = db.update();
        db.commit();
        chatroom = chatlist.getChatroomByGenre(genre);
        sendChatRoomToAllParticipants(genre.getGenre(),
              new Gson().toJson(chatroom));
        ctx.result(new Gson().toJson(chatroom));
        
        if(chatlist.getTotalParticipants() == 0) {
          RefreshDataInterval.cancel(false);
        }
      } else {
        ctx.result("You are not in the room");
      }
    });

    // Web sockets - DO NOT DELETE or CHANGE
    app.ws("/chatroom", new UiWebSocket());
  }

  /**
   * Send chatroom to all participants.
   * @param genre String
   * @param chatRoomJson String
   */
  private static void sendChatRoomToAllParticipants(final String genre,
        final String chatRoomJson) {
    Queue<Session> sessions = UiWebSocket.getSessions();
    for (Session sessionPlayer : sessions) {
      try {
        sessionPlayer.getRemote().sendString(chatRoomJson);
      } catch (IOException e) {
        // Add logger here
      }
    }
  }

  private static void sendChatMsgToAllParticipants(
      final String genre, final String msg) {
    Queue<Session> sessions = UiWebSocket.getSessions();
    for (Session sessionPlayer : sessions) {
      try {
        sessionPlayer.getRemote().sendString(msg);
      } catch (IOException e) {
        // Add logger here
      }
    }
  }

  /**
   * Stop the application.
   */
  public static void stop() {
    db.close();
    app.stop();
  }
}
