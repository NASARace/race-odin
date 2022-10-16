# Wildland Fire Data Sources
This document is a catalog of current and potentially future ODIN data sources. It is by no means complete and will be continuously updated. The main data categories are:

  1. fire detection and tracking
  2. fire danger, fuels and disturbances
  3. weather
  4. infrastructure
  5. object tracking

------------------------------------------------------------------------------------------------------------------

## Fire Detection and Tracking
This category includes sources that can be used to detect new fires in realtime, and to track fire spread/perimeters
for known fires.

### GOES Satellites
The [Geostationary Operational Environmental Satellites, R-Series](https://www.goes-r.gov) (designated GOES-x after launch) provide continuous weather imagery and monitoring of meteorological and space environment data across North and Central America. There are currently two operational satellites: GOES-16 (east) and GOES-17 (west). GOES-18 is slated to replace GOES-17 in early 2023.

The primary instrument onboard of these satellites is the [Advanced Baseline Imager (ABI)](http:https://www.goes-r.gov/spacesegment/abi.html). A collection of visual images created by ABI can be found [here](https://www.star.nesdis.noaa.gov/GOES/sector.php?sat=G17&sector=wus). The [Beginner's Guide to GOES-R Data](https://www.goes-r.gov/downloads/resources/documents/Beginners_Guide_to_GOES-R_Series_Data.pdf) is a general introduction.

The most relevant fire-related data product created with ABI is the short-wave infrared based **Fire / Hot Spot Characterization** for which more information is available in the following documents:

  * [GOES-R Series Product Definition User's Guide](https://www.goes-r.gov/products/docs/PUG-L2+-vol5.pdf)(pg 472ff)
  * [GOES-R Advanced Baseline Imager (ABI) Algorithm Theoretical Basis Document For Fire / Hot Spot Characterization](https://www.star.nesdis.noaa.gov/goesr/docs/ATBD/Fire.pdf)

This data product is updated every 5 minutes. The surface footprint of a fire pixel is about 2x2km at nadir.

Data access is through [Amazon S3](https://aws.amazon.com/s3/), region '**us-east-1**',  buckets '**noaa-goes-16**' and
'**noaa-goes-17**'. The data format is [NetCDF](https://www.unidata.ucar.edu/software/netcdf/), the data rate approximately 800 kB/5min per satellite. [Interactive and archived data](https://home.chpc.utah.edu/~u0553130/Brian_Blaylock/cgi-bin/goes16_download.cgi) is available.

ODIN currently supports the following functions:

  * live import (race-earth: `GoesRImportActor`, `GoesRDataReader`)
  * browser-based visualization (race-cesium: `CesiumGoesrRoute`)

As geostationary satellites the GOES-R series provides constant observation of the same area but - due to the high orbit (35,786 km) - lower spatial resolution and confidence. Satellites in this category are therefore primarily sources for low-latency detection of potential fires which should then be further investigated by higher resolution sensors. Consequently, ODIN uses GOES-R data as a time series in which hotspots should always be looked at in temporal and spatial context.


### JPSS Satellites
The [Joint Polar Satellite System (JPSS)](https://www.nesdis.noaa.gov/about/our-offices/joint-polar-satellite-system-jpss-program-office) currently includes two satellites: Suomi-NPP and NOAA-20. JPSS-2 is scheduled to launch by end of 2022. JPSS satellites have sun-synchronous orbits with a period of about 101 minutes at an average altitude of 800km. The satellites are spaced a half-orbit apart on the same orbital plane.

The primary instrument on board of JPSS satellites is the [Visible Infrared Imaging Radiometer Suite (VIIRS)](https://ladsweb.modaps.eosdis.nasa.gov/missions-and-measurements/viirs/). Additional information about VIIRS and its I-band based fire product is available here:

  * [VIIRS Sensor Data Record User's Guide](https://ncc.nesdis.noaa.gov/documents/documentation/viirs-users-guide-tech-report-142a-v1.3.pdf)
  * [Beginner's Guide to VIIRS Imagery Data](https://rammb.cira.colostate.edu/projects/npp/Beginner_Guide_to_VIIRS_Imagery_Data.pdf)
  * [VIIRS I-Band 375m Active Fire Data](https://www.earthdata.nasa.gov/learn/find-data/near-real-time/firms/viirs-i-band-375-m-active-fire-data)
  * [VIIRS 375m & 750m Active Fire Products Users's Guide](https://viirsland.gsfc.nasa.gov/PDF/VIIRS_activefire_User_Guide.pdf)
  * [The New VIIRS 375 m active fire detection data product: Algorithm description and initial assessment](https://www.sciencedirect.com/science/article/pii/S0034425713004483)

VIIRS is a whisk-broom scanner with a large across-track swath of about 3000km, providing a significant overlap between consecutive overpasses. Each satellite can therefore produce data for a given location in CONUS 2-4 times per day. Surface footprint of VIIRS fire pixels is about 375x375m at nadir.

There are several potential access points and mechanisms for VIIRS fire products, including [interactive, archived download](https://www.earthdata.nasa.gov/learn/find-data/near-real-time/firms/active-fire-data) in various formats (CSV, KML, shape file), and as [NetCDF](https://www.unidata.ucar.edu/software/netcdf/) files from <https://nrt3.modaps.eosdis.nasa.gov/archive/allData/5200>. Visual content is availble through NASA's [Fire Information for Resource Management System](https://firms.modaps.eosdis.nasa.gov/).

These sources differ in latency, based on downlink mechanism and locations: [ultra-realtime (URT), realtime (RT)and near-realtime (NRT)](https://wiki.earthdata.nasa.gov/display/FIRMS/2022/07/14/Wildfire+detection+in+the+US+and+Canada+within+a+minute+of+satellite+observation).

ODIN uses the [FIRMS http API](https://firms.modaps.eosdis.nasa.gov/usfs/api/area/) to retrieve the ultra-realtime (URT) VIIRS fire product in several configured time steps after each overpass for a configured area. This means that hotspot data for the same locations can change when post-processed data (NRT) becomes available. The format is CSV, data rate primarily depends on the number of detected hotspots (about 1MB per overpass with active fires). Overpass times are calculated based on [TLEs](https://www.space-track.org/documentation#/tle) retrieved from <space-track.org>.

ODIN currently supports the following functions:

  * live import (race-earth: `JpssImportActor`, `JpssDataReader`)
  * browser-based visualization (race-cesium: `CesiumJpssRoute`)
  * preliminary archive replay (race-earth: `HotspotReplayActor`, race-cesium: `CesiumHotspotRoute`, both to be unified with respective `Jpss` components)

Resolution of VIIRS hotspots is usually good enough to map to terrain, i.e. this data - if available in adequate time - can be used to verify GOES hotspots and track known fires.


### Aqua and Terra Satellites
The sun-synchronous polar orbiters [Aqua](https://aqua.nasa.gov/) and [Terra](https://terra.nasa.gov/) have orbital periods of 99min at an altitude of about 700km. Both fly the [Moderate Resolution Imaging SpectroRadiometer (MODIS)](https://modis.gsfc.nasa.gov/about/) which is a predecessor of VIIRS that provides roughly half the resolution (1kmx1km at nadir) for fire pixel surface footprints. Each satellite delivers two overpasses per day for CONUS.

Data formats and access mechanisms for MODIS satellites is very similar to JPSS/VIIRS. The preferred source is again the [FIRMS http API](https://firms.modaps.eosdis.nasa.gov/usfs/api/area/) that provides CSV with a slightly different attribute table. The same [ultra-realtime (URT), realtime (RT)and near-realtime (NRT)](https://wiki.earthdata.nasa.gov/display/FIRMS/2022/07/14/Wildfire+detection+in+the+US+and+Canada+within+a+minute+of+satellite+observation) considerations apply.

Due to its medium resolution MODIS satellite data is mostly used as a backup for VIIRS and to fill time gaps.


### Landsat-8 and Landsat-9
The [Landsat 8](https://www.usgs.gov/landsat-missions/landsat-8) and [Landsat-9](https://www.usgs.gov/landsat-missions/landsat-9) satellites are also a sun-synchronous polar orbiters at an altitude of about 700km. Both fly the [Operational Land Imager (OLI) instrument](https://landsat.gsfc.nasa.gov/satellites/landsat-9/landsat-9-instruments/oli-2-design/), which is a push-broom imager with a narrow 185km across-track swath, a pixel resolution of 30m and a revisit time of 16 days.

Due to the low temporal resolution Landsat data has been mostly used for burnt area detection and less for active fire detection. Active fire detection is described in [Schroeder et al 2015](https://reader.elsevier.com/reader/sd/pii/S0034425715301206?token=3C305FBAB1CB3B25F49554E97C0143A723E7F38DE7D0E3492B9ADB4979EAE2992F2793FF6C9088591DA639B04CDCB6D1&originRegion=us-east-1&originCreation=20220928052330) and [Kumar and Roy 2018](https://www.researchgate.net/publication/320673228_Global_operational_land_imager_Landsat-8_reflectance-based_active_fire_detection_algorithm).

Landsat data is available from several sites [listet here](https://www.usgs.gov/landsat-missions/landsat-data-access), including ([requester pays](https://docs.aws.amazon.com/AmazonS3/latest/userguide/RequesterPaysBuckets.html)) Amazon S3 (region: us-west-2, bucket: usgs-landsat) and interactively via the [SpatioTemporal Asset Catalog (STAC) browser](https://landsatlook.usgs.gov/stac-browser) and the [STAC server API](https://stac-utils.github.io/stac-server/). More information is available [here](https://www.usgs.gov/landsat-missions/landsat-commercial-cloud-data-access).


### Copernicus Sentinel-2 Satellites
[ESAs](https://www.esa.int/) [Copernicus Sentinel-2A and -2B](https://sentinel.esa.int/web/sentinel/missions/sentinel-2) satellites are polar orbiters that fly the the [MultiSpectral Instrument (MSI)](https://sentinels.copernicus.eu/web/sentinel/technical-guides/sentinel-2-msi/msi-instrument), featuring 13 spectral bands, a revisit time of less than 5 days, a 290km wide cross-track swath and  spatial resolution up to 20m.

Like Landsat the Sentinel-2 satellites have been mostly used for burnt area detection. An active fire detection algorithm based on MSI is described by [Hu et al 2021](https://reader.elsevier.com/reader/sd/pii/S0303243421000544?token=171F4942D9D809CB89B39E1F5A152736E0B67C975EA5C0649843144488B19E2D5733A9C096CEE9A4197FD25677E49045&originRegion=us-east-1&originCreation=20220928051435).

Data is available at the [Sentinel-2 Open Data on AWS registry](https://registry.opendata.aws/sentinel-2/)(requester pays).

According to [Li and Roy 2017](https://www.mdpi.com/2072-4292/9/9/902/htm) a combination of Landsat-8, Sentinel-2A and Sentinel-2B can achieve a revisit interval of 2.9 days.

Other satellites that could be used for active fire monitoring include [Sentinel-3 (STLR)](https://sentinels.copernicus.eu/web/sentinel/user-guides/sentinel-3-slstr/applications/land-monitoring/fire-location-radiative-power) and [FengYun-3C VIRR](https://ieeexplore.ieee.org/document/8022983).


### FIRMS
NASAs [Fire Information for Resource Management System (FIRMS)](https://firms.modaps.eosdis.nasa.gov/) site includes an [interactive viewer](https://firms.modaps.eosdis.nasa.gov/usfs/map/#d:24hrs;@-100.0,40.0,4z), [archive download](https://firms.modaps.eosdis.nasa.gov/download/create.php) and [active fire data](https://firms.modaps.eosdis.nasa.gov/usfs/active_fire/) for VIIRS and MODIS satellites as CSV, KML and shapefiles.

The [REST API](https://firms.modaps.eosdis.nasa.gov/usfs/api/area/) provides spatial, temporal and satellite filters for download of standard (SP), near realtime (NRT), realtime (RT) and ultra-realtime (URT) MODIS and VIIRS data in CSV format. This is considered to be the best source of low latency MODIS and VIIRS active fire data.

### Global Imagery Browse Service (GIBS)
Many of the GOES, JPSS, Aqua and Terra data products are also available as WMS or WMTS services on [NASAs Global Imagery Browse Services (GIBS)](https://nasa-gibs.github.io/gibs-api-docs/access-basics/#access-basics) site.


### UAS Based Fire Tracking
TBD.

### Ground Based Fire Sensors
Stationary fire sensors are currently mostly a commercial domain. Potential data sources will be added when technical information about data type and access becomes available.

#### Delphire Sentinel
[Delphire Sentinels](https://delphiretech.com/sentinel) are multi-sensor devices placed along powerlines and other critical infrastructure, providing realtime visual, infrared, VOC and weather readings. Devices use on-board AI to minimize erroneous notifications and data volume. The primary use of Sentinel devices is to detect and report below-canopy fires with rapid response time.

ODIN supports realtime import of Sentinel data via [http API](https://delphiretech.com/sentinel/api/), using websocket notifications to minimize latency of device updates.


### Wildland Fire Interagency Geospatial Services (WFIGS)
[WFIGS Current Wildland Fire Perimeters](https://data-nifc.opendata.arcgis.com/datasets/nifc::wfigs-current-wildland-fire-perimeters/about) is the authorative [NIFC](https://www.nifc.gov/) source for wildland fire information, including wildfires and prescribed burns.

The data is available in several formats (CSV, KML, shapefile) and levels of detail. The most suitable/comprehensive form  for automatic import is [GeoJSON](https://geojson.org/) obtained via [RESTful http API](https://data-nifc.opendata.arcgis.com/datasets/nifc::wfigs-current-wildland-fire-perimeters/api). This API supports fine grained control over included context, location and shape information.

------------------------------------------------------------------------------------------------------------------

## Fire Danger, Fuels and Disturbances

### LANDFIRE (LF)
The [LANDFIRE](https://landfire.gov/) website (curated by DOI/USGS) provides geospatial data products for vegetation, fuels and disturbances (changes on landscape such as previous burns).

Interactive access to LANDFIRE imagery is available through [this viewer](https://www.landfire.gov/viewer/). The website also provides [full mosaic images](https://landfire.gov/version_download.php).

Automated access to LANDFIRE data is available through [WMS](https://www.ogc.org/standards/wms) using [these URLs](https://landfire.gov/data_access.php) or [this ArcGIS REST API](https://lfps.usgs.gov/arcgis/rest/services/LandfireProductService/GPServer). Since most of the data is imagery, WMS is the preferred access mechanism.

Available layers include:

  * vegetation cover, height and type
  * fuels
    + surface and canopy
       - fire behavior fuel models
       - forest canopy bulk density, height and cover
    + fuel disturbance
    + fuelbeds

### MTBS
The interagency [Monitoring Trends in Burn Severity (MTBS)](https://www.mtbs.gov/) project provides annual burn severity data as [interactive viewer](https://www.mtbs.gov/viewer/?region=conus), [MTBS Data Explorer](https://apps.fs.usda.gov/lcms-viewer/mtbs.html) and [WMS or KML](https://www.mtbs.gov/direct-download).

### Wildland Fire Assessment System (WFAS)
The [Wildland Fire Assessment System (WFAS)](https://www.wfas.net/) provides fire danger ratings and underlying data such as dead fuel moisture. The data is mostly distributed as static maps (*.png images) and is therefore not suitable for automatic processing or overlays. Some data is also available as [KML layer](https://developers.google.com/kml).

------------------------------------------------------------------------------------------------------------------

## Weather

### High Resolution Rapid Refresh (HRRR)
NOAA's [High Resoltion Rapid Refresh](https://rapidrefresh.noaa.gov/hrrr/) service provides atmospheric realtime 3km forecast data. Of special interest are surface wind (10m, average and gust potential, updated every 15min) and smoke fields (updated hourly).

Data access is via ftp or http as [GRIB2](https://www.nco.ncep.noaa.gov/pmb/docs/grib2/grib2_doc/) from [NOAAs NCEP Central Operations server](https://www.nco.ncep.noaa.gov/pmb/products/hrrr/) or archived from [Amazon S3 (region 'us-east-1', bucket 'noaa-hrrr-bdp-pds')](https://registry.opendata.aws/noaa-hrrr-pds/).

The HRRR smoke product that is based on satellite FRP hotspots and HRRR wind forecast is also available with this [WMS configuration](https://realearth.ssec.wisc.edu/thredds/HRRR-smoke-surface).

### RAWS
There are about 2,200 [Remote Automatic Weather Stations (RAWS)](https://raws.nifc.gov) within the US that monitor fire relevant weather information.

Data access is through [REST API](https://weather.nifc.gov/ords/prd/f?p=107:900:9109639941099), response format is JSON. Stations can be interactively queried [here](https://weather.nifc.gov/ords/prd/f?p=107:100:9109639941099), or map-based from [this page](https://raws.dri.edu/).


### Mesonet
[Mesonet](https://developers.synopticdata.com/mesonet/) supports queries for a large number of surface weather stations across the US through REST API, producing JSON data. A list of available data fields can be found [here](https://developers.synopticdata.com/about/station-variables/).

------------------------------------------------------------------------------------------------------------------

## Infrastructure and Borders

### Homeland Infrastructure Foundation Level Data (HIFLD)
The [Homeland Infrastructure Foundation-Level Data (HIFLD)](https://hifld-geoplatform.opendata.arcgis.com/) site provides a large variety of public infrastructure information, including

  * [electric power transmission lines](https://hifld-geoplatform.opendata.arcgis.com/datasets/electric-power-transmission-lines)
  * [electric substations](https://hifld-geoplatform.opendata.arcgis.com/datasets/geoplatform::electric-substations)
  * [radio transmission towers](https://hifld-geoplatform.opendata.arcgis.com/datasets/geoplatform::fm-transmission-towers)
  * [cellular towers](https://hifld-geoplatform.opendata.arcgis.com/datasets/geoplatform::cellulartowers)
  * [fire stations](https://hifld-geoplatform.opendata.arcgis.com/datasets/fire-stations)
  * [county boundaries](https://hifld-geoplatform.opendata.arcgis.com/datasets/geoplatform::us-county-boundaries)
  * [state boundaries](https://hifld-geoplatform.opendata.arcgis.com/datasets/geoplatform::us-state-boundaries)
  * [local roads](https://hifld-geoplatform.opendata.arcgis.com/datasets/geoplatform::local-roads-72k-scale)

Each of the above topics is available in CSV, KML, shapefile and GeoJSON format. Since this data is semi-static downloads can be done manually.

### OSM Buildings
The [OSM Buildings](https://osmbuildings.org/) database provides shapes and meta information of buildings in form of GeoJSON. The [Overpass API](https://dev.overpass-api.de/overpass-doc/en/index.html) (http POST with query as form data) can be used to filter regions and objects of interest.

### Microsoft USBuildingFootprints
The [Microsoft US buildings footprint](https://github.com/microsoft/USBuildingFootprints) Github repository contains a large number of buildings scanned from Bing imagery. Stated intention is to integrate this database into OSM Buildings but as of late 2022 the repository has not been fully merged and contains a superset of OSM Buildings. 

------------------------------------------------------------------------------------------------------------------

## Object Tracking

### Local Air Traffic
Local air traffic can be tracked by means of [Automatic Dependent Surveillance-Broadcast (ADS-B)](https://www.faa.gov/air_traffic/technology/adsb). Receivers are [inexpensive and easy to build](https://flightaware.com/adsb/piaware/build/). The open sourced [dump1090 decoder](https://github.com/flightaware/dump1090) can be used to transform the radio signals into a [SBS compatible CSV stream](http://woodair.net/SBS/Article/Barebones42_Socket_Data.htm). Local ADS-B receivers typically provide 1Hz data.

Free ADS-B exchanges include [ADS-B Exchange](https://www.adsbexchange.com/) and [OpenSky-network](https://opensky-network.org/).

Commercial flight tracking services include [flightradar24](https://www.flightradar24.com/) and [flightaware](https://flightaware.com/).

These services provide about 5Hz data.

Space based ADS-B tracking is available from [Aireon](https://aireon.com/). This might be the best option for incidents in remote areas and mountainous terrain, especially if required response times are too short to install a local receiver network.

### GPS Ground Tracking
Various loggers for handheld GPS devices exist. The open source [GPSLogger for Android](https://gpslogger.app/) provides configurable log packets and allows to connect to dedicated (own) log-servers, which both address security concerns.

The challenge for GPS ground tracking is again connectivity in the field. Incidents in remote areas/steep terrain usually don't have sufficient cell coverage to rely on off-the-shelf handheld GPS devices.