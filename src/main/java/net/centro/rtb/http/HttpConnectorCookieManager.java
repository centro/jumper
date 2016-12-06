package net.centro.rtb.http;


import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cookie manager class. Used by the HttpConnector class
 */
public class HttpConnectorCookieManager {

    private static final Logger logger = LoggerFactory.getLogger(HttpConnectorCookieManager.class);

    private static final ThreadLocal<Map<String,NewCookie>> cookieStores = new ThreadLocal<Map<String,NewCookie>>() {
        @Override
        protected Map<String,NewCookie> initialValue() {
            return new HashMap<>();
        }
    };

    public static Map<String,NewCookie> getCookies() {
        return cookieStores.get();
    }

    public static void addCookies(Map<String,NewCookie> cookies) {
        if (cookies != null) {
            cookieStores.get().putAll(cookies);
        }
    }

    public static void setCookies(Invocation.Builder invoke) {
        cookieStores.get().entrySet().stream().forEach(entry -> invoke.cookie(entry.getValue()));
    }

    public static void reset() {
        cookieStores.remove();
    }

    /**
     * Removes any cookies with a matching name (string contains, not equals)
     * @param cookieName Cookies name to match
     * @return whether anyjersey  elements were removed
     */
    public static boolean removeCookies(String cookieName) {
        int existingCookies = cookieStores.get().size();
        cookieStores.set(cookieStores.get().entrySet().stream()
                .filter(cookie -> !cookie.getKey().contains(cookieName)).collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue())));
        return existingCookies > cookieStores.get().size();
    }

    public static void addCookie(NewCookie newCookie) {
        cookieStores.get().put(newCookie.getName(), newCookie);
    }

    public static void addCookie(Cookie cookie) { cookieStores.get().put(cookie.getName(), new NewCookie(cookie));}

    public static Cookie create(String name, String value) {
        return new Cookie(name, value);
    }

    public static Cookie getCookie(String name) {
        //List<Cookie> cookies = cookieStores.get();
        logger.debug("===> Cookies stored for thread: " + HttpConnector.map2String(cookieStores.get()) + "\n");;
        Cookie c = cookieStores.get().entrySet().stream().filter(cookie -> cookie.getKey().startsWith(name)).findFirst().orElse(null).getValue();
        if (c != null) { return c; }
        else {
            logger.error("Cookie: " + name + " was not found!");
            return null;
        }
    }
}
