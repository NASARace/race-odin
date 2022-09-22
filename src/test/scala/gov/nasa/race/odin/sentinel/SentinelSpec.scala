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
    val updates = parser.parseRecords()

    println(s"got ${updates.length} updates")
    updates.foreach(println)
  }

  "a SentinelParser" should "parse known device messages" in {
    val input = FileUtils.fileContentsAsString(baseResourceFile("sentinel-devices.json")).get
    println(s"--- parsing:\n$input")

    val parser = new SentinelParser()
    parser.initialize(input.getBytes)
    val devices = parser.parseDevices()

    println(s"got ${devices.size} devices")
    devices.foreach(println)
  }

  "a SentinelParser" should "parse known sensor messages" in {
    val input = FileUtils.fileContentsAsString(baseResourceFile("sentinel-sensors.json")).get
    println(s"--- parsing:\n$input")

    val parser = new SentinelParser()
    parser.initialize(input.getBytes)
    val sensors = parser.parseSensors()

    println(s"got ${sensors.size} sensors")
    sensors.foreach(println)
  }

  // the new live format
  "a SentinelParser" should "parse known sensor records" in {
    val input = FileUtils.fileContentsAsString(baseResourceFile("sentinel-records.json")).get
    println(s"--- parsing:\n$input")

    val parser = new SentinelParser()
    parser.initialize(input.getBytes)
    val readings = parser.parseRecords()

    println(s"got ${readings.size} sensor records")
    readings.foreach(println)
  }

  "a SentinelParser" should "parse known smoke sensor records" in {
    val input = FileUtils.fileContentsAsString(baseResourceFile("sentinel-smoke-records.json")).get
    println(s"--- parsing:\n$input")

    val parser = new SentinelParser()
    parser.initialize(input.getBytes)
    val readings = parser.parseRecords()

    println(s"got ${readings.size} sensor records")
    readings.foreach(println)
  }

  "a SentinelParser" should "parse known image sensor records" in {
    val input = FileUtils.fileContentsAsString(baseResourceFile("sentinel-image-records.json")).get
    println(s"--- parsing:\n$input")

    val parser = new SentinelParser()
    parser.initialize(input.getBytes)
    val readings = parser.parseRecords()

    println(s"got ${readings.size} sensor records")
    readings.foreach(println)
  }
}
