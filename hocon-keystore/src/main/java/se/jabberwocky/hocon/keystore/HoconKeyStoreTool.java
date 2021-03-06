package se.jabberwocky.hocon.keystore;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import joptsimple.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;

import static java.lang.System.exit;

public class HoconKeyStoreTool implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(HoconKeyStoreTool.class.getName());

    private static final int SUCCESS = 0;
    private static final int OPTIONS_ERROR = 3;
    private static final int WRONG_NUMBER_OF_ARGUMENTS = 4;
    private static final int UNHANDLED_EXCEPTION = 4;

    private static final OptionParser parser = new OptionParser();

    private static final OptionSpec<File> keystoreSpec = parser.accepts(
            "keystore","keystore file" )
            .withRequiredArg()
            .required()
            .ofType(File.class);

    private static final OptionSpec<String> passwordSpec = parser.accepts(
            "password", "Keystore password" )
            .withRequiredArg()
            .describedAs("password")
            .required()
            .ofType(String.class);

    private static final OptionSpec<Void> createSpec = parser.accepts(
            "create","Optionally create keystore" );

    private static final OptionSpec<Void> replaceSpec = parser.accepts(
            "replace-config", "Optionally replace the configuration " +
                    "file for redact and reveal");

    private static final OptionSpec<Void> jsonSpec = parser.accepts(
            "json", "Optionally print HOCON as JSON" );

    private static final OptionSpec<KeyStoreType> typeSpec = parser.accepts(
            "store-type",
            "Keystore type overriding type deduced from file type, either "
                    + KeyStoreType.PKCS12 + " or " + KeyStoreType.JCEKS)
            .withOptionalArg()
            .ofType(KeyStoreType.class)
            .defaultsTo(KeyStoreType.JCEKS);;

    public static final int DEFAULT_SECRET_KEY_SIZE = 2048;
    private static final OptionSpec<Integer> keySizeSpec = parser.accepts(
            "key-size",
            "Length of a generated key in bytes")
            .withOptionalArg()
            .ofType(Integer.class)
            .defaultsTo(DEFAULT_SECRET_KEY_SIZE);

    public static final String DEFAULT_SECRET_KEY_ALGORITHM = "HMacSHA256";
    private static final OptionSpec<String> keyAlgSpec = parser.accepts(
            "key-alg",
            "Algorithm used when generating a key")
            .withOptionalArg()
            .ofType(String.class)
            .defaultsTo(DEFAULT_SECRET_KEY_ALGORITHM);

    private static final NonOptionArgumentSpec<String> nonOptionsSpec = parser.nonOptions(
            "Supported comands:\n\n" +
                "  get <key>               get an entry\n" +
                "  del <key>               delete an entry\n" +
                "  put <key>=<value>       update an entry\n" +
                "  generate <key>          generate a secret key\n" +
                "  update <config>         update entries in keystore\n" +
                "  upsert <config>         insert or update entries in keystore\n" +
                "  redact <config>         redact entries from keystore\n" +
                "  reveal <config>         reveal entries from keystore\n")
                .ofType(String.class)
                .describedAs("<command> <argument>");

    // -- fields

    private HoconKeyStoreEditor editor;
    private Path keystore;
    private final String command;
    private final String argument;
    private final boolean replace;
    private final boolean json;
    private final String algorithm;
    private final int size;

    public HoconKeyStoreTool(Path keystore, HoconKeyStoreEditor editor,
                             String command, String argument,
                             boolean replace, boolean json, String algorithm, int size) {

        this.keystore = keystore;
        this.editor = editor;
        this.command = command;
        this.argument = argument;
        this.replace = replace;
        this.json = json;

        this.algorithm = algorithm;
        this.size = size;
    }

    public static void main(String... args) throws IOException {
        try {
            OptionSet options = parser.parse( args );
            List<?> nonOptionArguments = options.nonOptionArguments();

            if(nonOptionArguments.size() != 2) {
                System.err.println("There should be exactly one command and one argument!");
                printHelp();
                exit(WRONG_NUMBER_OF_ARGUMENTS);
            }

            File keystore = options.valueOf(keystoreSpec);
            String password = options.valueOf(passwordSpec);
            KeyStoreType type = getStoreType(options);
            String algorithm = options.valueOf(keyAlgSpec);
            int size = options.valueOf(keySizeSpec);
            boolean create = options.has(createSpec);
            boolean replace = options.has(replaceSpec);
            boolean json = options.has(jsonSpec);

            String command = (String) nonOptionArguments.get(0);
            String argument = (String) nonOptionArguments.get(1);

            // FIXME change keystore opt to Path type
            Path path = Paths.get(keystore.getPath());

            HoconKeyStoreEditor editor = create
                    ? HoconKeyStoreEditor.create(password, type)
                    : HoconKeyStoreEditor.from(path, password, type);

            HoconKeyStoreTool tool = new HoconKeyStoreTool(
                    path, editor,
                    command, argument,
                    replace, json, algorithm, size);

            tool.run();
            exit(SUCCESS);
        } catch(OptionException | IllegalArgumentException e) {
            System.err.print(buildErrorMessage(e));
            printHelp();
            exit(OPTIONS_ERROR);
        } catch(Exception e) {
            e.printStackTrace(System.err);
            exit(UNHANDLED_EXCEPTION);
        }
    }

    private static KeyStoreType getStoreType(OptionSet options) {

        String name = options.valueOf(keystoreSpec).getName();
        KeyStoreType deduced = KeyStoreType.fromFilename(name);

        if(options.has(typeSpec)) {
            KeyStoreType given = options.valueOf(typeSpec);
            if(given != deduced) {
                LOGGER.warning("The given keystore type of " + given
                        + " is different from the type " + deduced
                        + " deduced from the file extension");
            }
            return given;
        }

        if(deduced == KeyStoreType.UNKNOWN) {
            LOGGER.warning("Unknown keystore type");
        } else {
            LOGGER.info("Deduced the type " + deduced + " from the keystore file extension");
        }

        return deduced;
    }

    private static String buildErrorMessage(Throwable cause) {
        StringBuilder message = new StringBuilder();
        while(cause != null) {
            message.append(cause.getClass().getName());
            message.append(": '");
            message.append(cause.getMessage());
            message.append("'\n");
            cause = cause.getCause();
        }
        return message.toString();
    }

    static void printHelp() throws IOException {
        Config config = ConfigFactory.load();
        System.err.println();
        System.err.println("HOCON Keystore Tool " + config.getString("hocon.keystore.version"));
        System.err.println();
        parser.printHelpOn(System.err);
    }

    @Override
    public void run() {
        switch(command) {
            case "get":
                get(argument); break;
            case "put":
                put(argument); break;
            case "del":
                del(argument); break;
            case "generate":
                generate(argument); break;
            case "upsert":
                manageKeystore(config -> editor.upsert(config)); break;
            case "update":
                manageKeystore(config -> editor.update(config)); break;
            case "redact":
                manageConfig(config -> editor.redact(config)); break;
            case "reveal":
                manageConfig(config -> editor.reveal(config)); break;
            default:
                throw new IllegalArgumentException("Unknown command");
        }
    }

    private void manageConfig(Function<Config,Config> callback) {
        Config config = handle(callback);
        if(replace) {
            replace(config);
        } else {
            print(config);
        }
    }

    private <T> T manageKeystore(Function<Config,T> callback) {
        T t = handle(callback);
        editor.to(keystore);
        return t;
    }

    private void print(Config config) {

        ConfigRenderOptions options = getConfigRenderOptions();

        String configString = config.root().render(options);
        System.out.println(configString);

    }

    private ConfigRenderOptions getConfigRenderOptions() {
        return ConfigRenderOptions.defaults()
                    .setFormatted(true)
                    .setOriginComments(false)
                    .setJson(json)
                    .setComments(!json);
    }

    private void replace(Config config) {
        try {

            String string = config.root().render(getConfigRenderOptions());
            Path tempFile = Files.createTempFile("hocon-", ".p12");
            Files.write(tempFile, string.getBytes());

            Path configFile = FileSystems.getDefault().getPath(argument);
            Files.move(tempFile, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        } catch (IOException e) {
            throw new RuntimeException("Could not replace file", e);
        }
    }

    private <T> T handle(Function<Config, T> callback) {
        File configFile = new File(argument);
        Config config = ConfigFactory.parseFile(configFile);

        return callback.apply(config);
    }

    private void del(String argument) {
        editor.del(argument).to(keystore);;
    }

    private void generate(String argument) {
        editor.generate(argument, algorithm, size).to(keystore);;
    }

    private void put(String argument) {
        String[] array = argument.split("=", 2);
        if(array.length != 2) {
            throw new IllegalArgumentException("Expected a key and value separated by an equals sign");
        }
        editor.put(array[0], array[1]).to(keystore);
    }

    private void get(String argument) {
        System.out.println(editor.get(argument));
    }

}
