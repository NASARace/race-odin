# Anatomy of an ODIN-fire application

The demo application included in this repository shows the typical components that are required to integrate
a new external data source - in this case [Delphire's Sentinel powerline fre monitoring system](https://delphiretech.com/sentinel).

What we import here is raw data. If an external service provides data in a generic display or
domain-aware format (such as KML or GeoJSON) we do not have to program anything but can use existing RACE/ODIN
components in order to incorporate such layers in our visualization. However, using generic display formats can
make it considerably harder to add specific visualization or non-visualization related functions such as monitoring.


The three basic functions for raw data integration are (1) data import, (2) internal data model and (3) data processing.
Each of these functions is implemented as a RACE_actor_ that can make use of the existing RACE libraries. The sources
can be found in the ``src/main/scala/gov/nasa/race/odin/sentinel/`` directory.

<img class="center scale75" src="images/sentinel-app.svg">

### 1. Data import

This is the component that obtains data from the external (edge) server, either through http, a higher level
protocol such as Java Messaging Service (JMS), or by direct socket communication. Please refer to 
[RACE import](http://nasarace.github.io/race/design/connectivity.html) documentation for general design and supported 
protocols. In many cases all that is required in order to use  existing RACE components is to provide a `reader` that 
de-serializes the data received from the external server into update events.

A standout feature of RACE is that it supports to archive what we receive, and then to later-on replay such archives
without requiring to access the external servers that data came from. This makes use of the 
[RACE Archive and Replay](http://nasarace.github.io/race/design/archive-replay.html) infrastructure. Replay provides 
control over start-time and time-scale. The important aspect here is that the only application change that is required 
is to swap the live import actor for a respective replay actor - the rest of the system stays the same.

In our example we replay an archive of simulated Sentinel devices (``SentinelReplayActor.scala``).


### 2. Internal Data Model

This actor reads the  update events we receive from the import actor and accumulates them into an internal
data model representing the states of known Sentinel devices. Upon startup, the actor tries to retrieve the
last known state of these devices from local storage. During operation and upon termination this local
storage is updated so that connectivity loss does not lead to losing the last known state. This actor
typically first updates the data model and then emits a snapshot of the complete state, followed by the update
events that lead to the new state snapshot. This allows clients such as monitor actors to efficiently react
to specific state changes.

The sources implementing our example data model are ``Sentinel.scala``, ``SentinelSensorReading.scala`` and
``SentinelUpdateActor.scala``. 

### 3. Data Processing

This represents what to do with the accumulated data and respective changes. In our example we provide
the Sentinel data as a micro-service that can be shown as a layer on top of a virtual globe in a browser.
While this involves a lot of functionality the vast majority resides within the 
[RACE HttpServer infrastructure](http://nasarace.github.io/race/design/http-server.html). Here we just have to implement 
the ``RaceRouteInfo`` that deals with the Sentinel specifics, namely 

  * providing related document fragments (user interface components)
  * serving related assets (such as symbols and browser script modules)
  * pushing new Sentinel data through websockets to the connected clients

In our example this can be found in ``SentinelRoute.scala``.
