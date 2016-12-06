package net.centro.rtb.http;

/**
 * Http is a collection of enums.
 */
public class Http {

    /**
     * Defines the http REST method.
     */
    public enum HttpMethod {
        DELETE,
        GET,
        OPTIONS,
        PATCH,
        POST,
        PUT
    }

    /**
     * Defines a secure or non-secure connection.
     */
    public enum HttpProtocol {
        HTTP, HTTPS;
    }

    /**
     * Defines a synchronize or asynchronize http request.
     */
    public enum SyncType {
        SYNC, ASYNC
    }

    /**
     * Defines the connector implementation provider. HttpUrlConnector is the default.
     */
    public enum ConnectorProvider {
        Grizzly, Apache, Jetty, Netty, HttpUrlConnector
    }
}
