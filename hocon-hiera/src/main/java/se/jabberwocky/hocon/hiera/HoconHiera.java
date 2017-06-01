package se.jabberwocky.hocon.hiera;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.Map;

/**
 * Retrieves HOCON configuration files based on a config
 */
public interface HoconHiera {

    /**
     * Key used to retrieve the hierarchy of files in the hiera configuration.
     */
    String HIERARCHY_CONFIG_KEY = "hierarchy";

    /**
     * Return the unresolved Hiera Config that contains a list of paths under the HIERARCHY_CONFIG_KEY.
     *
     * @return an unresolved Hiera config
     */
    Config hiera();

    /**
     * Retrieve the config for the given path
     *
     * @param path to a configuration file
     * @return the parsed config or null if the path does not exist
     */
    Config config(String path);

    /**
     * Retrieve the resolved configuration for a given set of facts
     *
     * @param facts used to resolve the configuration
     * @return configuration matching the facts
     */
    default Config config(Map<String,String> facts) {

        Config factsConfig = ConfigFactory.parseMap(facts);

        // resolve the hiera config with the provided facts
        return hiera().resolveWith(factsConfig)
                // retrieve and stream the resolved paths
                .getStringList(HIERARCHY_CONFIG_KEY).stream()
                // map each path to a configuration
                .map(this::config)
                // use the previous config as the fallback for the next
                .reduce(ConfigFactory.empty(), (previous, next) -> next.withFallback(previous));

    }
}
