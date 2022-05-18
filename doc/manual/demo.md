# Building and running the demo application

### How to install and build prerequisites and components
If this is the first time you install RACE or RACE-ODIN you have to get a number of prerequisites. Eventually just
updating and building this repository will be required. 

Please note that `race-odin` at this time (04/2022) does require to fetch and locally publish the underlying RACE 
repository although we do publish binary RACE artifacts on mvnrepository.com and hence RACE will be just a normal 
managed dependency for `race-odin` once it's ODIN related components have stabilized.

#### (1) install a recent Java JDK
This should be a JDK version > 11 (tested with JDK 18), e.g. from [OpenJDK](https://openjdk.java.net/).
Please note the JDK can be installed within the user file system by extracting the downloaded archive and 
adding its `bin/` directory to the `PATH` environment variable.

#### (2) install the SBT build tool
Please visit the  [SBT download page](https://www.scala-sbt.org/download.html) for instructions of how to download
and install the SBT build tool. If there is no suitable package for your operating system obtain the generic *.zip archive,
unpack and add its `bin/` directory to the `PATH` (SBT is Java based).

#### (3) make sure GIT and the Git LFS extension are installed on your system
If the Git version control system is not installed on your system, please visit the 
[Git download page](https://git-scm.com/downloads) for instructions.

If you don't have Git-LFS installed (the Git extension to handle large binary files), please follow instructions on
the [Git LFS](https://git-lfs.github.com) page. Please do not forget to run `git lfs install` after installation.

#### (4) create a common root directory for RACE repositories
While not strictly required it is recommended to keep all RACE related projects under a common root directory, e.g.

    mkdir race-root
    cd race-root

#### (5) obtain and build RACE
(a) Obtain RACE:

    git clone https://github.com/NASARace/race

(b) build RACE:

    cd race
    sbt publishLocal

Please note that if you subsequently update RACE from its github repository you also need to rebuild RACE-ODIN. If
the RACE version has not changed this requires to run `sbt clean stage` to build RACE-ODIN so that it picks up the
new RACE binaries.

#### (6) obtain a Cesium access token
The RACE-ODIN demonstration uses the open source [CesiumJS](https://cesium.com/platform/cesiumjs/) virtual globe for
browser based visualization. In order to properly initialize some of the Cesium functions used by ODIN you need to obtain
a *cesium access token*. Please follow instructions on the [CesiumJS tutorial](https://cesium.com/learn/cesiumjs-learn/cesiumjs-quickstart/#step-1-create-an-account-and-get-a-token)
page.

#### (7) create a RACE vault
RACE is using a separate (optionally) [encrypted configuration](http://nasarace.github.io/race/usage/encryption.html)) file
(known as the *vault*) to store sensitive configuration values outside of repositories. For our demo purposes we can 
create a single, un-encrytped `cesium-vault` text file in the RACE root directory created in step (4):

    secret {
      cesium {
        access-token="<your-cesium-access-token>"
      }
    }

#### (8) obtain and build RACE-ODIN
Within your RACE root directory execute:

    git clone https://github.com/NASARace/race-odin
    cd race-odin
    sbt stage

#### (9) obtain RACE-DATA
Since RACE archives for test data can get quite large we keep them outside of RACE or ODIN in a separate `race-data`
repository. Make sure you have the git-lfs extension installed and initialized (see step (3)) and execute in the
RACE root directory

    git clone https://github.com/NASARace/race-data

There is no need to build anything since this repository only contains data archives. You should now have the following
directory structure:

    race-root/
        cesium-vault
        race/
        race-data/
        race-odin/


### How to run the RACE-ODIN demo
from within the `race-odin` directory execute

    ./odin --vault ../cesium-vault config/odin-demo.conf

This should start the ODIN server and eventually display a command line menu:

    enter command [1:show universes, 2:show actors, 3:show channels, 4:send message, 5:set loglevel, 6:app menu, 7: pause/resume, 8:start, 9:exit]

The menu can always be obtained by hitting <enter>. To terminate the server, use '9' + <enter>. 

Once the server is running, switch to a browser (Chrome has the best support for OpenGL as of this writing) and
go to `http://localhost:9000/odin`. This should show the globe and automatically zoom in on the San Francisco Bay Area,
showing local aircraft movements as yellow symbols, a red GPS ground track, and green Sentinel sensors.

The first time you start the demo will be considerably slower since RACE has to retrieve and cache external (proxied) data.

Please note that RACE uses a configured cache directory for proxied content - see `cache-dir` setting in 
`config/odin-demo.conf` which sets this to `../cache` (i.e. it should reside under your race root dir). Since this
directory is used to store content such as map tiles it can get big. This cache has to be reset/deleted manually if you
don't use `race-odin` anymore.
