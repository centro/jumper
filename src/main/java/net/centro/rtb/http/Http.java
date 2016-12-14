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

    /**
     * Represent the value of the Content-Encoding header.
     */
    public enum Encoding {
        NONE(""), GZIP("gzip"), DEFLATE("deflate");

        private String text;

        Encoding (String text) {
            this.text = text;
        }

        public String getText() {
            return this.text;
        }

        public static Encoding fromString(String text) {
            if (text != null) {
                for (Encoding b : Encoding.values()) {
                    if (text.equalsIgnoreCase(b.text)) {
                        return b;
                    }
                }
            }
            return null;
        }
    }
}
