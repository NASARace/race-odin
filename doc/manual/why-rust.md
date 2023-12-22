# Why is ODIN ported to Rust

As of 12/2023 we have started to port the ODIN server infrastructure to Rust. This became necessary
because our focus has shifted from RACEs original goal of building a Java-based prototyping environment for 
distributed simulations to ODINs goal of supporting field-deployable production servers that also utilize external 
native libraries and applications. In other words ODIN (as an application domain) is now the focus, not the
underlying library to implement it. The main design principle stays the same: using actors to create scalable
concurrent systems.

Although RACE was cross-platform (because of its pure Java use) it was originally supposed to run on dedicated
machines with respective admin support. Installation requires to build from sources, which means there are several
prerequisites (recent JDK version, Git, SBT build system, local RACE repository clone). 

ODIN servers on the other hand will have to run on machines that do not require *build-from-source* installation and might
not have the computational resources of a Java production server. ODIN applications also make use of existing external
native software, i.e. they are *not* pure Java. This means the original build-from-source / all-batteries-included approach
of RACE is not applicable anymore.

There are additional reasons for porting ODIN. RACE was heavily based on the excellent [Akka](https://akka.io/) library 
for its actor system implementation. As of 09/2022 [Lightbend](https://www.lightbend.com/) - the owner of Akka - has 
moved from Apache to a proprietary license. While ODIN as an open source project could have been exempt the same
is not necessarily true for ODIN's user community, which includes companies and universities. For this reason ODIN got
stuck with Akka < 2.7, which means there also was a delay in critical patches.

Lastly, RACE was using Scala 2.3. In order to stay abreast with latest versions of major dependencies we would have
had to port RACE to Scala 3. For all these reasons change was inevitable.

Our solution is twofold:

(1) port to a native programming environment with good cross-platform support and a rich eco-system for server
implementation. [Rust](https://www.rust-lang.org/) has proven itself as a very suitable choice. The capability to
produce single file, statically linked applications that can safely use the huge eco-system of Rust crates
and incorporate native C/C++ libraries is invaluable. We can also significantly reduce runtime cost (both memory
and CPU) of ODIN applications.

(2) move functions that are not easy to build for/run on ODIN stakeholder machines to dedicated Rust-based edge servers
that make use of the same infrastructure we create for (1). This means we build new edge servers that replace
external ones such as [dump1090](https://github.com/flightaware/dump1090) (for ADS-B tracking), and add new ones for
functions that are more ODIN specific (e.g. for [WindNinja](https://www.firelab.org/project/windninja) micro grid wind
integration), all from the same programming environment/build system. These servers will enjoy the same easy
deployment we gain from (1).

This porting effort is now well under way and sources are gradually showing up in the RACE repository. We expect to have
first examples around 03/2024. Please contact `peter.c.mehlitz@nasa.gov` if you have questions regarding the ODIN
Rust version.

