package net.centro.rtb.http;

import com.google.common.io.CharStreams;
import org.glassfish.jersey.message.internal.EntityInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.net.ssl.*;
import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import javax.ws.rs.core.Response;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Main http client class.
 *
 * Should be instantiated using the HttpConnectorBuilder class.
 *
 * @author Ofir Gal
 * @version 1.0
 */
public class HttpConnector {


    private static final Logger logger = LoggerFactory.getLogger(HttpConnector.class);
    private static long userAgentCounter = System.currentTimeMillis();
    private Client client;
    private javax.ws.rs.core.Response response;
    private Future<javax.ws.rs.core.Response> future;
    private Http.SyncType syncType;
    private SSLContext ssl;
    private String responseMessage;
    private Object requestBody;
    private Object responseBody = null;
    private int responseCode;
    private Http.HttpMethod httpMethod;
    private URI url;
    private MultivaluedMap<String, Object> reqProperties;
    private InvocationCallback invocationCallback;
    private java.nio.file.Path path;
    private MediaType mediaType;
    private boolean storeCookies;
    private long duration;
    private long start,end;
    private Invocation.Builder invoke;
    private Http.Encoding encoding = null;

    static {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    protected HttpConnector(HttpConnectorBuilder builder) {

        storeCookies = builder.isStoreCookies();
        url = builder.getURI();
        path = builder.getPath();
        syncType = builder.getSyncType();
        httpMethod = (builder.getHttpMethod() != null) ? builder.getHttpMethod() : Http.HttpMethod.GET;
        reqProperties = builder.getRequestHeaders();
        invocationCallback = processCallback(builder.getInvocationCallback());
        requestBody = builder.getBody();
        if (reqProperties.get("content-type") != null) {
            mediaType = MediaType.valueOf(reqProperties.get("content-type").get(0).toString());
        }
        builder.getRequestHeaders().add("user-agent", "http-client" + userAgentCounter++);

        client = ClientFactory.getClient(builder);
        WebTarget target = client.target(url);
        invoke = target.request();
        invoke.headers(reqProperties);
        HttpConnectorCookieManager.setCookies(invoke);

        logger.debug("\nHTTP REQUEST:" + builder.getUrl().toString() + " \n|BODY| " + builder.getBody() + " \n|METHOD| " + builder.getHttpMethod() + " \n|HEADER| " + map2String(builder.getRequestHeaders()) + " \n|COOKIES| " + map2String(HttpConnectorCookieManager.getCookies()) + "\n");

    }

    private InvocationCallback<Response> processCallback(InvocationCallback invocationCallback){

        return new InvocationCallback<Response>() {
            @Override
            public void completed(Response response) {
                end = System.currentTimeMillis();
                duration = end - start;
                logger.trace("Async response: {}",response.getStatus());
                if (invocationCallback != null) { invocationCallback.completed(response); }
            }

            @Override
            public void failed(Throwable throwable) {
                end = System.currentTimeMillis();
                duration = end - start;
                logger.warn("Request failed: " + throwable.getMessage());
            }
        };
    }


    public static <T,E> String map2String(Map<T,E> map) {

        if (map == null) return "";
        String rtn = "";
        for (Map.Entry entry: map.entrySet()) {
            rtn = rtn + entry.getKey() + " : " + entry.getValue() + "\n";
        }

        return rtn;
    }

    private <T> void invokeRequest (Invocation.Builder invoke) {

    Entity<T> entity;

    switch (httpMethod) {
        case GET:
            if (syncType == Http.SyncType.ASYNC) {
                if (invocationCallback == null) {
                    future = invoke.async().get();
                } else {
                    future = invoke.async().get(invocationCallback);
                }
            } else {
                response = invoke.buildGet().invoke();
            }
            break;

        case POST:
            entity = (mediaType == null) ? Entity.text((T)requestBody) : Entity.entity((T) requestBody, mediaType);
            if (syncType == Http.SyncType.ASYNC) {
                if (invocationCallback == null) {
                    future = invoke.async().post(entity);
                } else {
                    future = invoke.async().post(entity, invocationCallback);
                }
            } else {
                response = invoke.buildPost(entity).invoke();
            }
            break;

        case PUT:
            entity = (mediaType == null) ? Entity.text((T)requestBody) : Entity.entity((T)requestBody, mediaType);
            if (syncType == Http.SyncType.ASYNC) {
                if (invocationCallback == null) {
                    future = invoke.async().put(entity);
                } else {
                    future = invoke.async().post(entity, invocationCallback);
                }
            } else {
                response = invoke.buildPut(entity).invoke();
            }
            break;

        case DELETE:
            entity = (mediaType == null) ? Entity.text((T)requestBody) : Entity.entity((T)requestBody, mediaType);
            if (syncType == Http.SyncType.ASYNC) {
                    future = invoke.async().method("DELETE", entity);
            } else {
                response = invoke.build("DELETE", entity).invoke();
            }
            break;
        }
    }

    /**
     *  Triggers the actual http request.
     * @return this (builder design)
     */
    public HttpConnector execute() {

        start = System.currentTimeMillis();

        invokeRequest(invoke);

        if (syncType == Http.SyncType.SYNC) {
            end = System.currentTimeMillis();
            duration = end - start;
        }

        if (storeCookies) {
            Map<String, NewCookie> cookies = null;
            switch (syncType) {
                case SYNC:
                    cookies = response.getCookies();
                    break;
                case ASYNC:
                    try {
                        response = future.get();
                        cookies = response.getCookies();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }
                    break;
            }
            HttpConnectorCookieManager.addCookies(cookies);
        }

        return this;
    }

    /**
     * Retreive the Content-Encoding header as an enum.
     * @return an enum representation of the Content-Encoding header.
     */
    public Http.Encoding getEncoding() {

        if (encoding == null) {
            encoding = getEncoding(response);
        }
        return encoding;

    }

    private static Http.Encoding getEncoding(Response response) {

        Http.Encoding encoding;
        String encodingValue = (response.getHeaderString("content-encoding") != null) ? response.getHeaderString("content-encoding") : "";
        encoding = Http.Encoding.fromString(encodingValue);
        return encoding;
    }

    /**
     * @return HTTP request response code (200,302,400,500,..)
     */
    public int getResponseCode() {

        if (responseCode != 0) {
            return  responseCode;
        }

        if (syncType == Http.SyncType.ASYNC) {
            try {
                response = future.get();
                responseCode = response.getStatus();
                return responseCode;
            } catch (InterruptedException | ExecutionException e) {
                logger.error(e.getMessage());
                e.printStackTrace();
            }
        }
        responseCode = response.getStatus();
        return responseCode;

    }

    /**
     * Parse the response into a String.
     * @return HTTP request response body as String.
     */
    public String getResponseBody() {

        InputStreamReader inputStreamReader;

        if (responseBody == null) {
            inputStreamReader = new InputStreamReader(getResponseBody(InputStream.class));
            try {
                responseBody = CharStreams.toString(inputStreamReader);
            } catch (IOException e1) {
                logger.error("Failed to parse InputStreamReader to String");
                e1.printStackTrace();
                return null;
            }
        }
        return (String)responseBody;
    }

    /**
     * Returns the raw Client object. This is for experimental use only.
     * @return raw Client object.
     */
    public Client getRawClient() {
        return client;
    }

    /**
     * Returns the http response body.
     * @param tClass the type of object the response should be mapped to. (Ex. getResponseBody(String.class))
     * @param <T> Generic type
     * @return Response body object as the generic parameter type.
     */
    public <T> T getResponseBody(Class<T> tClass) {

        if (responseBody != null) {
            if ((tClass.isAssignableFrom(InputStream.class)) && (responseBody instanceof String)) {
                return (T) new ByteArrayInputStream(((String) responseBody).getBytes());
            }
            return (T) responseBody;
        }

        try {
            if ((response != null && !response.hasEntity()) || (future != null && !future.get().hasEntity())) {
                String response = "Empty response: " + getResponseCode() + " " +  getResponseMessage();
                logger.warn(response);
                responseBody = response;
                return (tClass.getSimpleName().equals("String")) ? (T) responseBody : null;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error(e.getMessage());
            e.printStackTrace();
        }

        if (syncType == Http.SyncType.ASYNC) {
            try {
                response = future.get();
                responseBody = response.readEntity(tClass);

            } catch (InterruptedException | ExecutionException e) {
                logger.error(e.getMessage());
                e.printStackTrace();
                return null;
            }
        } else {
            responseBody = response.readEntity(tClass);
            //return ((T) responseBody);
        }

        if (tClass.isAssignableFrom(InputStream.class)) {
            responseBody = getInputStreamDecoded(((InputStream) responseBody), getEncoding());
        }

        return (T) responseBody;
    }

    private static InputStream getInputStreamDecoded(InputStream inputStream, Http.Encoding encode) {

        try {
            switch (encode) {
                case NONE:
                    return inputStream;
                case GZIP:
                    return new EntityInputStream(new GZIPInputStream(inputStream));
                case DEFLATE:
                    return new EntityInputStream(new InflaterInputStream(inputStream));
            }
        } catch (IOException e) {
            logger.error("Failed to decode stream with {} encoding", encode.name());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Checks if the async request received a response. (wrapper to a Future isDone() method)
     * @return true if a response exists, false if a response has yet to be received.
     */
    public boolean isAsyncDone() {
        return future.isDone();
    }

    /**
     * Static method to provide similar usability when implementing the InvocationCallBack interface.
     * @param response The javax.ws.rs.core.Response object returned in the onComplete(Response reponse)
     * @param tClass The type of object the response should transform to.
     * @param <T> generic type.
     * @return a generic type object that represent the response.
     */
    public static <T> T getResponseBody(Response response, Class<T> tClass) {

        if (response != null && !response.hasEntity())  {
            String responseString = "Empty response: " + response.getStatus() + " " +  response.getStatusInfo().getReasonPhrase();;
            logger.warn(responseString);
            return (tClass.getSimpleName().equals("String")) ? (T) response : null;
        }

        T body = response.readEntity(tClass);

        if (tClass.isAssignableFrom(InputStream.class)) {
            body = (T) getInputStreamDecoded((InputStream) body, getEncoding(response));
        }
        return body;
    }

    /**
     * The time in milliseconds it took to invoke the request and get a response from the server.
     * @return the time in Milliseconds it took to invoke the request and get a response from the server. Returns -1 if an async request hasn't received a response yet.
     * This method will return the total time of invoking a request and getting the response from the server.
     */
    public long geResponseTime() {

        return (duration != 0L) ? duration : -1;
    }

    /**
     * Save the file to the path set in the builder class.
     * @throws IOException
     */
    public void saveToFile() throws IOException {

        saveToFile(path);
    }

    /**
     * Saves the http response stream to a local file.
     * @param path a java.nio.file.Path
     * @throws IOException
     */
    public void saveToFile(java.nio.file.Path path) throws IOException {

        saveToFile(getResponseBody(InputStream.class), path);

    }


    /**
     * Saves the http response stream to a local file. Designed to be used by invocationCallback implementation.
     * @param path a java.nio.file.Path
     * @throws IOException
     */
    public static void saveToFile(Response response, java.nio.file.Path path) throws IOException {

        saveToFile(getResponseBody(response, InputStream.class), path);

    }

    /**
     * Saves the http response stream to a local file.
     * @param path a java.nio.file.Path
     * @throws IOException
     */
    private static void saveToFile(InputStream body, java.nio.file.Path path) throws IOException {

        path = path.normalize();
        if (!path.toFile().isDirectory()) {
            Files.copy(body, path, StandardCopyOption.REPLACE_EXISTING);
        } else {
            logger.error(path + " is a directory! expecting a file. Data will not be saved");
        }

    }

    /**
     * @return Requests http response code's relative response message (OK, UNAUTHORIZED, etc.)
     */
    public String getResponseMessage() {

        if (syncType == Http.SyncType.ASYNC) {
            try {
                responseMessage = future.get().getStatusInfo().getReasonPhrase();
                return responseMessage;
            } catch (InterruptedException | ExecutionException e) {
                logger.error(e.getMessage());
                e.printStackTrace();
                responseMessage = e.getMessage();
                return responseMessage;
            }
        }

        responseMessage = response.getStatusInfo().getReasonPhrase();
        return responseMessage;
    }



    public Future<Response> getFuture() {
        return future;
    }

    /**
     * Clears cookies in the ThreadLocalCookieStore relevant to the current thread.
     */
    public static void clearCookies() {
        HttpConnectorCookieManager.reset();
    }

    /**
     * Get the original Jersey Response object.
     * @return the javax.ws.rs.core.Response object
     */
    public javax.ws.rs.core.Response getRawResponse() {
        return response;
    }

    /**
     * Get a Map collection of the response headers.
     * @return a Map of the response headers.
     */
    public MultivaluedMap<String, Object> getResponseHeaders() {
       return response.getHeaders();
    }

    /**
     * Get the value of a specific header.
     * @param key a key in the headers map.
     * @return String
     */
    public String getResponseHeader(String key) {
        return response.getHeaderString(key);
    }

    /**
     * Close the http connection. This happens automatically and is not mandatory.
     */
    public void close() {
        response.close();
    }


}