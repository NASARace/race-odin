# Create Common Root Directory for RACE/ODIN components

While it is not required it is recommended to put all RACE and RACE-ODIN related projects under a common root directory.
This not ony pertains to the two projects itself but also to the data and configuration files that are used by them.

Location and name for this directory can be chosen freely. The only manual action for this step is to create a root
directory, e.g. by running from a command prompt

    > mkdir race-root

Ultimately you should have a structure such as

    <race-root>/
    │                    #--- basic projects:
    ├─╴    race/             # '> git clone https://github.com/NASARace/race.git'
    ├─╴    race-odin/        # '> git clone https://github.com/NASARace/race-odin.git'
    │
    │                    #--- for archive replay demo:
    ├─╴    race-data/        # '> git clone https://github.com/NASARace/race-data.git' (using git-lfs)
    │
    │                    #--- for live data demo:
    ├─╴    orekit-data/      # '> git clone https://gitlab.orekit.org/orekit/orekit-data.git'
    │
    │                    #--- local configuration
    ├─╴    <my-vault>        # manually created text file - see respective build pages for entries
    ├─╴    local-config/     # optional - to keep locally modified *.conf files 
    │
    │                    #--- auto created when running RACE or RACE-ODIN
    └─╴    cache/            # automatically created/populated by RACE webserver 

The name for the *my-vault* file is your choice but has to be remembered since it has to be specified when running
RACE or RACE-ODIN, which typically involves a command sequence such as

    cd race-odin  
                             # for un-modified config files (e.g. race-odin/config/odin-live.conf):
    /odin --vault ../<my-vault> config/<some-config-file>
                             # for locally modified/created config files:
    /odin --vault ../<my-vault> ../local-config/<some-config-file>

Should this directory structure not be suitable (e.g. because of several installed RACE versions) all of the
above names can be modified and set in respective config files.

                          
