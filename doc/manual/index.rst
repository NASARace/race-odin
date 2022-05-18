About ODIN-fire
===============

The *Open Data Integration Framework for Wildland Fire Management* (ODIN-fire) project is aimed at providing an open source 
framework that allows stakeholders to create and run their own fire related applications. It is not another
wildland fire website or service. ODIN-fire is a software project to integrate a wide variety of existing 
3rd party web services such as weather, fire behavior models and sensor data with near-realtime tracking information 
such as ground crews, vehicles and aircraft. 

.. image:: images/odin-node.svg
    :class: center scale70
    :alt: RACE overview

Sytems built with ODIN-fire can run within the stakeholder organizations. They can be deployed in the field and can operate
with limited or intermittend connectivity to the outside world. The primary use case is a web-server with local/persistent 
data storage that runs within and only serves the stakeholder network (e.g. an incident command post). Although there is no 
reason why ODIN-fire could not run in the cloud we do not target publicly available services supporting thousands of simultaneous 
users/requests.

ODIN-fire is built on top of the open source RACE_ actor framework. Technically it uses the more general RACE system
to support the specific application domain of wildland fire management. Consequently the main distribution path is through the
RACE_repository_ and related binary artifacts. To that we have added a new RACE_ODIN_repository_ to provide

- an example of how to create new ODIN components (using the Delphire_Sentinel_ sensor network)
- a runnable example of an ODIN-fire application
- ODIN-fire related documentation (such as this web page)

The primary reason for extracting this from the RACE_repository_ is readability - RACE is a large project with hundreds of
files that would make it hard to locate respective example sources.

This website is not intended to replicate RACE documentation, for which we refer to the RACE_ website. Here we focus on the 
ODIN-fire specific aspects. Please note that - as opposed to RACE - ODIN-fire is a new project. Expect to see frequent changes
to both respository and website.

To learn more, read about

- the Motivation_ for ODIN-fire
- the Vision_ behind it
- `Why is ODIN-fire Open Sourced`_
- the `Anatomy of an ODIN-fire application`_
- `Building and running the demo application`_
- available Presentations_


.. _RACE: http://nasarace.github.io/race/
.. _RACE_repository: https://github.com/NASARace/race
.. _RACE_ODIN_repository: https://github.com/NASARace/race-odin
.. _Delphire_Sentinel: https://delphiretech.com/sentinel

