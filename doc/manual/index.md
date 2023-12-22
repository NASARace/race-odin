# About ODIN-fire
The *Open Data Integration Framework for Wildland Fire Management* (ODIN-fire) project is aimed at providing an open source
framework that allows stakeholders to create and run their own fire related applications. It is not another
wildland fire website or service. ODIN-fire is a software project to integrate a wide variety of existing
3rd party web services such as weather, fire behavior models and sensor data with near-realtime tracking information
such as ground crews, vehicles and aircraft.

<img class="center scale70" src="images/odin-node.svg">

Sytems built with ODIN-fire can run within the stakeholder organizations. They can be deployed in the field and can operate
with limited or intermittent connectivity to the outside world. The primary use case is a web-server with local/persistent
data storage that runs within and only serves the stakeholder network (e.g. an incident command post). Although there is no
reason why ODIN-fire could not run in the cloud we do not target publicly available services supporting thousands of simultaneous
users/requests.

You can get an idea about the data integration we aim at by watching our [TFRSAC](https://fsapps.nwcg.gov/nirops/pages/tfrsac) presentations:

  - [spring 2023](https://www.youtube.com/watch?v=b9DfMBYCe-s&t=4950s)
  - [fall 2022](https://www.youtube.com/watch?v=gCBXOaybDLA)

<br>
<hr>
**NOTE**
<b>ODIN (and RACE) are currently (as of 12/2023) ported to Rust, please see [Why is ODIN ported to Rust] for details. While
the live/czu examples of the Scala version will still work they do require to build native executables (e.g. 
[WindNinja](https://www.firelab.org/project/windninja)).</b> 
<hr>
<br>

ODIN-fire is built on top of the open source RACE_ actor framework. Technically it uses the more general RACE system
to support the specific application domain of wildland fire management. Consequently, the main distribution path is through the
[RACE_repository](https://github.com/NASARace/race) and related binary artifacts. To that we have added a new 
[RACE-ODIN repository](https://github.com/NASARace/race-odin) to provide

- an example of how to create new ODIN components (using the [Delphire Sentinel](https://delphiretech.com/sentinel) sensor
network as an example)
- runnable examples of an ODIN-fire application
- ODIN-fire related documentation (such as this web page)

The primary reason for extracting this from the [RACE_repository](https://github.com/NASARace/race) is readability - RACE 
is a large project with hundreds of files that would make it hard to locate respective example sources.

This website is not intended to replicate RACE documentation, for which we refer to the [RACE](http://nasarace.github.io/race/)
website. Here we focus on the ODIN-fire specific aspects. Please note that - as opposed to RACE - ODIN-fire is a 
new project. Expect to see frequent changes to both the repository and the website.

To learn more, read about

  * the [Motivation](motivation.md) for ODIN-fire
  * the [Vision](vision.md) behind it
  * [Why is ODIN-fire Open Sourced](opensource.md)
  * [Building and Running the RACE-ODIN Archive Replay Demo](archive-demo.md)
  * [Building and Running the RACE-ODIN live-data Demo](live-demo.md)
  * the [Anatomy of an ODIN-fire application](application.md)
  * available Presentations_