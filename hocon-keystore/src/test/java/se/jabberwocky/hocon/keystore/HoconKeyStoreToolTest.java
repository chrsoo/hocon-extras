package se.jabberwocky.hocon.keystore;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.Assert.assertEquals;
import static se.jabberwocky.hocon.keystore.HoconKeyStoreTool.DEFAULT_SECRET_KEY_ALGORITHM;
import static se.jabberwocky.hocon.keystore.HoconKeyStoreTool.DEFAULT_SECRET_KEY_SIZE;

public class HoconKeyStoreToolTest {

    private HoconKeyStoreEditor editor;
    private Path keystore;
    private Path conf;

    @Before
    public void setup() throws IOException {
        Path original;

        keystore = Files.createTempFile("hocon-", ".jceks");
        original = new File(getClass().getResource("/keystore.jceks").getFile()).toPath();
        Files.copy(original, keystore, StandardCopyOption.REPLACE_EXISTING);

        editor = HoconKeyStoreEditor.from(keystore, "CHANGEME", KeyStoreType.JCEKS);

        conf = Files.createTempFile("application-", ".conf");
        original = new File(getClass().getResource("/application.conf").getFile()).toPath();
        Files.copy(original, conf, StandardCopyOption.REPLACE_EXISTING);

    }

    @Test
    public void printHelp() throws IOException {
        HoconKeyStoreTool.printHelp();
    }

    @Test
    public void run_put() throws IOException {
        run("put", "Config.Redacted=NEW_VALUE",false, true);
        Config config = ConfigFactory.parseFile(conf.toFile());
        assertEquals("NEW_VALUE", editor.get("Config.Redacted"));
    }

    @Test
    public void run_del() throws IOException {
        run("del", "Config.Redacted",false, true);
        Config config = ConfigFactory.parseFile(conf.toFile());
        assertEquals(null, editor.get("Config.Redacted"));
    }

    @Test( expected = IllegalArgumentException.class)
    public void run_put_illegalArgument() throws IOException {
        run("put", "Config.Redacted",false, true);
    }

    @Test
    public void run_redact_stdout() throws IOException {
        run("redact", conf, false, true);
        run("redact", conf, false, false);
    }

    @Test
    public void run_generate() throws IOException {
        run("generate", "generated", false, true);
    }

    @Test
    public void run_redact_replace() throws IOException, URISyntaxException {
        run("redact", conf, true, false);

        Config config = ConfigFactory.parseFile(conf.toFile());

        assertEquals("*****", config.getString("Config.Redacted"));
        assertEquals("*****", config.getString("Config.Secret"));
        assertEquals("NO_SECRET", config.getString("Config.NoSecret"));
        assertEquals("****", config.getString("Config.FourStars"));
    }

    @Test
    public void run_reveal_replace() throws IOException, URISyntaxException {
        run("reveal", conf, true, false);

        Config config = ConfigFactory.parseFile(conf.toFile());

        assertEquals("REDACTED", config.getString("Config.Redacted"));
        assertEquals("SECRET", config.getString("Config.Secret"));
        assertEquals("NO_SECRET", config.getString("Config.NoSecret"));
        assertEquals("****", config.getString("Config.FourStars"));
    }

    private void run(String command, Path config, boolean replace, boolean json) {
        run(command, config.toString(), replace, json);
    }

    private void run(String command, String argument, boolean replace, boolean json) {
        HoconKeyStoreTool tool = new HoconKeyStoreTool(
                keystore, editor,
                command, argument,
                replace, json, DEFAULT_SECRET_KEY_ALGORITHM, DEFAULT_SECRET_KEY_SIZE);
        tool.run();
    }

}