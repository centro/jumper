Jumper
-----------
Simplified NIO (non-blocking I/O) HTTP client library with embedded cache.
Get your code running in just a couple of minutes. Based on the [Jersey](https://github.com/jersey/jersey) library. Highly efficient and scalable with seamless client caching.   Reasons for choosing Jumper:   

* Easy to read (and write) with a simplified builder client.
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

Choose the right library for your team!
Your team is new to Jersey or programming http requests? With Jumper you will get :


1. Started in just 10 minutes.   
2. Best practices in terms of performance are enforced seamlessly.    
3. Readability and simplified usability.
4. Simplified non-blocking I/O.
5. Simplfied compression & seamless de-compression of gzip and deflate encoding.

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

Please see [Javadoc](https://centro.github.io/jumper/docs) comments for these classes to get a better grasp of them.

#### Installation
Maven

~~~~     
<dependency>
  <groupId>net.centro.rtb</groupId>
  <artifactId>jumper</artifactId>
  <version>${jumper-version}</version>
</dependency>
~~~~
{: .language-xml}

Gradle

~~~
dependencies {
  compile "net.centro.rtb:jumper:${jumper-version}"
}
~~~
{: .language-groovy}

#### Javadoc
The library contains a substantial amount of Javadoc comments. These should be available in your IDE, once you declare a dependency on Jumper via Maven or Gradle.

### How to use it
(The unit tests contain many examples.)

Simple Get request

~~~
HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("https://github.com/centro")
                .build();

httpConnector.execute();

System.out.println(httpConnector.getResponseBody());

//getResponseBody() parse response body to a String
~~~
{: .language-java}

Get request with response mapping

~~~
HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("https://github.com/centro")
                .build();

httpConnector.execute();

// Object mapping
System.out.println(httpConnector.getResponseBody(MyObject.class));

// GenerticType mapping
GenericType<List<Integer>> type = new GenericType<List<Integer>>() {};
List<Integer> list = httpConnector.getResponseBody(type);
~~~
{: .language-java}

Post request

~~~
HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
        .url("https://example.com/login")
        .setMethod(Http.HttpMethod.POST)
        .setBody(object)
        .addHeaderProperty("Content-Type", "application/json")
        .storeCookies()
        .build();

httpConnector.execute();
~~~
{: .language-java}

File download request
(saveToFile() support both Path and String parameters)

~~~
java.nio.file.Path path = Paths.get("/Users/me/home/document.pdf");

http = new HttpConnectorBuilder()
            .url("http://example.com/downloadFile")
            .saveToFile(path)
            .build();

http.execute();

http.saveToFile();
~~~
{: .language-java}

**Asyncronized requests**

*async()* - will invoke the request async. and return a future

~~~
HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("https://github.com/centro")
                .async()
                .build();

httpConnector.execute();

MyObject myObject = http.getResponseBody(myObject.class);
~~~
{: .language-java}



*async(InvocationCallback)* - will invoke the request async. and execute the InvocationCallback on response.

~~~java
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
~~~
JSON parsing

~~~java
HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("https://example.com/json")
                .async()
                .build();

httpConnector.execute();

ObjectNode objectNode = httpConnector.getResponseBody(ObjectNode.class);
~~~

Performance metrics

~~~java
HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("https://github.com/centro")
                .async()
                .build();

httpConnector.execute();

httpConnector.geResponseTime();
~~~

Fetch cookie - Retrieve all cookies stored by multiple requests per thread.

~~~java
HttpConnector http = HttpConnectorBuilder.newBuilder()
        .url("http://localhost:9998/cookie")
        .storeCookies()
        .build()
        .execute();

Map<String,NewCookie> cookies = HttpConnectorCookieManager.getCookies();
Cookie cookie = HttpConnectorCookieManager.getCookie("test");
~~~
Gzip or Deflate encoded data - Parsing a response with gzip or Deflate encoding,

~~~java
HttpConnector http = HttpConnectorBuilder.newBuilder()
        .url("http://localhost:9998/gzip")
        .build()
        .execute();

// automatically detects gzip, deflate compressed input stream
// and decompress it.

System.out.println(http.getResponseBody());
~~~

Gzip or Deflate request body compression - Compressing the request body.

~~~java
HttpConnector http = HttpConnectorBuilder.newBuilder()
        .url("http://localhost:9998/gzip")
        .setBody(object)
        .compress(Http.Encoding.GZIP)
        .build()
        .execute();
~~~

Image (jpeg, png, bmp, wbmp, gif) - Parsing a response of an image.

~~~java
HttpConnector http = HttpConnectorBuilder.newBuilder()
                .url("https://httpbin.org/image/jpeg")
                .build()
                .execute();

BufferedImage image = null;
image = ImageIO.read(http.getResponseBody(InputStream.class));
~~~
File (Multipart) upload - Parsing a file in a client request.

~~~java
final FileDataBodyPart filePart = new FileDataBodyPart("test", new File("uploadFile.gz"));
final FormDataMultiPart multiPart = (FormDataMultiPart) new FormDataMultiPart()
        .bodyPart(filePart);

HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
        .url("http://localhost:9998/multi")
        .addHeaderProperty("Content-Type", "multipart/form-data")
        .setBody(multiPart)
        .setMethod(Http.HttpMethod.POST)
        .build();

httpConnector.execute();
~~~
Testing mode - trust uncertified HTTPS domains

~~~java
HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
        .url("https://localhost:9998/slow")
        .trustAllSslContext()
        .build()
        .execute();
~~~

#### More info on Jumper
Review the Javadoc documentation and the github.io page.
If you run into any issues or have questions, ask at [ofir.gal@centro.net](mailto:ofir.gal@centro.net)
