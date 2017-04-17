# Hocon Keystore
Hocon Keystore is a small java library for managing 
[HOCON](https://github.com/typesafehub/config) secrets in a Java keystore. Secrets are 
stored in JCEKS or the more portable PKCS12 (.p12) keystore format.

## Overview
Secrets in HOCON configuration files are **redacted**, i.e. **replaced by five 
or more asterisks**:

````
Config {
    NoSecret: "NO_SECRET"
    Redacted: "********"
}
````

Redacted configuration entries indicate that the entry is to be found in the keystore.

When the application starts redacted secrets are replaced by entries found in the 
keystore and thus made available to the application.

Secret keys are stored in the keystore under an alias corresponding to the 
HOCON configuration entry path. For the example secret above the path becomes

````
Config.Redacted
````

## API
The Hocon Keystore API is centered around the `KeyStoreConfigEditor` class that provides
a fluent API around a keystore. Typical usage in an application would look something
like this:

````java
InputStream stream = getClass().getResourceAsStream("/keystore.jceks");
Config redacted = ConfigFactory.load();
Config revealed = KeyStoreConfigEditor.from(stream, "CHANGEME", StoreType.JCEKS).reveal(config);

````
... where the `revealed` `Config` instance contains all secrets in clear text and is passed to the application code.

## Command line tool

The Java SE [keytool](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/keytool.html)
command does not support storing key secret keys in a keystore. To this end the 
command line utility `hocon-keystore.jar` can be used. This is an executable JAR that contains 
the Hocon Ketystore library including all its dependencies. It can easily be exceuted from the 
command line by using 

````
java -jar hocon-keytool.jar <options> <command> <argument>
````

Please see below for details on the available options, commands and arguments!

### Managing Hocon configuration files
keystore entries can be managed in bulk by supplying a HOCON configuration file. HOCON 
configuration files can also be redacted or have their secrets revealed.
 
#### Add or update all configuration entries
Add or update all entries from a configuration file to a keystore:
````
java -jar hocon-keytool.jar \ 
    --password CHANGEME \
    --keystore keystore.jceks \
    upsert application.conf
````
#### Update all configuration entries
Update all entries from a configuration file to a keystore:
````
java -jar hocon-keytool.jar \ 
    --password CHANGEME \
    --keystore keystore.jceks \
    update application.conf
````

**If the entries do not exist in the keystore the update will fail 
and the keystore is left unchanged**

#### Redact all configuration entries
Redact all configuration entries in a configuration file that can be found 
in the keystore:
````
java -jar hocon-keytool.jar \ 
    --password CHANGEME \
    --keystore keystore.jceks \
    --replace \
    redact application.conf
````
If the optional parameter `--replace` is given the 
configuration file is replaced by the redacted content, else the redacted
content is written to std out.

#### Reveal all configuration entries
Reveal all redacted configuration entries in a configuration file that 
can be found in the keystore:
````
java -jar hocon-keytool.jar \ 
    --password CHANGEME \
    --keystore keystore.jceks \
    --replace \
    reveal application.conf
````

If the optional parameter `--replace` is given the 
configuration file is replaced by the revealed content, else the redacted
content is written to std out.

**The operation will fail if there are entries in the configuration file that do 
 match any entry in the keystore.**
 
### Managing individual entries
Secret key entries in the keystore can be managed individually. 

#### Add or update a secret entry to a keystore
````
java -jar hocon-keytool.jar \ 
    --password CHANGEME \
    --keystore keystore.jceks \
    put some.path.Secret=SECRET
````

#### Get a secret entry from a keystore
````
java -jar hocon-keytool.jar \ 
    --password CHANGEME \
    --keystore keystore.jceks \
    get some.path.Secret
````

#### Delete a secret entry from a keystore
````
java -jar hocon-keytool.jar \ 
    --password CHANGEME \
    --keystore keystore.jceks \
    del some.path.Secret
````


## The JavaSE `keytool` command
The JavaSE 8 [keytool](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/keytool.html)
command kan be used to create different kinds of keystores 

### JCEKS
````
keytool -keystore keystore.jceks -genkey -alias client -storetype jceks
````
### PKCS12
````
keytool -keystore keystore.p12 -genkey -alias client -storetype pkcs12
````

## Reference
* [Oracle's Java Cryptography Architecture ](http://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#KeyStore)
* [Different types of keystore in Java -- Overview](http://www.pixelstech.net/article/1408345768-Different-types-of-keystore-in-Java----Overview)
* [Different types of keystore in Java -- JCEKS](http://www.pixelstech.net/article/1420439432-Different-types-of-keystore-in-Java----JCEKS)
* [Different types of keystore in Java -- PKCS12](http://www.pixelstech.net/article/1420427307-Different-types-of-keystore-in-Java----PKCS12)
