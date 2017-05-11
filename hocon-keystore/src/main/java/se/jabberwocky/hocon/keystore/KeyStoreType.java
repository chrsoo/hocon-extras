package se.jabberwocky.hocon.keystore;

public enum KeyStoreType {

    UNKNOWN,
    JKS,
    JCEKS,
    PKCS12,
    ;

    public static KeyStoreType fromFilename(String filename) {
        int dot = filename.lastIndexOf(".");
        if(dot == -1) {
            return UNKNOWN;
        }

        String suffix = filename.substring(dot).toLowerCase();

        switch (suffix) {
            case ".jks":
                return JKS;
            case ".jceks":
                return JCEKS;
            case ".p12":
            case ".pfx":
                return PKCS12;
            default:
                return UNKNOWN;
        }
    }
}
