package okhttp3.sample;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import javax.net.SocketFactory;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class OkDocker {
  // curl --unix-socket /var/run/docker.sock "http://socket/_ping"
  // curl --unix-socket /var/run/docker.sock "http://socket/version"
  private static final String ENDPOINT = "unix://%2Fvar%2Frun%2Fdocker.sock/_ping";
//  private static final Moshi MOSHI = new Moshi.Builder().build();
//  private static final JsonAdapter<List<Contributor>> CONTRIBUTORS_JSON_ADAPTER = MOSHI.adapter(
//      Types.newParameterizedType(List.class, Contributor.class));

  public static void main(String... args) throws Exception {
    OkHttpClient client = new OkHttpClient.Builder().socketFactory(new UnixSocketFactory()).build();

    // Create request for remote resource.
    Request request = new Request.Builder()
        .url("http://localhost/unix--var-run-docker.sock/_ping")
        .build();

//    Request request = new Request.Builder()
//        .url(new HttpUrl.Builder()
//            .scheme("http")
//            .host("unix--var-run-docker.sock")
////            .host("unix%3A%2F%2F%2Fvar%2Frun%2Fdocker.sock")
//            .addPathSegment("_ping").build())
//        .build();

    // Execute the request and retrieve the response.
    Response response = client.newCall(request).execute();

    // Deserialize HTTP response to concrete type.
    ResponseBody body = response.body();
    System.out.println(body.string());
  }

  private OkDocker() {
    // No instances.
  }

  private static class UnixSocketFactory extends SocketFactory {
    @Override
    public Socket createSocket() throws IOException {
      return newSocket();
    }

    @Override
    public Socket createSocket(String s, int i) throws IOException, UnknownHostException {
      System.err.println(".. " + s + "/" + i);
      return newSocket();
    }

    @Override
    public Socket createSocket(String s, int i, InetAddress inetAddress, int i1) throws IOException, UnknownHostException {
      System.err.println(".. " + s + "/" + i);
      return newSocket();
    }

    @Override
    public Socket createSocket(InetAddress inetAddress, int i) throws IOException {
      System.err.println(".. " + inetAddress + "/" + i);
      return newSocket();
    }

    @Override
    public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1, int i1) throws IOException {
      System.err.println(".. " + inetAddress + "/" + i);
      return newSocket();
    }

    private Socket newSocket() throws IOException {
      String host = "/var/run/docker.sock";
      System.out.println("connect via '" + host + "'...");

      File socketFile = new File(host);
      System.out.println("unix socket exists " + socketFile.exists());
      System.out.println("unix socket canRead: " + socketFile.canRead());
      System.out.println("unix socket canWrite: " + socketFile.canWrite());

      AFUNIXSocket socket = AFUNIXSocket.newInstance();
      try {
        Thread.sleep(500);
      }
      catch (InterruptedException ignore) {
      }
      socket.connect(new AFUNIXSocketAddress(socketFile), 0);
      socket.setSoTimeout(0);
      return socket;
    }
  }
}
