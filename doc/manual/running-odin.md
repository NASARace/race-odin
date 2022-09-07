# Running RACE-ODIN Applications

Just as with RACE, running applications requires a launcher (e.g. the `race-odin/odin` script) and a config file
that specifies the application (e.g. `race-odin/config/odin-live.conf`), e.g.:

    cd race-odin
    ./odin config/odin-czu-demo.conf




The private config file to use is specified with the `--vault <pathName>` command line option, e.g.: 

    cd race-odin
    ./odin --vault ../my-vault config/odin-live-demo.conf


This should start the ODIN server and display a command line menu:

    enter command [1:show universes, 2:show actors, 3:show channels, 4:send message, 5:set loglevel, 6:app menu, 7: pause/resume, 8:start, 9:exit]

The menu can always be obtained by hitting <enter>. To terminate the server type `9 + <enter>`.

Once the server is running, switch to a browser (Chrome has the best support for WebGL as of this writing) and
go to `http://localhost:9000/odin`. Clicking on the icons in the upper left will open/close overlay windows for
respective data sources.

The first time you start the demo will be considerably slower since RACE has to retrieve and cache external (proxied) data
such as map tiles.