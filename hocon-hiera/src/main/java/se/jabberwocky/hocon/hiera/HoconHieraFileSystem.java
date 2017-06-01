package se.jabberwocky.hocon.hiera;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * HOCON Hiera implementation backed by a FileSystem
 */
public class HoconHieraFileSystem implements HoconHiera {

    public static final String HIERA_CONFIG_FILE = "hiera.conf";

    private final FileSystem fileSystem;
    private final Path root;

    public HoconHieraFileSystem(String root) {
    }

    public HoconHieraFileSystem(Path root) {
        this(FileSystems.getDefault(), root);
    }

    public HoconHieraFileSystem(FileSystem fileSystem, Path root) {
        this.fileSystem = fileSystem;
        this.root = root;
    }

    @Override
    public Config hiera() {
        return config(HIERA_CONFIG_FILE);
    }

    @Override
    public Config config(String file) {
        Path path = root.resolve(file);
        if(Files.exists(path)) {
            return ConfigFactory.parseFile(path.toFile());
        } else {
            // FIXME log warning "The facet value does not match a config file"
            return ConfigFactory.empty();
        }
    }

}
