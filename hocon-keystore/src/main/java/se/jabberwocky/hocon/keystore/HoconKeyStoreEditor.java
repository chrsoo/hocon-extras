package se.jabberwocky.hocon.keystore;

import com.typesafe.config.*;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.SecretKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

public final class HoconKeyStoreEditor {

    private static final Logger LOGGER = Logger.getLogger(HoconKeyStoreEditor.class.getName());
    public static final String DEFAULT_PBE_KEY_SPEC = "PBEWithHmacSHA224AndAES_256";

    private final KeyStore keyStore;
    private final PasswordProtection password;
    private final SecretKeyFactory secretKeyFactory;

    private HoconKeyStoreEditor(KeyStore keyStore, String password) {

        this.keyStore = keyStore;
        this.password = new PasswordProtection(password.toCharArray());

        try {
            this.secretKeyFactory = SecretKeyFactory.getInstance(DEFAULT_PBE_KEY_SPEC);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Could not initialize the SecretConfigUtilities", e);
        }

    }

    public static HoconKeyStoreEditor with(KeyStore keyStore, String password) {
        return new HoconKeyStoreEditor(keyStore, password);
    }

    /**
     * Reveal all secret values for keys found in the key store.
     *
     * @param config the configuration containing ordinary configuration values and redacted secrets
     *
     * @return a new config backed by the supplied config that masks all config entries found in the key store.
     * @throws MissingKeyException if redacted secrets in the configuration cannot be found in the key store
     */

    public Config reveal(Config config) throws MissingKeyException {

        Map<String,String> secrets = new HashMap<>();
        List<Entry<String,ConfigValue>> missingSecrets = new ArrayList<>();

        config.entrySet().forEach( entry -> {

            if(isRedacted(entry.getValue())) {
                String secret = get(entry.getKey());
                if(secret == null) {
                    missingSecrets.add(entry);
                    LOGGER.warning("Cannot find entry for key '" + entry.getKey() + "' in the secret key store");
                }
                secrets.put(entry.getKey(), secret);
            }
            LOGGER.fine("key: '" + entry.getKey() + "'; value: '" + entry.getValue() + "'");

        });

        if(missingSecrets.isEmpty()) {
            Config secretConfig = ConfigFactory.parseMap(secrets);
            return secretConfig.withFallback(config);
        } else {
            throw new MissingKeyException(missingSecrets);
        }

    }

    /**
     * Redact all secret values for keys found in the key store.
     *
     * @param config the configuration containing ordinary configuration values and secrets
     * @return a new config backed by the supplied config that masks all config entries found in the key store.
     */
    public Config redact(Config config) {

        Map<String,String> secrets = new HashMap<>();
        List<Entry<String,ConfigValue>> missingSecrets = new ArrayList<>();

        config.entrySet().forEach( entry -> {

            String secret = get(entry.getKey());
            if(secret != null) {
                secrets.put(entry.getKey(), "*****");
                LOGGER.info("Redacting value for key '" + entry.getKey() + "'");
            }
        });
        Config secretConfig = ConfigFactory.parseMap(secrets);
        return secretConfig.withFallback(config);
    }

    /**
     * Update or insert all values in the key store for keys found in the config.
     *
     * @param config The configuration containing the configuration values to store in the key store.
     * @throws IllegalArgumentException if any of the config entries is a system property
     */
    public HoconKeyStoreEditor upsert(Config config) {
        config.entrySet().forEach( entry -> {
            assertValidSecret(entry);
        });

        config.entrySet().forEach( entry -> {
            put(entry);
        });

        return this;
    }

    /**
     * Update or insert all values in the key store for keys found in the map config.
     *
     * @param mapConfig The configuration containing the configuration values to store in the key store. Key values are
     *                  dot notation configuration paths (e.g. <code>path.to.some.key</code>)
     */
    public HoconKeyStoreEditor upsert(Map<String,String> mapConfig) {
        Config config = ConfigFactory.parseMap(mapConfig);
        return upsert(config);
    }

