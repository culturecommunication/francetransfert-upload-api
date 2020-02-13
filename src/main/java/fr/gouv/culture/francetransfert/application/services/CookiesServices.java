package fr.gouv.culture.francetransfert.application.services;

import fr.gouv.culture.francetransfert.domain.enums.CookiesEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

@Service
public class CookiesServices {

    private static final Logger LOGGER = LoggerFactory.getLogger(CookiesServices.class);

    public String extractCookie(Cookie[] cookies, String cookieName) throws Exception {
        String cookieValue = "";
        if (cookies != null) {
            for (Cookie cookie: cookies) {
                if (cookieName.equals(cookie.getName())) {
                    cookieValue = cookie.getValue();
                }
            }
        }
        return cookieValue;
    }

    public Cookie createCookie(String name, String value, boolean httpOnly, String path, String domain, int maxAge) throws Exception {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(httpOnly);
//            cookie.setSecure(true);
        cookie.setPath(path);
        cookie.setDomain(domain);
        cookie.setMaxAge(maxAge);
        LOGGER.info("========================= cookie {} is created with value : ", name, value);
        return cookie;
    }

    public boolean isConsented(Cookie[] cookies) throws Exception {
        if (cookies != null) {
            for (Cookie cookie: cookies) {
                if (CookiesEnum.IS_CONSENTED.getValue().equals(cookie.getName())) {
                   return true;
                }
            }
        }
        return false;
    }

    public String getToken(HttpServletRequest request) throws Exception {
        //extract token from cookies if exist
        String token = "";
        boolean isConsented = isConsented(request.getCookies());
        if (isConsented) {
            token = extractCookie(request.getCookies(), CookiesEnum.SENDER_TOKEN.getValue());
        }
        LOGGER.info("==============================> sender-token is : {} ", StringUtils.isEmpty(token) ?  "empty" : token);
        return token;
    }

    public String getSenderId(HttpServletRequest request) throws Exception {
        //extract senderId from cookies if exist
        String senderId = "";
        boolean isConsented = isConsented(request.getCookies());
        if (isConsented) {
            senderId = extractCookie(request.getCookies(), CookiesEnum.SENDER_ID.getValue());
        }
        LOGGER.info("==============================> sender-id is : {} ", StringUtils.isEmpty(senderId) ?  "empty" : senderId);
        return senderId;
    }


}
