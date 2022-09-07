# Obtain and Build RACE

This is just a standard install of RACE, which is described on [this page](http://nasarace.github.io/race/installation/build.html).
In short, it involves two steps:

### 1. obtain RACE:

    cd race-root     # go to common root dir created in step 1
    git clone https://github.com/NASARace/race

### 2. build RACE:

    cd race
    sbt publishLocal

To subsequently update RACE run this from its directory

    git pull
    sbt publishLocal

The `publishLocal` step is required *before* building an updated RACE-ODIN