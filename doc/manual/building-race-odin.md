# Obtain and Build RACE-ODIN

This follows the standard procedure of building RACE dependent projects, which includes the following steps:

### obtain RACE-ODIN

    cd race-root     # go to common root dir created in step 1
    git clone https://github.com/NASARace/race-odin


### build RACE-ODIN

    cd race-odin
    sbt stage

Please note that if you later-on update RACE you should also rebuild RACE-ODIN. To make sure RACE-ODIN is picking
up the new RACE binaries run from within the RACE-ODIN directory (*after* a previous `sbt publishLocal` of RACE):

    git pull
    sbt clean stage

