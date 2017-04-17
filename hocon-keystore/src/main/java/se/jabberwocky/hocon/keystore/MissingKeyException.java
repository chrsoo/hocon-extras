package se.jabberwocky.hocon.keystore;

import com.typesafe.config.ConfigValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

public class MissingKeyException extends RuntimeException {

    private List<Entry<String,ConfigValue>> missingEntries;

    public MissingKeyException(List<Entry<String, ConfigValue>> missingEntries) {
        super("Could not find one or more entries in the key store:");
        this.missingEntries = missingEntries;
    }

    public MissingKeyException(String key) {
        super("Could not find the key '" + key + "'  in the key store:");
        this.missingEntries = new ArrayList<>();
    }

    @Override
    public String getMessage() {
        StringBuilder builder = new StringBuilder(super.getMessage());
        missingEntries.forEach(entry -> {
            builder.append("\n - '");
            builder.append(entry.getKey());
            builder.append("' key from ");
            builder.append(entry.getValue().origin().description());
        });
        return builder.toString();
    }

    @Override
    public String getLocalizedMessage() {
        return getMessage();
    }
}
