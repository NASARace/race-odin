/*
 * Copyright (c) 2022, United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * The RACE - Runtime for Airspace Concept Evaluation platform is licensed
 * under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gov.nasa.race.odin

import com.typesafe.config.Config
import gov.nasa.race.cesium.{CesiumBldgRoute, CesiumGpsRoute, CesiumLayerRoute, CesiumTrackRoute, CesiumWindRoute}
import gov.nasa.race.core.ParentActor
import gov.nasa.race.http.{CachedFileAssetMap, DocumentRoute}
import gov.nasa.race.ui.UiSettingsRoute
import gov.nasa.race.odin.sentinel.SentinelRoute

/**
 * the aggregation of micro-services we provide under the configured URL
 */
class DemoApp(val parent: ParentActor, val config: Config)
         extends DocumentRoute // the document (HTML + config.js) creator
           with UiSettingsRoute
           with CesiumBldgRoute // a route that includes OSMBuildings support
           with CesiumGpsRoute // a route that tracks GPS devices on the ground
           with CesiumTrackRoute // a route that tracks aircraft/drones
           with CesiumLayerRoute  // a route that contains configured, user-selectable display layers (KML, GeoJSON etc.)
           with CesiumWindRoute  // a route that can display wind fields
           with SentinelRoute // a route that can display Sentinel sensor state
