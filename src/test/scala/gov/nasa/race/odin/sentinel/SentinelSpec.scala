package gov.nasa.race.odin.sentinel

import gov.nasa.race.test.RaceSpec
import gov.nasa.race.util.FileUtils
import org.scalatest.flatspec.AnyFlatSpec

/**
 * regression unit test for parsing Sentinel records
 */
class SentinelSpec extends AnyFlatSpec with RaceSpec {
  val resourcePath = "src/resources"

  "a SentinelParser" should "parse a known accel update input source" in {
    val input = FileUtils.fileContentsAsString(baseResourceFile("sentinel-accel-records.json")).get
    println(s"--- parsing:\n$input")

    val parser = new SentinelParser()
    parser.initialize(input.getBytes)
    val updates = parser.parse()

    println(s"got ${updates.length} updates")
    updates.foreach(println)
  }
}
