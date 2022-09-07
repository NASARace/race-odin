# Check Prerequisites for Building and Running RACE-ODIN

If this is the first time you install RACE or RACE-ODIN you have to check or get a number of prerequisites, namely:

  * a recent **Java SDK**
  * the **SBT** build tool
  * the **Git** version control system and its **Git-Lfs** extension
  * obtain a Cesium Ion access token

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
your 'Private Configuration'.