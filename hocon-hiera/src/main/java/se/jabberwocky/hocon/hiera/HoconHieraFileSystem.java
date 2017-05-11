package se.jabberwocky.hocon.hiera;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * HOCON Hiera implementation backed by a FileSystem
 */
public class HoconHieraFileSystem implements HoconHiera {

    public static final String HIERA_CONFIG_FILE = "hierarchy.conf";
    public static final String DEFAULT_CONFIG_FILE = "default.conf";

    private final FileSystem fileSystem;
    private final Path root;

    public HoconHieraFileSystem(String root) {
        this(Paths.get(root));
    }

    public HoconHieraFileSystem(Path root) {
        this(FileSystems.getDefault(), root);
    }

    public HoconHieraFileSystem(FileSystem fileSystem, Path root) {
        this.fileSystem = fileSystem;
        this.root = root;
    }


    @Override
    public List<String> hierarchy() {
        return root().getList(HIERARCHY_CONFIG_KEY).stream()
                .map(config -> config.unwrapped().toString())
                .collect(Collectors.toList());
    }

    @Override
    public Config root() {
        Path path = fileSystem.getPath(root.toString(), HIERA_CONFIG_FILE);
        return config(path);
    }

    @Override
    public Config config(String facet, String value) {

        if(value == null) {
            return config(fileSystem.getPath(root.toString(),facet, DEFAULT_CONFIG_FILE));
        }

        Path path = fileSystem.getPath(root.toString(),facet, value + ".conf");
        return config(path);

    }

    private Config config(Path path) {
        if(Files.exists(path)) {
            return ConfigFactory.parseFile(path.toFile());
        } else {
            throw new IllegalArgumentException("The facet value does not match a config file");
        }
    }
}
