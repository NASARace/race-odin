// example build configuration for projects using RACE

name := "race-odin"
scalaVersion := "2.13.8"

enablePlugins(LaikaPlugin) // optional - to generate website and slides

// those settings are not RACE specific but recommended when running applications from within a SBT shell

val raceVersion = "1.8.+"

lazy val root = (project in file(".")).
  enablePlugins(JavaAppPackaging,LauncherJarPlugin). // provides 'stage' task to generate stand alone scripts that can be executed outside SBT
  settings(
    Compile / mainClass := Some("gov.nasa.race.main.ConsoleMain"),  // we just use RACEs driver
    run / fork := true,
    run / Keys.connectInput := true,
    Test / fork := true,
    outputStrategy := Some(StdoutOutput),
    PluginSettings.pluginSettings,
    commands ++= LaikaCommands.commands,
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-simple" % "2.0.0-alpha1",
      "gov.nasa.race" %% "race-core" % raceVersion,
      "gov.nasa.race" %% "race-air" % raceVersion,
      "gov.nasa.race" %% "race-client-ui" % raceVersion,
      "gov.nasa.race" %% "race-cesium" % raceVersion,
      "gov.nasa.race" %% "race-net-http" % raceVersion,
      "gov.nasa.race" %% "race-tools" % raceVersion excludeAll(
        ExclusionRule(organization="ch.qos.logback")
      ),
      "gov.nasa.race" %% "race-testkit" % raceVersion % Test
    ),
    resolvers += "unidata" at "https://artifacts.unidata.ucar.edu/repository/unidata-all/"
  )
