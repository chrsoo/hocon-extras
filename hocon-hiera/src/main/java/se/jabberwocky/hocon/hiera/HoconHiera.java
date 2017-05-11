package se.jabberwocky.hocon.hiera;

import com.typesafe.config.Config;

import java.util.List;
import java.util.Map;

/**
 * Retrieves HOCON configuration files based on a config
 */
public interface HoconHiera {

    static final String HIERARCHY_CONFIG_KEY = "hierarchy";

    List<String> hierarchy();

    Config root();

    Config config(String facet, String value);

    default Config config(Map<String,String> facets) {
        return hierarchy()
                .stream()
                .map(facet -> config(facet, facets.get(facet)))
                .reduce(root(), (parent, child) -> child.withFallback(parent));
    }
}
