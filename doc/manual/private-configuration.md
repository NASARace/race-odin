# Private Configuration Vault

Some of the RACE-ODIN demos do need private configuration that should be kept outside the RACE repositories
(e.g. account info on external servers). This data should be kept in a [RACE *vault* file](http://nasarace.github.io/race/usage/encryption.html).

The name and location of this file can be chosen freely but we recommend to keep it under the [common root directory](common-root.md)
since it is shared between different applications/projects. Since the file contains private data and is usually encrypted
we refer to it as the **vault**.

The format of the vault file is the usual [HOCON](https://github.com/lightbend/config/blob/master/HOCON.md) that is used
for all RACE or RACE-ODIN configurations, only that it contains one outer `secret` block that wraps the whole file:

    secret {
       // your private config options go here
       ...
    }

In production systems the vault file is normally encrypted (producing files that end with `*.crypt`) but for the purpose
of the RACE-ODIN demos there is no sensitive information, hence the file can be created with a normal text editor. 
You can choose the filename but don't use a `.crypt` extension.

A full vault file for all RACE-ODIN demos looks like this:

    secret {
      cesium {
        access-token="<your-cesium-access-token>"
      }
      spacetrack {
        authentication="identity=<your-email>&password=<your-password>"
      }
      firms {
        map-key="<your-map-key>"
      }

      // optional
      adsb {
        host="<your-adsb-host-url>"
        port = 30003
      }
    }