    /**
     * Update or insert the secret key to the String value of the Map Entry.
     *
     * @param entry
     * @throws RuntimeException if the put failed due to a problem with setting the entry in the key store
     * @throws IllegalArgumentException if the config entry is a system property
     */
    public HoconKeyStoreEditor put(Entry<String, ConfigValue> entry) {
        assertValidSecret(entry);
        if(isRedacted(entry.getValue())) {
            if(isKeyStoreEntry(entry.getKey())) {
                LOGGER.warning("Not updating redacted value for key '" + entry.getKey() + "'");
            } else {
                throw new MissingKeyException(entry.getKey());
            }
            return this;
        } else {
            return  put(entry.getKey(), (String) entry.getValue().unwrapped());
        }
    }

    /**
     * Update or insert a single configuration entry in the key store
     *
     * @param key the dot-notation configuration path to update (e.g. <code>path.to.some.key</code>)
     * @param secret the secret value to store in the key store
     * @return <code>this</code> for a fluent interface
     * @throws IllegalArgumentException if value is not a String
     */
    public HoconKeyStoreEditor put(String key, String secret) {
        try {
            SecretKey secretKey = secretKeyFactory.generateSecret(new PBEKeySpec(secret.toCharArray()));
            SecretKeyEntry keyEntry = new SecretKeyEntry(secretKey);
            keyStore.setEntry(key, keyEntry, password);
            LOGGER.fine("Upserted value for key '" + key + "'");
        } catch (InvalidKeySpecException | KeyStoreException e) {
            throw new RuntimeException("Could not store key '" + key + "' in key store", e);
        }

        return  this;
    }

    /**
     * Update all values in the key store for the keys found in the config. If one or more keys are missing in the key
     * store no values are updated and an exception is thrown.
     *
     * @param config The configuration containing the configuration values to store in the key store.
     * @throws MissingKeyException if one or more keys in the config is missing in the key store
     * @return <code>this</code> for a fluent interface
     */
    public HoconKeyStoreEditor update(Config config) throws MissingKeyException {

        List<Entry<String,ConfigValue>> missingKeys = new ArrayList<>();

        config.entrySet().forEach( entry -> {
            if(!isKeyStoreEntry(entry.getKey())) missingKeys.add(entry);
        });

        // update only if all keys are already present in the key store
        if(missingKeys.isEmpty()) {
            config.entrySet().forEach( entry -> {
                if(isRedacted(entry.getValue())) {
                    LOGGER.warning("Not updating redacted value for key '" + entry.getKey() + "'");
                } else {
                    update(entry);
                }
            });
            return this;
        } else {
            throw new MissingKeyException(missingKeys);
        }
    }


    /**
     * Update a single configuration entry in the key store
     *
     * @param entry the configuration entry to update
     * @return this for a fluent interface
     * @throws IllegalArgumentException if the configuration key does not exist
     */
    public HoconKeyStoreEditor update(Entry<String, ConfigValue> entry) throws IllegalArgumentException {
        assertValidSecret(entry);
        return update(entry.getKey(), (String) entry.getValue().unwrapped());
    }

    /**
     * Update a single configuration entry in the key store
     *
     * @param key the dot-notation configuration path to update (e.g. <code>path.to.some.key</code>)
     * @param secret the secret value to store in the key store
     * @return <code>this</code> for a fluent interface
     * @throws IllegalArgumentException if the configuration key does not exist or if the value is not a String
     */
    public HoconKeyStoreEditor update(String key, String secret) throws IllegalArgumentException {
        if(isKeyStoreEntry(key)) {
            put(key, secret);
            return this;
        } else {
            throw new IllegalArgumentException("Could not find the key '" + key + "' in the key store");
        }
    }


    public boolean isRedacted(ConfigValue value) {
        return value.valueType() == ConfigValueType.STRING
                && ((String) value.unwrapped()).startsWith("*****");
    }

    public String get(String key) {
        SecretKey secretKey = getSecretKey(key);
        if(secretKey == null) {
            return null;
        }

        return getConfigSecret(secretKey);
    }

    public SecretKey getSecretKey(String key) {
        try {
            if(keyStore.isKeyEntry(key)) {
                SecretKeyEntry entry = (SecretKeyEntry) keyStore.getEntry(key, password);
                return entry.getSecretKey();
            }
            if(keyStore.isCertificateEntry(key)) {
                throw new IllegalArgumentException("Certifiactes entries not supported!");
            }
            // key entry not found
            return null;
        } catch (NoSuchAlgorithmException | UnrecoverableEntryException | KeyStoreException e) {
            throw new RuntimeException("Could not retrieve secret for key '" + key + "' from key store", e);
        }
    }

