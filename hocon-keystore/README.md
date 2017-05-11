# Hocon Keystore
Hocon Keystore is a small java library for managing 
[HOCON](https://github.com/typesafehub/config) secrets in a Java keystore. Secrets are 
stored in JCEKS or the more portable PKCS12 (.p12) keystore format.

## Overview
Secrets in HOCON configuration files are **redacted**, i.e. **replaced by five 
or more asterisks**:

```
Config {
    NoSecret: "NO_SECRET"
    Redacted: "********"
}
```

Redacted configuration entries indicate that the entry is to be found in the keystore.

When the application starts redacted secrets are replaced by entries found in the 
keystore and thus made available to the application.

Secret keys are stored in the keystore under an alias corresponding to the 
HOCON configuration entry path. For the example secret above the path becomes

```
Config.Redacted
```
This approach has its limits when it comes to secrets stored in an array:

```
Config {
    Sites [
        {
            url: "http://loopback.edu/api"
            user: "anonymous"
            pass: "*****"
        }, {
            url: "http://vampiresquid.com/api"
            user: "goldman"
            pass: "*****"
        }
    ]
}
```
The best solution is probably to change the structure from an array to an object

```
Config {
    Sites {
        University {
            url: "http://loopback.edu/api"
            user: "anonymous"
            pass: "*****"
        } 
        Bank {
            url: "http://vampiresquid.com/api"
            user: "goldman"
            pass: "*****"
        }
    }
}
```
... but where this is not possible or desirable the secrets can be defined as placeholders in the array instead:
```
Config {
    University.Password: "*****"
    Bank.Password: "*****"
    Sites [
        {
            url: "http://loopback.edu/api"
            user: "anonymous"
            pass: ${University.Password}
        }, {
            url: "http://vampiresquid.com/api"
            user: "goldman"
            pass: ${Bank.Password}
        }
    ]
}
```
## API
The Hocon Keystore API is centered around the `KeyStoreConfigEditor` class that provides
a fluent API around a keystore. Typical usage in an application would look something
like this:
```java
InputStream stream = getClass().getResourceAsStream("/keystore.jceks");
Config redacted = ConfigFactory.load();
// HOCON autmatically maps system variables to config entries and the password can be passed to 
// the application using the java -D option, e.g. "-Dpassword=secret"
String password = redacted.getString("password");
Config revealed = KeyStoreConfigEditor.from(stream, password, StoreType.JCEKS).reveal(config);

```
... where the `revealed` `Config` instance contains all secrets in clear text and is passed to the application code.

## Command line tool

The Java SE [keytool](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/keytool.html)
command does not support storing key secret keys in a keystore. To this end the 
command line utility `hocon-keystore.jar` can be used. This is an executable JAR that contains 
the Hocon Ketystore library including all its dependencies. It can easily be exceuted from the 
command line by using 

```
java -jar hocon-keytool.jar <options> <command> <argument>
```
Running the tool without options and parameters produces the following help on its usage:

```
HOCON Keystore Tool 0.1-SNAPSHOT

Non-option arguments:                                        
[<command> <argument>] -- Supported comands:                 
  get <key>               get an entry                        
  del <key>               delete an entry                     
  put <key>=<value>       update an entry                     
  update <config>         update entries in keystore          
  upsert <config>         insert or update entries in keystore
  redact <config>         redact entries from keystore        
  reveal <config>         reveal entries from keystore        

Option (* = required)     Description                          
---------------------     -----------                          
--create                  Optionally create keystore           
--json                    Optionally print HOCON as JSON       
* --keystore <File>       PKCS12 keystore file                 
* --password <password>   Keystore password                    
--replace-config          Optionally replace the configuration 
                            file for redact and reveal         
--store-type [StoreType]  Keystore type overriding type deduced
                            from file type, either PKCS12 or   
                            JCEKS (default: JCEKS)
```

Please see below for details on the available commands and arguments!

### Managing HOCON keystores
Keystore entries can be managed in bulk by supplying a HOCON configuration file.
 
#### Add or update all configuration entries
All entries in the configuration file are added to the keystore. If an entry already exists, it is 
updated with the value from the configuration file.
```
java -jar hocon-keytool.jar --password CHANGEME --keystore keystore.jceks \
    upsert application.conf
```
#### Update all configuration entries
All entries in the keystore are updated with values from the configuration file.
```
java -jar hocon-keytool.jar --password CHANGEME --keystore keystore.jceks \
    update application.conf
```

**If entries do not exist in the keystore the update fails and the keystore is left unchanged**

### Managing HOCON configuration files
Secrets in HOCON configuration files can be redacted or revealed. The output is either given on stdout or the
configuration file can optionally be replaced by use of the `--replace-config` flag.
 
#### Redact all configuration entries
Redact all configuration entries in a configuration file that can be found 
in the keystore:
```
java -jar hocon-keytool.jar --password CHANGEME --keystore keystore.jceks \
    redact application.conf
```
If the optional parameter `--replace-config` is given the 
configuration file is replaced by the redacted content, else the redacted
content is written to std out.

#### Reveal all configuration entries
Reveal all redacted configuration entries in a configuration file that 
can be found in the keystore:
```
java -jar hocon-keytool.jar --password CHANGEME --keystore keystore.jceks \
    reveal application.conf
```

If the optional parameter `--replace-config` is given the 
configuration file is replaced by the revealed content, else the redacted
content is written to std out.

**The operation fails if there are entries in the configuration file that do match any entry in the keystore.**
 
### Managing individual entries in a keystore
Secret key entries in the keystore can be managed individually. 

#### Add or update a secret entry to a keystore
```
java -jar hocon-keytool.jar --password CHANGEME --keystore keystore.jceks \
    put some.path.Secret=SECRET
```

#### Get a secret entry from a keystore
```
java -jar hocon-keytool.jar --password CHANGEME --keystore keystore.jceks \
    get some.path.Secret
```

#### Delete a secret entry from a keystore
```
java -jar hocon-keytool.jar --password CHANGEME --keystore keystore.jceks \
    del some.path.Secret
```

## The JavaSE `keytool` command
The JavaSE 8 [keytool](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/keytool.html)
command kan be used to create different kinds of keystores 

### JCEKS
```
keytool -keystore keystore.jceks -genkey -alias client -storetype jceks
```
### PKCS12
```
keytool -keystore keystore.p12 -genkey -alias client -storetype pkcs12
```

## Reference
* [Oracle's Java Cryptography Architecture ](http://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#KeyStore)
* [Different types of keystore in Java -- Overview](http://www.pixelstech.net/article/1408345768-Different-types-of-keystore-in-Java----Overview)
* [Different types of keystore in Java -- JCEKS](http://www.pixelstech.net/article/1420439432-Different-types-of-keystore-in-Java----JCEKS)
* [Different types of keystore in Java -- PKCS12](http://www.pixelstech.net/article/1420427307-Different-types-of-keystore-in-Java----PKCS12)
