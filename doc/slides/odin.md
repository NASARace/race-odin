# ODIN Live Demo

website: <http://nasarace.github.io/race-odin><br/>
repository: <https://github.com/nasarace/race-odin><br/>

<p class="author">
Peter.C.Mehlitz@nasa.gov<br/>
NASA Ames Research Center
</p>

## Slides
@:navigationTree { entries = [ { target = "#" } ] }


## Current Situation
* integration: existing data in various locations, formats
* extensibility: no easy way to integrate own static and **dynamic** data 

<img src="./images/current.svg" class="center scale85">
 

## ODIN Vision
* **not** yet-another-web-site

<img src="./images/odin-node.svg" class="center scale70">


## ODIN Live Demo
* focus on import of data sources and formats, not (yet) on user interface and visualization

<img src="./images/live-demo.png" class="center scale85">

<!--
## ODIN Live Demo (not so live)
<video  width="85%" controls Autoplay=autoplay src="./images/odin-live-data.webm"></video>
-->

## ODIN Live Demo Data Flow
* live data using ADS-B tracking and GOES-R and JPSS fire products

<img src="./images/live-demo.svg" class="center scale60">


## More ODIN Applications
* example with archived data
* only import components are replaced
<img src="./images/czu-demo.png" class="center scale80">



## Why Open Source?
* *community* is larger than fire agencies (>600)
* provide common ground with low barrier of entry for stakeholders, vendors and research orgs

<img src="./images/odin-open.svg" class="center scale70"/>


## ODIN Foundation: Actor Programming Model
* well known concurrency programming model since 1973 (Hewitt et al)
* _Actors_ are objects that communicate only through async messages
  ⟹ no shared state
* objects process messages one-at-a-time ⟹ sequential code

<img src="./images/actor.svg" class="center scale55"/>


## ODIN Implementation: Actor System
* runs on JVM, programmed in Scala using Akka actor library
* ODIN node = set of communicating actors
* ODIN messages are sent through (logical) publish/subscribe **channels**
* ODIN actors/channels are runtime configured (JSON), not hardwired

<img src="./images/race-design.svg" class="center scale45"/>


## ODIN Application Design
* uniform design - everything is an actor
* toplevel actors are deterministically created, initialized and terminated
  by _Master_ actor
* actors communicate through (configured) bus channels

<img src="./images/race-overview-2.svg" class="center scale55"/>


## ODIN History: RACE (Runtime for Airspace Concept Evaluation)
* live National Airspace System (NAS) visualization and monitoring
* realtime import of SWIM (FAA) and local ADS-B data
* up to 1000 msg/sec, 4500 simultaneous flights
* ..means ODIN can handle a lot more data than current demos

<div>
  <img src="./images/swim-sbs-all-ww.svg" class="left scale40"/>
  <img src="./images/race-nas.png" class="right scale45"/>
</div>
