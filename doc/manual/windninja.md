# How to Build WindNinja

<br>
<hr>
**NOTE 12/2023**
The following steps refer to the Scala version of ODIN. This is going to be simplified in the upcoming
Rust version of ODIN (see [Why is ODIN ported to Rust]).
<hr>
<br>

ODIN demos make use of the [WindNinja](https://www.firelab.org/project/windninja) micro-grid wind simulator
from [Missoula FireLab](https://www.firelab.org/). Since we need a raw (height,u,v,w) output mode this requires to
obtain a WindNinja variant that is not yet merged back into the main WindNinja repository.

Note that the build requires [Git](https://git-scm.com/), [CMake](https://cmake.org/) and a working C/C++ compiler as pre-requisites.

We also need a working installation of the [GDAL](https://gdal.org/) library and executables, which should be
obtained through your operating specific package management system (e.g. [homebrew](https://brew.sh/) on MacOS
or [vcpkg](https://vcpkg.io/en/) on Windows). Once installed (you need the shared library and the executables) you
can verify by running `gdalinfo --help` from the command line.

To obtain and build [our WindNinja fork](https://github.com/pcmehlitz/windninja.git), please follow these steps on a 
Linux/Unix/MacOS system:
```
    # choose a build/install directory
    mkdir micro-wind
    cd micro-wind
    git clone https://github.com/pcmehlitz/windninja.git

    # build windninja
    mkdir build
    cd build
    cmake -DCMAKE_BUILD_TYPE=Release -DNINJA_CLI=ON -DNINJA_QTGUI=OFF ../windninja
    cmake --build .
    
    # basic test
    src/cli/WindNinja_cli --help
```

Since ODIN needs to transform the raw WindNinja output (h,u,v,w GeoTiff file) into CSV follow these steps
to build from sources that are included in the RACE repository.

```
    # switch to the gdalutil directory within your RACE clone
    cd $RACE_ROOT/race-earth/src/main/c++/gdalutil
    
    # build gdalutil
    mkdir build
    cd build
    cmake -DCMAKE_BUILD_TYPE=Release ..
    cmake --build .
    
    # basic test
    src/huvw_csv_grid
```

Once the `WindNinja_cli`, `huvw_csv_grid` and `huvw_csv_vector` executables have been built we have to tell
ODIN where to find them. The easiest way to do this is to add a 

```
   executable-paths = "../race-executables"
```

line to respective config files, create a `race-executables` directory above your ODIN directory and then create
symbolic links in there to the executables built above:

```
   # from ODIN/RACE root
   cd ..
   mkdir race-executables
   cd race-executables
   
   # adapt to your WindNinja/RACE directory choices
   ln -s ../micro-wind/build/src/cli/WindNinja_cli WindNinja_cli
   ln -s ../race/race-earth/src/main/c++/gdalutil/build/src/huvw_csv_grid huvw_csv_grid
   ln -s ../race/race-earth/src/main/c++/gdalutil/build/src/huvw_csv_vector huvw_csv_vector
```

(please not `ln` is Linux/MacOS, symbolic links on Windows are created with `mklink`)
