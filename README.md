## RACE-ODIN

The purpose of this project is to show how to build Open Data Integration (ODIN) applications with 
[RACE](http://nasarace.github.io/race/) in the context of  wildland fire management. It specifically serves as a 
template to demonstrate how to add components (actors and supporting code) outside of the RACE code base, using
[Delphire Sentinel](https://delphiretech.com/sentinel) powerline fire sensors as the integration example. 

### How to install and build prerequisites and components
If this is the first time you install RACE or RACE-ODIN you have to get a number of prerequisites. Proceed in the
following sequence.

#### (1) install a contemporary Java JDK
This should be a JDK version > 11 (tested with JDK 18), e.g. from [OpenJDK](https://openjdk.java.net/).
Please note the JDK can be installed within the user file system by extracting respective downloads and adding their `bin` 
directory to the `PATH` environment variable.

#### (2) install the SBT build tool
Please visit the  [SBT download page](https://www.scala-sbt.org/download.html) for instructions of how to download
and install the build tool. If there is no suitable package for your operating system obtain the generic *.zip archive,
unpack and add its `bin` directory to the `PATH` (SBT is Java based).

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

#### (6) obtain a Cesium access token
The RACE-ODIN demonstration uses the open source [CesiumJS](https://cesium.com/platform/cesiumjs/) virtual globe for
browser based visualization. In order to properly initialize web pages using this viewer you need to obtain
a *cesium access token*. Please follow instructions on the [CesiumJS tutorial](https://cesium.com/learn/cesiumjs-learn/cesiumjs-quickstart/#step-1-create-an-account-and-get-a-token)
page.

#### (7) create a RACE vault
RACE is using a separate (possibly) [encrypted configuration](http://nasarace.github.io/race/usage/encryption.html)) file
(known as the *vault*) to store sensitive information outside of repositories. For our demo purposes we can create a single,
un-encrytped `cesium-vault` text file in the RACE root directory created in step (4):

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
Since RACE archives for test data can get quite large we keep them outside of RACE in a separate *race-data*
repository. Make sure you have the git-lfs extension installed and initialized (see step (3)) and execute in the
RACE root directory

    git clone https://github.com/NASARace/race-data

You should now have the following directory structure

    race-root/
        cesium-vault
        race/
        race-data/
        race-odin/


### How to run the RACE-ODIN demo
from within the `race-odin` directory execute

    ./odin --vault ../cesium-vault config/odin-demo.conf

This should start the ODIN server and display a command line menu, which can always be restored by hitting <enter>. To
terminate the server, enter '9' for the exit menu option.

Once the server is running, switch to a browser (Chrome has the best support for OpenGL as of this writing) and
go to `http://localhost:9000/app`. This should show the globe and zoom in on the San Francisco Bay Area, showing 
local aircraft movements as yellow symbols, a red GPS ground track, and green Sentinel sensors.

