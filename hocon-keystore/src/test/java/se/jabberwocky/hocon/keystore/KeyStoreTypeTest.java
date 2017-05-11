package se.jabberwocky.hocon.keystore;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KeyStoreTypeTest {

    @Test
    public void fromFilename() throws Exception {
        assertEquals(KeyStoreType.JKS, KeyStoreType.fromFilename("file.jks"));

        assertEquals(KeyStoreType.JCEKS, KeyStoreType.fromFilename("file.jceks"));

        assertEquals(KeyStoreType.PKCS12, KeyStoreType.fromFilename("file.p12"));
        assertEquals(KeyStoreType.PKCS12, KeyStoreType.fromFilename("file.pfx"));

        assertEquals(KeyStoreType.UNKNOWN, KeyStoreType.fromFilename("file.txt"));

    }

    @Test
    public void fromFilename_uppperCase() throws Exception {
        assertEquals(KeyStoreType.JKS, KeyStoreType.fromFilename("FILE.JKS"));
        assertEquals(KeyStoreType.PKCS12, KeyStoreType.fromFilename("FILE.P12"));
    }

    @Test
    public void fromFilename_multipleDots() throws Exception {
        assertEquals(KeyStoreType.JKS, KeyStoreType.fromFilename("some.file.jks"));
        assertEquals(KeyStoreType.JKS, KeyStoreType.fromFilename("some.file..jks"));
    }


}