package se.jabberwocky.hocon.hiera;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Before;
import org.junit.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class HoconHieraFileSystemTest {

    private HoconHieraFileSystem hiera;
    private Map<String, String> facts;
    private Config config;

    @Before
    public void setup() throws URISyntaxException {
        URL root = getClass().getResource("/root");
        hiera = new HoconHieraFileSystem(root.getFile());

        facts = new HashMap<>();
        facts.put("groupId", "com.richemont.dms.commerce");
        facts.put("artifactId", "dms-commerce-core");
        facts.put("env", "prd");
        facts.put("dtc", "chvsg");
        facts.put("hostname", "dtcmeawsp01");

    }

    // -- tests

    @Test
    public void hiera() throws Exception {

        config = hiera.hiera();
        config = config.resolveWith(ConfigFactory.parseMap(facts));

        assertEquals("root", config.getString("some-param"));

        List<String> paths = config.getStringList(HoconHiera.HIERARCHY_CONFIG_KEY);

        assertEquals("app/com.richemont.dms.commerce.conf", paths.get(0));
        assertEquals("svc/dms-commerce-core.conf", paths.get(1));
        assertEquals("env/prd.conf", paths.get(2));
        assertEquals("dtc/chvsg.conf", paths.get(3));
        assertEquals("node/dtcmeawsp01.conf", paths.get(4));
        assertEquals("app-env/com.richemont.dms.commerce-prd.conf", paths.get(5));
        assertEquals("app-dtc/com.richemont.dms.commerce-chvsg.conf", paths.get(6));
        assertEquals("app-node/com.richemont.dms.commerce-dtcmeawsp01.conf", paths.get(7));
        assertEquals("svc-env/dms-commerce-core-prd.conf", paths.get(8));
        assertEquals("svc-dtc/dms-commerce-core-chvsg.conf", paths.get(9));
        assertEquals("svc-node/dms-commerce-core-dtcmeawsp01.conf", paths.get(10));

    }



    @Test
    public void config_path() throws Exception {
        assertNotNull(hiera.config("hiera.conf"));
        assertEquals("root", hiera.config("hiera.conf").getString("some-param"));

        assertNotNull(hiera.config("dtc/cnpdg.conf"));
        assertEquals("mongo.cn", hiera.config("dtc/cnpdg.conf").getString("mongo.host"));

        assertSame(ConfigFactory.empty(), hiera.config("bogus/path"));
    }


    @Test
    public void config_facts() throws Exception {
        config = hiera.config(facts);
        assertEquals("server-1", config.getString("some-param"));
        assertEquals("commerce-core", config.getString("http.baseName"));
        assertEquals("prd", config.getString("env"));
    }

}