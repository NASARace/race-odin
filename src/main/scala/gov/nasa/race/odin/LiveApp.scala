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
import gov.nasa.race.cesium.{CesiumGoesrRoute, CesiumJpssRoute, CesiumTrackRoute}
import gov.nasa.race.core.ParentActor
import gov.nasa.race.http.DocumentRoute
import gov.nasa.race.odin.sentinel.SentinelRoute
import gov.nasa.race.ui.UiSettingsRoute

/**
 * route with live track, GOES_R satellites, JPSS satellites and Delphire sensors
 */
class LiveApp (val parent: ParentActor, val config: Config) extends DocumentRoute
  with UiSettingsRoute
  with SentinelRoute
  with CesiumTrackRoute
  with CesiumGoesrRoute
  with CesiumJpssRoute
