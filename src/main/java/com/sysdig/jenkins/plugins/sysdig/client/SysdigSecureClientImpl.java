package com.sysdig.jenkins.plugins.sysdig.client;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class SysdigSecureClientImpl implements SysdigSecureClient {
  private static final String DEFAULT_API_URL = "https://secure.sysdig.com/";

  private final String token;
  private final String apiURL;
  private boolean verifySSL;

  private SysdigSecureClientImpl(String token, String apiURL) {
    this.token = token;
    this.apiURL = apiURL.replaceAll("/+$", "");
  }

  public static SysdigSecureClient newClient(String token, String apiURL) {
    SysdigSecureClientImpl client = new SysdigSecureClientImpl(token, apiURL);
    client.verifySSL = true;
    return client;
  }

  public static SysdigSecureClient newInsecureClient(String token, String apiURL) {
    SysdigSecureClientImpl client = new SysdigSecureClientImpl(token, apiURL);
    client.verifySSL = false;
    return client;
  }


  @Override
  public ImageScanningSubmission submitImageForScanning(String tag, String dockerFile) throws ImageScanningException {
    String imagesUrl = String.format("%s/images", apiURL);

    JSONObject jsonBody = new JSONObject();
    jsonBody.put("tag", tag);
    if (null != dockerFile) {
      jsonBody.put("dockerfile", dockerFile);
    }

    HttpClientContext context = makeHttpClientContext(token);

    try (CloseableHttpClient httpclient = makeHttpClient(verifySSL)) {
      String body = jsonBody.toString();

      HttpPost httppost = new HttpPost(imagesUrl);
      httppost.addHeader("Content-Type", "application/json");
      httppost.setEntity(new StringEntity(body));

      try (CloseableHttpResponse response = httpclient.execute(httppost, context)) {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200) {
          String serverMessage = EntityUtils.toString(response.getEntity());
          throw new ImageScanningException(String.format("sysdig-secure-engine add image failed. URL: %s, status: %s, error: %s", imagesUrl, response.getStatusLine(), serverMessage));
        }

        // Read the response body.
        String responseBody = EntityUtils.toString(response.getEntity());
        String imageDigest = JSONObject.fromObject(JSONArray.fromObject(responseBody).get(0)).getString("imageDigest");
        return new ImageScanningSubmission(tag, imageDigest);
      }
    } catch (Exception e) {
      throw new ImageScanningException(e);
    }
  }

  @Override
  public ImageScanningResult retrieveImageScanningResults(String tag, String dockerFile) throws ImageScanningException {
    return null;
  }


  private static CloseableHttpClient makeHttpClient(boolean verify) {
    CloseableHttpClient httpclient = null;
    if (verify) {
      httpclient = HttpClients.createDefault();
    } else {
      //SSLContextBuilder builder;

      //SSLConnectionSocketFactory sslsf=null;

      try {
        SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(),
          SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).build();
      } catch (Exception e) {
        System.out.println(e.toString());
      }
    }
    return (httpclient);
  }

  private static HttpClientContext makeHttpClientContext(String token) {
    CredentialsProvider credsProvider = new BasicCredentialsProvider();
    credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("", token));
    HttpClientContext context = HttpClientContext.create();
    context.setCredentialsProvider(credsProvider);
    return context;
  }
}
