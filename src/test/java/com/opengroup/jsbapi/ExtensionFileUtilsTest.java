package com.opengroup.jsbapi;

import com.opengroup.jsbapi.domain.utils.ExtensionFileUtils;
import org.junit.Test;

import static org.junit.Assert.*;

public class ExtensionFileUtilsTest {

    @Test
    public void testGetExtension() {
        assertEquals("", ExtensionFileUtils.getExtension("C"));
        assertEquals("ext", ExtensionFileUtils.getExtension("C.ext"));
        assertEquals("ext", ExtensionFileUtils.getExtension("A/B/C.ext"));
        assertEquals("", ExtensionFileUtils.getExtension("A/B/C.ext/"));
        assertEquals("", ExtensionFileUtils.getExtension("A/B/C.ext/.."));
        assertEquals("bin", ExtensionFileUtils.getExtension("A/B/C.bin"));
        assertEquals("hidden", ExtensionFileUtils.getExtension(".hidden"));
        assertEquals("dsstore", ExtensionFileUtils.getExtension("/user/home/.dsstore"));
        assertEquals("", ExtensionFileUtils.getExtension(".strange."));
        assertEquals("3", ExtensionFileUtils.getExtension("1.2.3"));
        assertEquals("exe", ExtensionFileUtils.getExtension("C:\\Program Files (x86)\\java\\bin\\javaw.exe"));
    }
}