    private String getConfigSecret(SecretKey secretKey) {
        try {
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(secretKey.getAlgorithm());
            PBEKeySpec keySpec = (PBEKeySpec) keyFactory.getKeySpec(secretKey, PBEKeySpec.class);
            char[] secret = keySpec.getPassword();
            return new String(secret);
        } catch (NoSuchAlgorithmException |InvalidKeySpecException e) {
            throw new IllegalStateException("Could not get config secret", e);
        }
    }


    public HoconKeyStoreEditor del(String key) {
        try {
            keyStore.deleteEntry(key);
            return this;
        } catch (Exception e) {
            throw new RuntimeException("Could not retrieve secret for key '" + key + "' from key store", e);
        }
    }

    public static HoconKeyStoreEditor create(String password, KeyStoreType type) {
        return from((InputStream) null, password, type);
    }

    public static HoconKeyStoreEditor from(Path path, String password, KeyStoreType type) {
        try {
            InputStream stream = Files.newInputStream(path);
            return from(stream, password, type);
        } catch (IOException e) {
            throw new RuntimeException("Could not open the path '" + path + "'", e);
        }
    }

    public static HoconKeyStoreEditor from(InputStream stream, String password, KeyStoreType type) {
        try {
            KeyStore keyStore = KeyStore.getInstance(type.name());
            keyStore.load(stream, password.toCharArray());

            return new HoconKeyStoreEditor(keyStore, password);

        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("Could not load key store", e);
        }
    }

    public void to(KeyStore keyStore) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        to(stream);
        try {
            keyStore.load(new ByteArrayInputStream(stream.toByteArray()), password.getPassword());
        } catch (IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new RuntimeException("Could not write key store", e);
        }
    }

    public HoconKeyStoreEditor to(OutputStream stream) {
        try {
            keyStore.store(stream, password.getPassword());
            return this;
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new RuntimeException("Could not write keystore to stream", e);
        }
    }

    public HoconKeyStoreEditor to(String file) {
        return to(new File(file));
    }

    public HoconKeyStoreEditor to(File file) {
        return to(Paths.get(file.getPath()));
    }
    public HoconKeyStoreEditor to(Path path) {
        try {
            Path tempFile = Files.createTempFile("hocon-", ".p12");
            Files.newOutputStream(tempFile);
            OutputStream stream = Files.newOutputStream(tempFile);
            keyStore.store(stream, password.getPassword());
            Files.copy(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
            return this;
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new RuntimeException("Could not write keystore to '" + path.toAbsolutePath().toString() + "'", e);
        }
    }


    /**
     * Generate a secret key for the given alias using the keystore password for protection.
     *
     * @param alias the alias under which to store the key
     * @param algorithm the secret key algorithm
     * @param size the size of the secret key, cf. the allowed values for the algorithm
     * @return this for a fluent interface
     */
    public HoconKeyStoreEditor generate(String alias, String algorithm, int size) {
        try {

            KeyGenerator keyGen = KeyGenerator.getInstance(algorithm);
            keyGen.init(size);

            SecretKey secretKey = keyGen.generateKey();
            KeyStore.Entry entry = new SecretKeyEntry(secretKey);

            keyStore.setEntry(alias, entry, password);

            return this;
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw new RuntimeException("Could generate secret key", e);
        }

    }

    // -- private methods

    private void assertValidSecret(Entry<String, ConfigValue> entry) {
        ConfigOrigin origin = entry.getValue().origin();

        if("system properties".equals(origin.description())) {
            throw new IllegalArgumentException("System properties are not valid key store entries; " +
                    "found system property '" + entry.getKey() + "'");
        }

        if(entry.getValue().valueType() != ConfigValueType.STRING) {
            throw new IllegalArgumentException("Only " + ConfigValueType.STRING
                    + " values supported, found "
                    + entry.getValue().valueType() +
                    " value for key '" + entry.getKey() + "'");
        }
    }

    private boolean isKeyStoreEntry(String key) {
        try {
            return keyStore.isKeyEntry(key);
        } catch (KeyStoreException e) {
            throw new RuntimeException("Could not check entry for key '" + key + "'", e);
        }

    }

}
