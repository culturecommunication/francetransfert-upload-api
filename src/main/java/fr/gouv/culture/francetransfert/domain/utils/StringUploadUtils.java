package fr.gouv.culture.francetransfert.domain.utils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * StringUtils class
 * Utily class to manipulate Strings
 */
public class StringUploadUtils {

    private static final int INDEX_NOT_FOUND = -1;

    private final static String regex_valid_email = "^\\w+([\\.-]\\w+)*(\\+\\w+)?@\\w+([\\.-]\\w+)*(\\.\\w+)+$";

    private final static String regex_gouv_email ="^\\w+([\\.-]\\w+)*(\\+\\w+)?@(\\w+([\\.-]\\w+)*\\.)?gouv\\.fr$";

    private StringUploadUtils() {
        // private constructor
    }

    /**
     * <p>Checks if a CharSequence is whitespace, empty ("") or null.</p>
     *
     * <pre>
     * StringUtils.isBlank(null)      = true
     * StringUtils.isBlank("")        = true
     * StringUtils.isBlank(" ")       = true
     * StringUtils.isBlank("bob")     = false
     * StringUtils.isBlank("  bob  ") = false
     * </pre>
     *
     * @param cs the CharSequence to check, may be null
     * @return {@code true} if the CharSequence is null, empty or whitespace
     * @since 2.0
     * @since 3.0 Changed signature from isBlank(String) to isBlank(CharSequence)
     */
    public static boolean isBlank(final CharSequence cs) {
        int strLen;
        if (cs == null || (strLen = cs.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }


    /**
     * <p>Removes control characters (char &lt;= 32) from both
     * ends of this String, handling {@code null} by returning
     * {@code null}.</p>
     *
     * <p>The String is trimmed using {@link String#trim()}.
     * Trim removes start and end characters &lt;= 32.
     * To strip whitespace use .</p>
     *
     * <p>To trim your choice of characters, use the
     * methods.</p>
     *
     * <pre>
     * StringUtils.trim(null)          = null
     * StringUtils.trim("")            = ""
     * StringUtils.trim("     ")       = ""
     * StringUtils.trim("abc")         = "abc"
     * StringUtils.trim("    abc    ") = "abc"
     * </pre>
     *
     * @param str the String to be trimmed, may be null
     * @return the trimmed string, {@code null} if null String input
     */
    public static String trim(final String str) {
        return str == null ? null : str.trim();
    }


    /**
     * <p>Checks if a CharSequence is empty ("") or null.</p>
     *
     * <pre>
     * StringUtils.isEmpty(null)      = true
     * StringUtils.isEmpty("")        = true
     * StringUtils.isEmpty(" ")       = false
     * StringUtils.isEmpty("bob")     = false
     * StringUtils.isEmpty("  bob  ") = false
     * </pre>
     *
     * <p>NOTE: This method changed in Lang version 2.0.
     * It no longer trims the CharSequence.
     * That functionality is available in isBlank().</p>
     *
     * @param cs the CharSequence to check, may be null
     * @return {@code true} if the CharSequence is empty or null
     * @since 3.0 Changed signature from isEmpty(String) to isEmpty(CharSequence)
     */
    public static boolean isEmpty(final CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    /**
     * <p>Removes all occurrences of a character from within the source string.</p>
     *
     * <p>A {@code null} source string will return {@code null}.
     * An empty ("") source string will return the empty string.</p>
     *
     * <pre>
     * StringUtils.remove(null, *)       = null
     * StringUtils.remove("", *)         = ""
     * StringUtils.remove("queued", 'u') = "qeed"
     * StringUtils.remove("queued", 'z') = "queued"
     * </pre>
     *
     * @param str    the source String to search, may be null
     * @param remove the char to search for and remove, may be null
     * @return the substring with the char removed if found,
     * {@code null} if null String input
     * @since 2.1
     */
    public static String remove(final String str, final char remove) {
        if (isEmpty(str) || str.indexOf(remove) == INDEX_NOT_FOUND) {
            return str;
        }
        final char[] chars = str.toCharArray();
        int pos = 0;
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] != remove) {
                chars[pos++] = chars[i];
            }
        }
        return new String(chars, 0, pos);
    }

    public static String extractDomainNameFromEmailAddress(String str) {
        String result = "";
        if (str != null && !str.isEmpty()) {
            result = str.substring(str.indexOf("@") + 1);
        }
        return result;
    }

    public static boolean isGouvEmail(String email, String regexGouvMail) {
        if (regexGouvMail != null) {
            return isValidRegex(regexGouvMail, email);
        } else {
            return isValidRegex(regex_gouv_email, email);
        }
    }
    public static boolean isAllGouvEmail(List<String> emails, String regexGouvMail) {
        if (regexGouvMail != null) {
            return emails.stream().allMatch(email -> isValidRegex(regexGouvMail, email));
        } else {
            return emails.stream().allMatch(email -> isValidRegex(regex_gouv_email, email));
        }
    }

    public static boolean isValidEmail(String email) {
        return isValidRegex(regex_valid_email, email);
    }

    public static boolean isValidRegex(String p, String str) {
        if (null == str) {
            return false;
        }
        return Pattern.matches(p, str);
    }

    public static String extractValueUsingRegex(String line, String pattern) throws Exception {
        String result = "";
        Pattern r = Pattern.compile(pattern); // Create a Pattern object
        Matcher m = r.matcher(line);  // Now create matcher object.
        if (m.find()) {
           result = m.group(0);
        } else {
            throw new Exception();
        }
        return result;
    }
}