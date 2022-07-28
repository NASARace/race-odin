# Building and Running the RACE-ODIN Demo

### How to install and build prerequisites and components
If this is the first time you install RACE or RACE-ODIN you have to get a number of prerequisites. Eventually it will
be sufficient to just update and build the RACE-ODIN project.

Please note that RACE-ODIN at this point still requires to fetch and locally publish the underlying RACE 
repository. Once components will have stabilized we will publish binary RACE artifacts on mvnrepository.com and this
step can be omitted.

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

To subsequently update RACE run from its directory

    git pull
    sbt publishLocal

#### (6) obtain and build RACE-ODIN
Within your RACE **root** directory execute:

    git clone https://github.com/NASARace/race-odin
    cd race-odin
    sbt stage

Please note that if you later-on update RACE you should also rebuild RACE-ODIN. To make sure RACE-ODIN is picking 
up the new RACE binaries run from within the RACE-ODIN directory:

    git pull
    sbt clean stage

#### (7) obtain RACE-DATA
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
On Unix/Linux/macOS run in a console from within the `race-odin` directory

    ./odin config/odin-czu-demo.conf

On Windows run from a command prompt within the 'race-odin' directory

    script/odin.bat config\odin-czu-demo.conf


This should start the ODIN server and eventually display a command line menu:

    enter command [1:show universes, 2:show actors, 3:show channels, 4:send message, 5:set loglevel, 6:app menu, 7: pause/resume, 8:start, 9:exit]

The menu can always be obtained by hitting <enter>. To terminate the server, use '9' + <enter>. 

Once the server is running, switch to a browser (Chrome has the best support for OpenGL as of this writing) and
go to `http://localhost:9000/odin`. This should show the globe and automatically zoom in on the San Francisco Bay Area,
showing local aircraft movements as yellow symbols, a red GPS ground track, and green Sentinel sensors.

The first time you start the demo will be considerably slower since RACE has to retrieve and cache external (proxied) data.

Please note that RACE uses a configured cache directory for proxied content - see `cache-dir` setting in 
`config/*.conf` which sets this to `../cache` (i.e. it should reside under your race root dir). Since this
directory is used to store content such as map tiles it can consume considerable disk space. Make sure to delete it
if you don't use RACE or RACE-ODIN anymore.
