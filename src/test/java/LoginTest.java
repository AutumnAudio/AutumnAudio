import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

import models.Login;

public class LoginTest {
  String refreshToken = "AQCDFdBbzda1H17Vcv3FMIFR5hvFEZjrNGhkCnjDXpDHHohCvp_vX2cvXyg0XpJ2SC69M9A4EOMCUVCgBjcUYMJYZZsbcUysqzf-kUrDev0LE0Wxi283o2l3k13JdXUiShc";

  @Test
  public void getSpotifyAuthUrlTest() {
    String url = Login.getSpotifyAuthUrl();
    assertEquals("https://accounts.spotify.com/authorize?response_type=code&client_id=17b185367b7d45a8b8cb068eda7787cf&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fprocess_auth&scope=user-read-email+user-read-recently-played+user-read-currently-playing+playlist-modify-public+playlist-modify-private", url);
  }

  @Test
  public void getSpotifyTokenFromCodeTest() {
    Map<String, String> map = Login.getSpotifyTokenFromCode("123");
    assertEquals(2, map.size());
  }

  @Test
  public void refreshSpotifyTokenTest() {
    String token = Login.refreshSpotifyToken(refreshToken);
    assertNotNull(token);
  }

  @Test
  public void getEmailFromSpotifyTokenTest() {
    String token = Login.refreshSpotifyToken(refreshToken);
    assertEquals("cherrychu_120@hotmail.com", Login.getEmailFromSpotifyToken(token));
  }
}
