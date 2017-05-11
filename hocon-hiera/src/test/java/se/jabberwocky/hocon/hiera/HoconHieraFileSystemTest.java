package se.jabberwocky.hocon.hiera;

import com.typesafe.config.Config;
import org.junit.Before;
import org.junit.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class HoconHieraFileSystemTest {

    private HoconHieraFileSystem hiera;
    private Config config;

    @Before
    public void setup() throws URISyntaxException {
        URL root = getClass().getResource("/root");
        assertNotNull(root);
        hiera = new HoconHieraFileSystem(root.getFile());
    }

    @Test
    public void hierarchy() throws Exception {
        List<String> hierarchy = hiera.hierarchy();
        assertEquals("system", hierarchy.get(0));
        assertEquals("app", hierarchy.get(1));
        assertEquals("node", hierarchy.get(2));
    }

    @Test
    public void root() throws Exception {
        config = hiera.root();
        assertNotNull(config);
        assertEquals("root", config.getString("some-param"));

    }

    @Test
    public void config_singleFacets() throws Exception {
        assertEquals("cms", hiera.config("system", "cms").getString("some-param"));
        assertEquals("web", hiera.config("system", "web").getString("some-param"));
        assertEquals("server-1", hiera.config("node", "server-1").getString("some-param"));
        assertEquals("server-2", hiera.config("node", "server-2").getString("some-param"));
        assertEquals("ecom", hiera.config("app", "ecom").getString("some-param"));
    }

    @Test
    public void config_hierarchy() throws Exception {
        Map<String,String> facets = new HashMap<>();
        facets.put("system", "cms");

        assertEquals("default", hiera.config(facets).getString("some-param"));

        facets.put("app", "ecom");
        assertEquals("ecom", hiera.config(facets).getString("some-param"));
    }

}