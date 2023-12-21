# Building and Running Archive Replay Demo

<hr>
**NOTE 12/2023**
Building and running demos is going to become significantly more simple in the upcoming Rust version
of ODIN (see [Why is ODIN ported to Rust])
<hr>

The archived data demo from `config/odin-czu-demo.conf` shows a variety of input sources including

 * satellite fire detection (from the [CZU fire](https://en.wikipedia.org/wiki/CZU_Lightning_Complex_fires))
 * powerline fire sensors ([Delphire Sentinel](https://delphiretech.com/))
 * local flight and ground tracking
 * static information such as powerline locations, [OSM buildings](https://osmbuildings.org/)
 * NOAA wind forecast

<img class="center scale80" src="images/archive-demo.png">

To run the demo please follow these steps:

1. [Check Prerequisites for Building and Running RACE-ODIN](prerequisites.md)
2. [Create Common Root Directory for RACE/ODIN components](common-root.md)
3. [Obtain and Build RACE](building-race.md)
4. [Obtain and Build RACE-ODIN](building-race-odin.md)
5. Obtain and Build WindNinja (see [How to Build WindNinja])
6. [Obtain RACE-DATA](#obtain-race-data)
7. [Start ODIN Live Data Demo](#start-odin-live-data-demo)

## Obtain RACE-DATA
This project only contains archived data. To retrieve it you need to have [Git-lfs](https://git-lfs.github.com/)
installed, which is platform dependent and might require admin privileges. Please refer to the [Git-lfs](https://git-lfs.github.com/)
website and don't forget to run `git-lfs install` from a command line before cloning repositories.

Once Git-lfs is installed you can download [RACE-DATA](https://github.com/NASARace/race-data) by executing from a
command line

    cd race-root   # or what you chose as the common root dir in step 2
    git clone https://github.com/NASARace/race-data.git

Note there is no need to build anything as this is a data-only repository.

## Start ODIN Live Data Demo
Apart from the [Cesium access token](cesium-access-token.md) there is no further vault entry to the [Private Configuration][priv]
required.

To start the demo server run the following from within the `race-odin` directory:

    ./odin --vault ../<my-vault> config/odin-czu-demo.conf

Once the server is running, switch to a browser (Chromium based browsers have the best support for WebGL as of this writing) and
go to <http://localhost:9000/odin>.
For more details see [Running RACE-ODIN Applications](running-odin.md).

[priv]: private-configuration.md