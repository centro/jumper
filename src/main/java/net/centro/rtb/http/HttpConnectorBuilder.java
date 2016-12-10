package net.centro.rtb.http;

import org.apache.commons.validator.routines.UrlValidator;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.glassfish.jersey.client.spi.ConnectorProvider;
import org.glassfish.jersey.grizzly.connector.GrizzlyConnectorProvider;
import org.glassfish.jersey.jetty.connector.JettyConnectorProvider;
import org.glassfish.jersey.netty.connector.NettyConnectorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;


/**
 * This starting point for creating a new http connection.
 *
 * @author Ofir Gal
 * @version 1.0
 */
public class HttpConnectorBuilder {

    private static final Logger logger = LoggerFactory.getLogger(HttpConnectorBuilder.class);

    private UrlValidator urlValidator = new UrlValidator(UrlValidator.ALLOW_2_SLASHES + UrlValidator.ALLOW_LOCAL_URLS);

    private URI uri;
    private String url;
    private Object body;
    private MultivaluedMap<String, Object> reqProperties = new MultivaluedHashMap<>();
    private Http.HttpProtocol connType;
    private Http.HttpMethod httpMethod = null;
    private Http.SyncType syncType = Http.SyncType.SYNC;
    private InvocationCallback invocationCallback;
    private Path path = null;
    private boolean storeCookies = false;
    private ConnectorProvider connectorProvider = new HttpUrlConnectorProvider();
    private Http.ConnectorProvider connectorProviderEnum = Http.ConnectorProvider.HttpUrlConnector;
    private static SSLContext sslContext = getTrustAllSslContext();
    private boolean trustAllSSLContext = false;
    private Map<String, Object> clientProperties = new HashMap<>();
    private static Map<String, Object> clientPropertiesDefault;

    static {
        clientPropertiesDefault = new HashMap<>();
        clientPropertiesDefault.put(ClientProperties.SUPPRESS_HTTP_COMPLIANCE_VALIDATION, true);
        clientPropertiesDefault.put(ClientProperties.ASYNC_THREADPOOL_SIZE, 100);
        clientPropertiesDefault.put(ClientProperties.FOLLOW_REDIRECTS, true);
        clientPropertiesDefault.put(ClientProperties.CONNECT_TIMEOUT, 0);
    }

    /**
     * instantiating a new HttpConnectorBuilder object.
     */
    public static HttpConnectorBuilder newBuilder() { return new HttpConnectorBuilder();}

    /**
     * (optional) Set custom http connector. Default(Jersey) is HttpUrlConnection.
     * @param provider - Connector enum
     * @return - Builder Ojbect
     */
    public HttpConnectorBuilder setConnectorProvider (Http.ConnectorProvider provider) {
        switch (provider) {
            case Grizzly:
                connectorProvider = new GrizzlyConnectorProvider();
                connectorProviderEnum = Http.ConnectorProvider.Grizzly;
                break;
            case Apache:
                connectorProvider = new ApacheConnectorProvider();
                connectorProviderEnum = Http.ConnectorProvider.Apache;
                break;
            case Jetty:
                connectorProvider = new JettyConnectorProvider();
                connectorProviderEnum = Http.ConnectorProvider.Jetty;
                break;
            case Netty:
                connectorProvider = new NettyConnectorProvider();
                connectorProviderEnum = Http.ConnectorProvider.Netty;
                break;
            case HttpUrlConnector:
                connectorProvider = new HttpUrlConnectorProvider();
                connectorProviderEnum = Http.ConnectorProvider.HttpUrlConnector;
                break;
            default:
                connectorProvider = new HttpUrlConnectorProvider();
                connectorProviderEnum = Http.ConnectorProvider.HttpUrlConnector;
                break;
        }
        return this;
    }

    /**
     * @return The set connector provider enum
     */
    public Http.ConnectorProvider getConnectorProvider() { return connectorProviderEnum; }


    /**
     * @param urlString - url path we are to connect to. requires http:// OR https://
     * @return Builder object
     */

