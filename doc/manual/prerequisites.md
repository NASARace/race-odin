# Check Prerequisites for RACE-ODIN

If this is the first time you install RACE or RACE-ODIN you have to check or get a number of prerequisites, namely:

  * a recent **Java SDK**
  * the **SBT** build tool
  * the **Git** version control system and its **Git-Lfs** extension
  * obtain a Cesium Ion access token
  * (optionally) CMake and C/C++ compilers to build native 3rd party executables

### 1. install a recent Java JDK
This should be a JDK version > 11 (tested with JDK 18.x), e.g. from [OpenJDK](https://openjdk.java.net/).
Please note the JDK can be installed within the user file system by extracting the downloaded archive and
adding its `bin/` directory to the `PATH` environment variable.

The JDK runtime libraries (which are part of the JDK) are the only prerequisite for running and already built RACE-ODIN system.


### 2. install the SBT build tool
Please visit the  [SBT download page](https://www.scala-sbt.org/download.html) for instructions of how to download
and install the SBT build tool. If there is no suitable package for your operating system obtain the generic *.zip archive,
unpack and add its `bin/` directory to the `PATH` (SBT is Java based).


### 3. install Git and Git-LFS
If the Git version control system is not installed on your system, please visit the
[Git download page](https://git-scm.com/downloads) for instructions.

If you don't have Git-LFS installed (the Git extension to handle large binary files), please follow instructions on
the [Git LFS](https://git-lfs.github.com) page. Please do not forget to run `git lfs install` after installation.


### 4. Obtain Cesium Ion access token
Although not strictly required for occasionally running the basic RACE-ODIN demos it is recommended to obtain a free
Cesium Ion access token from <https://cesium.com/ion/signup/>. Once you have an access token, please enter it into
your [Private Configuration Vault].


### 5. Native Build Environment for 3rd party executables
If you want to use ODIN with native 3rd party executables such as [WindNinja](https://www.firelab.org/project/windninja)
(micro grid wind simulation from Missoula FireLab) and [dump1090](https://github.com/flightaware/dump1090) (ADS-B edge
server) you also need respective native build environments for your platform (make/[CMake](https://cmake.org/), a 
C/C++ compiler and required 3rd party libraries such as [GDAL](https://gdal.org/)). Those have to be installed through
operating specific package management systems (e.g.[homebrew](https://brew.sh/) on MacOS).
The [Building and Running Live Data Demo] page includes some descriptions of how to build those externals but 
platform-specific details are beyond the scope of this documentation. This is going  to be greatly simplified once 
we have ported ODIN to Rust (see [Why is ODIN ported to Rust]) 
