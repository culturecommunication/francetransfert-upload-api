package fr.gouv.culture.francetransfert.application.services;

import fr.gouv.culture.francetransfert.application.error.ErrorEnum;
import fr.gouv.culture.francetransfert.domain.enums.CookiesEnum;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadExcption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

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
        try {
            Cookie cookie = new Cookie(name, value);
            cookie.setHttpOnly(httpOnly);
//            cookie.setSecure(true);
            cookie.setPath(path);
            cookie.setDomain(domain);
            cookie.setMaxAge(maxAge);
            LOGGER.info("========================= cookie {} is created with value : ", name, value);
            return cookie;
        }  catch (Exception e) {
            String uuid = UUID.randomUUID().toString();
            LOGGER.error("Type: {} -- id: {} ", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
            throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
        }
    }

    public boolean isConsented(Cookie[] cookies) throws Exception {
        try {
            if (cookies != null) {
                for (Cookie cookie: cookies) {
                    if (CookiesEnum.IS_CONSENTED.getValue().equals(cookie.getName())) {
                        return true;
                    }
                }
            }
            return false;
        }  catch (Exception e) {
            String uuid = UUID.randomUUID().toString();
            LOGGER.error("Type: {} -- id: {} ", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
            throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
        }
    }

    public String getToken(HttpServletRequest request) throws Exception {
        try {
            //extract token from cookies if exist
            String token = "";
            boolean isConsented = isConsented(request.getCookies());
            if (isConsented) {
                token = extractCookie(request.getCookies(), CookiesEnum.SENDER_TOKEN.getValue());
            }
            LOGGER.info("==============================> sender-token is : {} ", StringUtils.isEmpty(token) ?  "empty" : token);
            return token;
        }  catch (Exception e) {
            String uuid = UUID.randomUUID().toString();
            LOGGER.error("Type: {} -- id: {} ", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
            throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
        }
    }

    public String getSenderId(HttpServletRequest request) throws Exception {
        try {
            //extract senderId from cookies if exist
            String senderId = "";
            boolean isConsented = isConsented(request.getCookies());
            if (isConsented) {
                senderId = extractCookie(request.getCookies(), CookiesEnum.SENDER_ID.getValue());
            }
            LOGGER.info("==============================> sender-id is : {} ", StringUtils.isEmpty(senderId) ?  "empty" : senderId);
            return senderId;
        } catch (Exception e) {
            String uuid = UUID.randomUUID().toString();
            LOGGER.error("Type: {} -- id: {} ", ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
            throw new UploadExcption(ErrorEnum.TECHNICAL_ERROR.getValue(), uuid);
        }
    }


}
