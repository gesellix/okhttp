package okhttp3.sample;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class OkDocker {
  // curl --unix-socket /var/run/docker.sock "http://socket/_ping"
  // curl --unix-socket /var/run/docker.sock "http://socket/version"
  private static final String ENDPOINT = "unix://%2Fvar%2Frun%2Fdocker.sock/_ping";
//  private static final Moshi MOSHI = new Moshi.Builder().build();
//  private static final JsonAdapter<List<Contributor>> CONTRIBUTORS_JSON_ADAPTER = MOSHI.adapter(
//      Types.newParameterizedType(List.class, Contributor.class));

  public static void main(String... args) throws Exception {
    OkHttpClient client = new OkHttpClient();

    // Create request for remote resource.
    Request request = new Request.Builder()
        .url("http://unix--var-run-docker.sock/_ping")
        .build();

//    Request request = new Request.Builder()
//        .url(new HttpUrl.Builder()
//            .scheme("http")
//            .host("unix--var-run-docker.sock")
////            .host("unix%3A%2F%2F%2Fvar%2Frun%2Fdocker.sock")
//            .addPathSegment("_ping").build())
//        .build();

    // Execute the request and retrieve the response.
//    client.newBuilder().socketFactory()
    Response response = client.newCall(request).execute();

    // Deserialize HTTP response to concrete type.
    ResponseBody body = response.body();
    System.out.println(body.string());
  }

  private OkDocker() {
    // No instances.
  }
}
