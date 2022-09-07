# Building and Running Live Data Demo

The live-data demo from `config/odin-live.conf` shows the following input sources

 * (optional) aircraft (ADS-B) tracking
 * fire detection from GOES-R (geostationary) satellites
 * fire tracking with JPSS (polar orbiting) satellites

<img class="center scale80" src="images/live-demo.png">

To run the demo please follow these steps:

 1. [Check Prerequisites for Building and Running RACE-ODIN](prerequisites.md)
 2. [Create Common Root Directory for RACE/ODIN components](common-root.md) 
 3. [Obtain and Build RACE](building-race.md)
 4. [Obtain and Build RACE-ODIN](building-race-odin.md)
 5. [Obtain and Configure Live Data Prerequisites](#obtain-and-configure-live-data-prerequisites)
 6. [Start ODIN Live Data Demo](#start-odin-live-data-demo)

## Obtain and Configure Live Data Prerequisites
The live data demo currently requires access configuration for the following external servers
 
 * (optional) ADS-B host
 * `https://space-track.org` - to obtain orbit parameters for JPSS satellites
 * `https://firms.modaps.eosdis.nasa.gov` - to obtain active fire data products from JPSS satellites

In addition the [Orekit](https://www.orekit.org/) library needs ephemeris data to compute orbits of JPSS satellites.

### Configuring ADS-B (flight tracking) host
The demo optionally reads flight tracking information from a host that runs the open source [dump1090](https://github.com/flightaware/dump1090)
demodulator/decoder, which currently builds on Linux and macOS. This decoder requires an antenna and an 
[SDR (software defined radio) dongle](https://flightaware.store/products/pro-stick-plus).

While there are several options of how to build such a system details are outside the scope of this documentation. Please
refer to these [instructions](https://flightaware.com/adsb/piaware/build) for details.

Once you have access to such a system, configuring RACE-ODIN is just a matter of adding hostname and port to the 
'Private Configuration' vault file : 

    secret {
      ...
      adsb {
        host="<your-adsb-host-url>"
        port = 30003
      }
      ...
    }

Since this data source requires more effort the demo considers it optional. If there is no valid adsb.host ODIN
will product the following warnings on startup but will otherwise run showing the satellite data.

    [ERR]  trackImporter: failed to initialize: java.net.ConnectException: Connection refused
    ...
    [WARN] odin-live: initialization of trackImporter failed: rejected


### Obtaining a space-track.org Account
This website provides access to orbital parameters in form of [two line elements](https://en.wikipedia.org/wiki/Two-line_element_set). Accounts
are free and can be obtained from [this page](https://www.space-track.org/auth/createAccount).

Once you have the account information add the following to your [Private Configuration][priv]

    secret {
      ...
       spacetrack {
        authentication="identity=<your-email>&password=<your-password>"
      }
      ...
    }

### Obtaining a Map Key from FIRMS
In order to retrieve active fire data from polar orbiting satellites you have to obtain a map key from [this page](https://firms.modaps.eosdis.nasa.gov/usfs/api/area/)
(scroll to the bottom of the page and click the "Get MAP_KEY" button). Keys are free. Once you have otained the key
add the following to your [Private Configuration][priv]

    secret {
      ...
      firms {
        map-key="<your-map-key>"
      }
      ...
    }

### Download Orekit Ephemeris Data
`race-odin` uses the [Orekit](https://www.orekit.org/) library to compute orbits for the JPSS satellites. This library
requires ephemeris that needs to be downloaded and updated. Follow these steps to get the data:

  1. switch to the `race-odin` parent directory
  2. clone  the `orekit-data` repository by executing  `git clone https://gitlab.orekit.org/orekit/orekit-data.git`

Please note this directory needs to be updated periodically. You can either use the `update.sh` script that is part of the repository
or execute `git pull` from within it.

If you keep the name (`orekit-data`) and location (parent directory of `race-odin`) no further configuration is required.


### Start ODIN Live Data Demo
Please execute the following from within the `race-odin` directory:

    ./odin --vault ../<my-vault> config/odin-live-demo.conf

Once the server is running, switch to a browser (Chromium based browsers have the best support for WebGL as of this writing) and
go to <http://localhost:9000/odin>.

For more details see [Running RACE-ODIN Applications](running-odin.md).

[priv]: private-configuration.md