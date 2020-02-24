package fr.gouv.culture.francetransfert;

import fr.gouv.culture.francetransfert.domain.utils.StringUploadUtils;
import org.junit.Test;

import static org.junit.Assert.*;

public class StringUtilsTest {


    @Test
    public void testIsEmpty() {
        assertTrue(StringUploadUtils.isEmpty(null));
        assertTrue(StringUploadUtils.isEmpty(""));
        assertFalse(StringUploadUtils.isEmpty(" "));
        assertFalse(StringUploadUtils.isEmpty("foo"));
        assertFalse(StringUploadUtils.isEmpty("  foo  "));
    }

    @Test
    public void testIsBlank() {
        assertTrue(StringUploadUtils.isBlank(null));
        assertTrue(StringUploadUtils.isBlank(""));
        assertTrue(StringUploadUtils.isBlank("   "));
        assertFalse(StringUploadUtils.isBlank("foo"));
        assertFalse(StringUploadUtils.isBlank("  foo  "));
    }


    @Test
    public void testTrim() {
        assertEquals("foo", StringUploadUtils.trim(" foo "));
        assertNotEquals("  foo  ", StringUploadUtils.trim("  foo  "));
    }


    @Test
    public void testRemove() {
        assertEquals("qeed", StringUploadUtils.remove("queued", 'u'));
        assertNotEquals("qeed", StringUploadUtils.remove("queued", 'z'));
    }

}
