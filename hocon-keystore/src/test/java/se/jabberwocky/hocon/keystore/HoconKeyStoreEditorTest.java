package se.jabberwocky.hocon.keystore;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Before;
import org.junit.Test;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.SecretKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.*;

public class HoconKeyStoreEditorTest {

    private Config config;
    private PasswordProtection keyStorePP;
    private SecretKeyFactory factory;
    private KeyStore keyStore;

    @Before
    public void setup() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, InvalidKeySpecException {

        char[] password = "CHANGEME".toCharArray();
        keyStore = KeyStore.getInstance("JCEKS");
        keyStore.load(null, password);

        factory = SecretKeyFactory.getInstance(HoconKeyStoreEditor.DEFAULT_PBE_KEY_SPEC);
        keyStorePP = new PasswordProtection(password);

        addSecret("Config.Secret", "SECRET");
        addSecret("Config.Redacted", "REDACTED");

//        FileInputStream fIn = new FileInputStream(keystoreLocation);
//        keyStore.load(fIn, password);

        config = ConfigFactory.parseResources("application.conf");
    }

    @Test
    public void put() throws Exception {
        HoconKeyStoreEditor.with(keyStore, "CHANGEME")
                .put("SomeKey", "SomeValue")
                .put("OtherKey", "OtherValue");
        assertKeyStoreEntry("SomeKey", "SomeValue");
        assertKeyStoreEntry("OtherKey", "OtherValue");
    }

    @Test
    public void del() throws Exception {
        HoconKeyStoreEditor.with(keyStore, "CHANGEME").put("SomeKey", "SomeValue");
        assertKeyStoreEntry("SomeKey", "SomeValue");
        HoconKeyStoreEditor.with(keyStore, "CHANGEME").del("SomeKey");
        assertKeyStoreEntry("SomeKey", null);
    }

    @Test
    public void get() throws Exception {
        String actual = HoconKeyStoreEditor.with(keyStore, "CHANGEME").get("Config.Redacted");
        assertEquals("REDACTED", actual);
    }

    @Test
    public void generate() throws Exception {
        HoconKeyStoreEditor editor = HoconKeyStoreEditor.create("CHANGEME", KeyStoreType.JCEKS);
        String actual = editor
                .generate("secret", "HMacSHA256", 2048)
                .get("secret");

        int index = actual.indexOf(":");
        assertEquals("ENC(HmacSHA256", actual.substring(0, index));
        assertTrue(actual.endsWith(")"));

        String base64 = actual.substring(index+1, actual.length() - 1);
        byte[] decoded = Base64.getDecoder().decode(base64);

        editor.put("encoded", actual);
        KeyStore keyStore = KeyStore.getInstance(KeyStoreType.JCEKS.name());
        editor.to(keyStore);
        SecretKeyEntry entry = (SecretKeyEntry) keyStore.getEntry(
                "encoded", new PasswordProtection("CHANGEME".toCharArray()));

        SecretKey encodedKey = entry.getSecretKey();
        assertEquals("RAW", encodedKey.getFormat());
        byte[] encoded = encodedKey.getEncoded();

        assertArrayEquals(decoded, encoded);
    }

    @Test
    public void reveal() throws Exception {

        config = HoconKeyStoreEditor.with(keyStore, "CHANGEME").reveal(config);

        assertEquals("NO_SECRET", config.getString("Config.NoSecret"));
        assertEquals("****", config.getString("Config.FourStars"));
        assertEquals("SECRET", config.getString("Config.Secret"));
        assertEquals("REDACTED", config.getString("Config.Redacted"));

    }

    @Test
    public void redact() throws Exception {

        config = HoconKeyStoreEditor.with(keyStore, "CHANGEME").redact(config);

        assertEquals("NO_SECRET", config.getString("Config.NoSecret"));
        assertEquals("****", config.getString("Config.FourStars"));
        assertEquals("*****", config.getString("Config.Secret"));
        assertEquals("*****", config.getString("Config.Redacted"));

    }

    @Test(expected = MissingKeyException.class)
    public void update_missingKeys() throws Exception {
        HoconKeyStoreEditor.with(keyStore, "CHANGEME").update(config);
    }

    @Test
    public void update() throws Exception {

        addSecret("Config.NoSecret", "ShouldBeUpdated");
        addSecret("Config.FourStars", "ShouldBeUpdated");

        HoconKeyStoreEditor.with(keyStore, "CHANGEME").update(config);

        assertKeyStoreEntry("Config.NoSecret", "NO_SECRET");
        assertKeyStoreEntry("Config.FourStars", "****");
        assertKeyStoreEntry("Config.Secret", "SECRET");
        assertKeyStoreEntry("Config.Redacted", "REDACTED");

    }

    @Test
    public void upsert() throws Exception {

        HoconKeyStoreEditor.with(keyStore, "CHANGEME").upsert(config);

        assertKeyStoreEntry("Config.NoSecret", "NO_SECRET");
        assertKeyStoreEntry("Config.FourStars", "****");
        assertKeyStoreEntry("Config.Secret", "SECRET");
        assertKeyStoreEntry("Config.Redacted", "REDACTED");

    }

    @Test(expected = IllegalArgumentException.class)
    public void upsert_systemProperties() {
        config = ConfigFactory.load(); // includes system properties which we don't want to add
        HoconKeyStoreEditor.with(keyStore, "CHANGEME").upsert(config);
    }

    @Test
    public void from_stream() {
        InputStream stream = getClass().getResourceAsStream("/keystore.jceks");
        Config revealed = HoconKeyStoreEditor.from(stream, "CHANGEME", KeyStoreType.JCEKS).reveal(config);
        assertEquals("REDACTED", revealed.getString("Config.Redacted"));
    }

    @Test
    public void create() {
        HoconKeyStoreEditor editor = HoconKeyStoreEditor.create("CHANGEME", KeyStoreType.JCEKS)
                .put("secret", "CHANGEME")
                ;
        assertEquals("CHANGEME", editor.get("secret"));
    }

    private void assertKeyStoreEntry(String key, String secret) throws UnrecoverableEntryException, NoSuchAlgorithmException, KeyStoreException, InvalidKeySpecException {

        if( secret == null) {
            assertFalse(keyStore.isKeyEntry(key));
            return;
        }

        KeyStore.Entry entry = keyStore.getEntry(key, keyStorePP);
        SecretKeyEntry ske = (SecretKeyEntry) keyStore.getEntry(key, keyStorePP);

        assertNotNull("KeyStore entry must not be null", ske);

        PBEKeySpec keySpec = (PBEKeySpec) factory.getKeySpec(ske.getSecretKey(), PBEKeySpec.class);
        char[] value = keySpec.getPassword();
        assertEquals(secret, new String(value));

    }

    private void addSecret(String key, String secret) throws KeyStoreException, InvalidKeySpecException {
        SecretKey generatedSecret = factory.generateSecret(new PBEKeySpec(secret.toCharArray()));
        keyStore.setEntry(key, new SecretKeyEntry(generatedSecret), keyStorePP);
    }

}