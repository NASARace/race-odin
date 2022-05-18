import java.io.File
import sbt._
import Keys._
import laika.markdown.github.GitHubFlavor

// NOTE - using macros such as "++=" and "+=" is dangerous since it
// uses a implicit (context dependent) Append.Value(s) argument

object PluginSettings {

  // some of these are just examples for now, to show how to initialize plugin settings
  // without proliferating build.sbt with plugin configs


  //----------------------------------------------------------------------------------
  // laika from https://github.com/planet42/Laika
  // adds laika:site and laika:clean to generate web site and/pr PDFs
  import laika.sbt.LaikaPlugin
  import laika.sbt.LaikaPlugin.autoImport._
  val laikaSettings = LaikaPlugin.projectSettings ++ Seq(
    laikaExtensions := Seq(
      GitHubFlavor
    ),
    Laika / sourceDirectories := Seq(file("doc/manual")),
    Laika / target := target.value / "doc",
    laikaIncludePDF := false,
    laikaIncludeAPI := false, // not yet
    Laika / excludeFilter := new FileFilter {
      override def accept(file:File): Boolean = Seq("attic","slides").contains(file.getName)
    },

    laikaConfig := LaikaConfig.defaults.withRawContent, // this would be the new reference

    Laika / aggregate := false
  )

  // collect all settings for all active plugins. This goes into build.sbt commonSettings
  val pluginSettings = laikaSettings
}