    /**
     * The simplest, most commonly used method to set the destination URL.
     * @param urlString (Ex. http://ofirgal.com)
     */
    public HttpConnectorBuilder url(String urlString) throws URISyntaxException {

        this.url = urlString;
        connType = getProtocol(url);
        uri = getURI(url);

        return this;
    }

    private URI getURI(String urlString) throws URISyntaxException {

        if (!urlValidator.isValid(urlString)) {
            if (urlString == null) { logger.error("URL is set to NULL");}
            throw new URISyntaxException(urlString,"Malformed URL");
        }
        return new URI(urlString);
    }

    /**
     * An optional way to set a url.
     * @param protocol HTTP/HTTPS
     * @param domain   Domain we are connecting to
     * @param port     On which port we are connecting on
     * @param path     url path appended to the end of domain and port
     * @return Builder
     */
    public HttpConnectorBuilder url(Http.HttpProtocol protocol, String domain, int port, String path) throws URISyntaxException {

        url(protocol.name().toLowerCase() + "://" + domain + ":" + port + path);
        return this;
    }

    /**
     * Sets the location to save the http response to as a file.
     */
    public HttpConnectorBuilder saveToFile(String saveDir, String fileName) {

        return saveToFile(Paths.get(saveDir + fileName));

    }
    /**
     * Sets the location to save the http response to as a file.
     */
    public HttpConnectorBuilder saveToFile(String filename) {

        return saveToFile(Paths.get(filename));
    }
    /**
     * Sets the location to save the http response to as a file.
     * @param path The location as a java.nio.file.Path object.
     */
    public HttpConnectorBuilder saveToFile(Path path) {

        if (path.toFile().isDirectory()) {
            logger.error(path + " is a directory! Expecting a file. Data will not be saved to a file.");
            return this;
        }
        this.path = path.normalize();
        logger.info("Save response to: {}", path.toString());

        return this;
    }

    /**
     * (optional) Default set to true
     * @param redirect set if to follow redirects or not
     * @return Builder
     */
    public HttpConnectorBuilder follow_redirect(boolean redirect) {

        clientProperties.put(ClientProperties.FOLLOW_REDIRECTS, redirect);
        return this;
    }

    /**
     * (optional) Set the timeout to establish a connection to the server in milliseconds . Default is set to infinity.
     * @param timeout value int
     * @return Builder
     */
    public HttpConnectorBuilder setConnectTimeout(int timeout) {

        clientProperties.put(ClientProperties.CONNECT_TIMEOUT, timeout);
        return this;
    }

    /**
     * (optional) Set the timeout to read the response from the server in milliseconds . Default is set to infinity.
     * @param timeout value int
     * @return Builder
     */
    public HttpConnectorBuilder setReadTimeout(int timeout) {

        clientProperties.put(ClientProperties.READ_TIMEOUT, timeout);
        return this;
    }

    /**
     * @param req Set request type (GET, POST, PUT, etc.)
     * @return Builder
     */
    public HttpConnectorBuilder setMethod(Http.HttpMethod req) {
        if (req == Http.HttpMethod.PATCH) {
            addHeaderProperty("X-HTTP-Method-Override", "PATCH");
            httpMethod = Http.HttpMethod.POST;
        }else {
            httpMethod = req;
        }
        return this;
    }

    /**
     * Adds a key,value request property to the request headers list.
     * @param key   Header field
     * @param value Header value
     * @return Builder
     */
    public HttpConnectorBuilder addHeaderProperty(String key, String value) {
        reqProperties.add(key, value);
        return this;
    }

    /**
     * Sets the body of the request.
     */
    public <E> HttpConnectorBuilder setBody(E body){

        this.body =body;
        return this;
    }

    /**
     * Sets the request to async. The HttpConnector.execute() will finish before a response returns.
     */
    public HttpConnectorBuilder async() {

        syncType = Http.SyncType.ASYNC;
        return this;
    }

    /**
     * Implement the InvocationCallback interface to define the handling of the response.
     * Ideal, clean and efficient way for optimized async processing.
     * @param invocationCallback javax.ws.rs.client.InvocationCallback.
     */
    public HttpConnectorBuilder async(InvocationCallback invocationCallback) {

        syncType = syncType.ASYNC;
        this.invocationCallback = invocationCallback;
        return this;

    }

