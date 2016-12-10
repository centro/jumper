## Jumper
Simplified NIO (non-blocking I/O) HTTP client library with embedded cache.
Get your code running in just a couple of minutes. Based on the [Jersey](https://github.com/jersey/jersey) library. Highly efficient and scalable with seamless client caching. <br/>
<br/>Reasons for choosing Jumper:
* Easy to read(and write) with a simplified builder client.
* Enforce performance [best practices.](https://blogs.oracle.com/japod/entry/how_to_use_jersey_client)
* Switch between Apache, Jetty, Netty, Grizzly, HttpUrlConnector implementation with an easy enum selection.
* Providing seamless caching of clients for enhanced performance and scalability.
* Easy secured connections (HTTPS) to un-certified domains (for QA and testing) with TrustAllSSL mode.
* Out of the box performance metrics.
* Based on the industry-standard Jersey.

##### Why not simply use Jersey?
While Jersey is a great library, it merely provides building blocks for http connections. Jumper aims to provide idioms
and ready-to-use library that guarantees a high standard of performance and readability in your Java applications.

Jumper's ultimate goal is to encapsulate, simplify, and facilitate all http client concerns within a
Java application.

Choose the right library for your team! <br/><br/>
Your team is new to Jersey or programming http requests? With Jumper you will get :
<br/>
 1) Started in just 10 minutes. <br/>
 2) Best practices in terms of performance are enforced seamlessly. <br/>
 3) Readability and simplified usability. <br/>
 4) Simplified non-blocking I/O.





##### Compatibility
Java 8+

##### Dependencies
* [Jersey 2](https://github.com/jersey/jersey) - REST framework that provides JAX-RS Reference Implementation.
* [Google Guava](https://github.com/google/guava) - utilities and cache instrumentation.
* [Jackson JSON](https://github.com/FasterXML/jackson) - JSON parsing.
* Apache [Commons validator](http://commons.apache.org/proper/commons-validator/)  - utilities.
* [SLF4J](http://www.slf4j.org/) - logging.


## Usage
#### Basic Architecture
* `HttpConnector` - is the REST http implementation; it should be instantiated by the HttpConnectorBuilder class.
* `HttpConnectorBuilder` - setting the configuration of the http connection; it follows the builder design pattern.
methods for collecting metrics and instrumenting various entities.
* `ClientFactory` - provides the HttpConnector with a Client object, either from an internal cache or by instantiation.
* `Http` - a collection of library enums.
* `HttpConnectorCookieManager` - seamless cookies management.

Please see Javadoc comments for these classes to get a better grasp of them.

#### Installation
Maven
```xml
<dependency>
  <groupId>net.centro.rtb</groupId>
  <artifactId>jumper</artifactId>
  <version>${jumper-version}</version>
</dependency>
```

Gradle
```
dependencies {
  compile "net.centro.rtb:jumper:${jumper-version}"
}
```
#### Javadoc
The library contains a substantial amount of Javadoc comments. These should be available in your IDE, once you declare
a dependency on Jumper via Maven or Gradle.

### How to use it
(The unit tests contain many examples.)

Simple Get request
```java
HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("https://github.com/centro")
                .build();

httpConnector.execute();
```

Post request
```java
HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
        .url("https://example.com/login")
        .setMethod(Http.HttpMethod.POST)
        .setBody(object)
        .addHeaderProperty("Content-Type", "application/json")
        .storeCookies()
        .build();

httpConnector.execute();
```

File download request
(saveToFile() support both Path and String parameters)
```java
java.nio.file.Path path = Paths.get("/Users/me/home/document.pdf");

http = new HttpConnectorBuilder()
            .url("http://example.com/downloadFile")
            .saveToFile(path)
            .build();

http.execute();
```

**Asyncronized requests**

*async()* - will invoke the request async. and return a future
```java
HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("https://github.com/centro")
                .async()
                .build();

httpConnector.execute();

MyObject myObject = http.getResponseBody(myObject.class);
}
```
*async(InvocationCallback)* - will invoke the request async. and execute the InvocationCallback on response.
```java
InvocationCallback invocationCallback = new InvocationCallback<Response>() {
    @Override
    public void completed(Response response) {
        System.out.println(response.getStatus());
        System.out.println(HttpConnector.getResponseBody(response,String.class));
    }
    
    @Override
    public void failed(Throwable throwable) {
        System.out.println("oops");
    }
};

HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("https://example.com")
                .async(invocationCallback)
                .build();

httpConnector.execute();

```

JSON parsing
```java
HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("https://example.com/json")
                .async()
                .build();

httpConnector.execute();

ObjectNode objectNode = httpConnector.getResponseBody(ObjectNode.class);
```

Performance metrics
```java
HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("https://github.com/centro")
                .async()
                .build();

httpConnector.execute();

httpConnector.geResponseTime();
```

Fetch cookie - Retrieve all cookies stored by multiple requests per thread.
```java
HttpConnector http = HttpConnectorBuilder.newBuilder()
        .url("http://localhost:9998/cookie")
        .storeCookies()
        .build()
        .execute();

Map<String,NewCookie> cookies = HttpConnectorCookieManager.getCookies();
Cookie cookie = HttpConnectorCookieManager.getCookie("test");
```
Gzip encoded data - Parsing a response with gzip encoding
```java
HttpConnector http = HttpConnectorBuilder.newBuilder()
        .url("http://localhost:9998/gzip")
        .build()
        .execute();

InputStreamReader reader;
reader = new InputStreamReader(new GZIPInputStream(http.getResponseBody(InputStream.class)));

```
Deflate encoded data - Parsing a response with deflate encoding
```java
HttpConnector http = HttpConnectorBuilder.newBuilder()
        .url("http://localhost:9998/deflate")
        .build()
        .execute();

InputStreamReader reader;
reader = new InputStreamReader(new InflaterInputStream(http.getResponseBody(InputStream.class)));

```
Image (jpeg, png, bmp, wbmp, gif) - Parsing a response of an image.
```java
HttpConnector http = HttpConnectorBuilder.newBuilder()
                .url("https://httpbin.org/image/jpeg")
                .build()
                .execute();

BufferedImage image = null;
image = ImageIO.read(http.getResponseBody(InputStream.class));
```
#### More info on Jumper
Review the Javadoc documentation and the github.io page.
If you run into any issues or have questions, ask at [ofir.gal@centro.net](mailto:ofir.gal@centro.net)