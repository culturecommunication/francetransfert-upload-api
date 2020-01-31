package fr.gouv.culture.francetransfert.application.services;

import fr.gouv.culture.francetransfert.domain.enums.CookiesEnum;
import org.springframework.stereotype.Service;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

@Service
public class CookiesServices {

    public String extractCookie(Cookie[] cookies, String cookieName) throws Exception {
        String token = "";
        if (cookies != null) {
            for (Cookie cookie: cookies) {
                if (cookieName.equals(cookie.getName())) {
                    token = cookie.getValue();
                }
            }
        }
        return token;
    }

    public Cookie createCookie(String Name, String value, boolean httpOnly, String path, String domain, int maxAge) throws Exception {
        Cookie cookie = new Cookie(Name, value);
        cookie.setHttpOnly(httpOnly);
//            cookie.setSecure(true);
        cookie.setPath(path);
        cookie.setDomain(domain);
        cookie.setMaxAge(maxAge);
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
            token = extractCookie(request.getCookies(), CookiesEnum.SENDER_TOKEN.name());
        }
        return token;
    }


}