    /**
     * (optional) Sets the request to store any set cookies associated with the response.
     * Should only be used if needed, as it will have a performance impact on async requests.
     */
    public HttpConnectorBuilder storeCookies() {
        storeCookies = true;
        return this;
    }

    private static SSLContext createTrustAllSSL() {

        SSLContext ssl = null;
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }};
        // Install the all-trusting trust manager
        try {
            ssl = SSLContext.getInstance("SSL");
            ssl.init(null, trustAllCerts, new java.security.SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e ) {
            logger.error("SSL generation error: {}", e.getMessage());
            e.printStackTrace();
        }

        HttpsURLConnection.setDefaultSSLSocketFactory(ssl.getSocketFactory());
        // Create all-trusting host name verifier
        HostnameVerifier allHostsValid = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };
        // Install the all-trusting host verifier
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

        return ssl;
    }

    /**
     * (optional) Created to allow greater control, but not required for the common usage.
     * @param properties keys and values should follow org.glassfish.jersey.client.ClientProperties
     */
    public HttpConnectorBuilder setClientProperties(Map<String,Object> properties) {

        clientProperties.putAll(properties);
        return this;
    }

    /**
     * (optional) Sets the thread pool size for async calls. Default is set to 100 threads.
     */
    public HttpConnectorBuilder setAsyncThreadPoolSize (int numberOfThreads) {

        clientProperties.put(ClientProperties.ASYNC_THREADPOOL_SIZE, numberOfThreads);
        return this;
    }

    private Http.HttpProtocol getProtocol(String urlString) throws URISyntaxException {

        String protocol = urlString.toLowerCase().substring(0, urlString.indexOf("://") + 3);
        switch (protocol) {
            case "http://":
                return Http.HttpProtocol.HTTP;
            case "https://":
                return Http.HttpProtocol.HTTPS;
            default:
                throw new  URISyntaxException(urlString, " url string is missing http/https");
        }
    }

    /**
     * (optional) Made with testing in mind. When you want to make HTTPS connection without validating SSL certificates.
     * Ideal for testing, risky for production.
     */
    public HttpConnectorBuilder trustAllSslContext() {
        trustAllSSLContext = true;
        logger.warn("SSL context was set to trust all. This setting has security risks and was designed to be used for testing");
        return this;
    }

    /**
     * Builds the HttpConnector object. Make this call as the final step of your builder.
     * @return a new HttpConnector object
     */
    public HttpConnector build()  {

            clientPropertiesDefault.entrySet().stream().forEach(entry -> clientProperties.putIfAbsent(entry.getKey(), entry.getValue()));
            return new HttpConnector(this);
    }


    /////////////////////////////////// GETTERS /////////////////////////////////////////

    public String getUrl() {
        return url;
    }

    public URI getURI() {
        return uri;
    }

    public Path getPath() {
        return path;
    }

    public Object getBody() {
        return body;
    }

    public MultivaluedMap<String, Object> getReqProperties() {
        return reqProperties;
    }

    public Http.HttpProtocol getConnType() {
        return connType;
    }

    public Map<String,Object> getClientProperties() { return clientProperties; }

    public Http.HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public Http.SyncType getSyncType() {
        return syncType;
    }

    public InvocationCallback getInvocationCallback() {
        return invocationCallback;
    }

    public int getAsyncThreadPoolSize () {
        return (int)clientProperties.getOrDefault(ClientProperties.ASYNC_THREADPOOL_SIZE, 100);
    }

    public boolean isRedirect() {
        return (boolean)clientProperties.getOrDefault(ClientProperties.FOLLOW_REDIRECTS, true);
    }

    public boolean isStoreCookies() {
        return storeCookies;
    }

    public boolean isTrustAllSslContext() { return trustAllSSLContext;}

    // not syncronized to improve performance
    public static SSLContext getTrustAllSslContext() {
        if (sslContext == null) {
            sslContext = createTrustAllSSL();
        }
        return sslContext;
    }

}

