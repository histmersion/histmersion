package com.histmersion
package server

import com.histmersion.testsupport.ComparedByValue.given

import java.time.{Instant, ZoneOffset}

class TimeZoneCheckSuite extends munit.FunSuite:

  private val probe = TimeZoneCheck.Probe(
    TimeZoneCheck.Probe.Zone("Europe/Warsaw"),
    Instant.parse("2026-01-15T12:00:00Z"),
    ZoneOffset.ofHours(1)
  )

  test("every probe at its offset and a default zone: ok, with the count, the tzdb version and the default"):
    val (ok, line) = TimeZoneCheck.report(
      List(probe),
      (_, _) => Right(ZoneOffset.ofHours(1)),
      TimeZoneCheck.ZoneCount(600),
      Some(TimeZoneCheck.TzdbVersion("2026a")),
      Right(TimeZoneCheck.Probe.Zone("Europe/Warsaw"))
    )
    assert(ok, line)
    assertEquals(line, "time zones: 600 from the JDK's tzdb 2026a · default Europe/Warsaw · 1 offsets checked")

  test("a wrong offset, an unknown zone, no zone ids or no default zone: BROKEN, naming each"):
    val (ok1, wrong) = TimeZoneCheck.report(
      List(probe),
      (_, _) => Right(ZoneOffset.UTC),
      TimeZoneCheck.ZoneCount(600),
      None,
      Right(TimeZoneCheck.Probe.Zone("UTC"))
    )
    assert(!ok1)
    assertEquals(wrong, "time zones: BROKEN — Europe/Warsaw at 2026-01-15T12:00:00Z: Z, expected +01:00")
    val (ok2, unknown) = TimeZoneCheck.report(
      List(probe),
      (_, _) => Left(TimeZoneCheck.Problem("ZoneRulesException: Unknown time-zone ID")),
      TimeZoneCheck.ZoneCount(0),
      None,
      Left(TimeZoneCheck.Problem("no default"))
    )
    assert(!ok2)
    assertEquals(
      unknown,
      "time zones: BROKEN — no zone ids; Europe/Warsaw: ZoneRulesException: Unknown time-zone ID; " +
        "the default zone: no default"
    )

  test("this JDK resolves every probe, across DST changes and half-hour offsets"):
    val (ok, line) = TimeZoneCheck.jdk()
    assert(ok, line)
    assert(
      line.value.matches("""time zones: \d{3} from the JDK's tzdb 20\d\d[a-z] · default \S+ · 7 offsets checked"""),
      line
    )

  test("the JVM class path carries no scala-java-time-tzdb (build.sbt excludes it): zones are the JDK's own"):
    // the jar's own database object and its copy of the JDK provider class; the JDK's provider lives in
    // java.base (jrt:), never in a jar
    assertEquals(Option(getClass.getClassLoader.getResource("zonedb/java/tzdb$.class")), None)
    val provider = Option(ClassLoader.getSystemResource("java/time/zone/TzdbZoneRulesProvider.class")).map(_.toString)
    assert(provider.forall(_.startsWith("jrt:")), provider)
    assertEquals(classOf[java.time.zone.ZoneRulesProvider].getModule.getName, "java.base")
