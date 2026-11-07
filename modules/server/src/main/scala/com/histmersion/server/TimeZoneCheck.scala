package com.histmersion.server

import java.time.{Instant, ZoneId, ZoneOffset}
import java.time.zone.ZoneRulesProvider
import scala.util.Try

/** The binary's time zones come from the JDK's own tzdb (`lib/tzdb.dat`, which native-image embeds). The build excludes
  * `scala-java-time-tzdb` — a time-zone database for Scala.js that scribe's perfolation drags onto the JVM class path,
  * holding its own `java.time.zone.TzdbZoneRulesProvider`, which GraalVM 25.4's builder refuses there (build.sbt).
  * Zones are not optional: pgjdbc sends the default zone to Postgres on every connection, and HTTP dates parse through
  * the zone rules. So `--self-check` resolves a few zones with a known offset, on both sides of a DST change, and a
  * binary linked without the data fails the check instead of the first query.
  */
object TimeZoneCheck:
  type Report = Report.T
  object Report extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A zone, an instant, and the offset the zone has then (tzdb rules in force since 2008 or earlier). */
  type ReportZones = ReportZones.T
  object ReportZones extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReportVersion = ReportVersion.T
  object ReportVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Probe(zone: Probe.Zone, at: Instant, offset: ZoneOffset)

  object Probe:
    type Zone = Zone.T
    object Zone extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Winter = Instant.parse("2026-01-15T12:00:00Z")
  private val Summer = Instant.parse("2026-07-15T12:00:00Z")

  val Probes: List[Probe] = List(
    Probe("Europe/Warsaw", Winter, ZoneOffset.ofHours(1)),
    Probe("Europe/Warsaw", Summer, ZoneOffset.ofHours(2)),
    Probe("America/New_York", Winter, ZoneOffset.ofHours(-5)),
    Probe("America/New_York", Summer, ZoneOffset.ofHours(-4)),
    Probe("Asia/Kolkata", Summer, ZoneOffset.ofHoursMinutes(5, 30)),
    Probe("Australia/Lord_Howe", Winter, ZoneOffset.ofHours(11)),
    Probe("Australia/Lord_Howe", Summer, ZoneOffset.ofHoursMinutes(10, 30))
  )

  /** Pure over the lookups: (ok, the self-check line). `offset` resolves a zone at an instant, `zones` is how many
    * region ids there are, `version` the tzdb version, `default` the default zone's id.
    */
  def report(
      probes: List[Probe],
      offset: (com.histmersion.server.TimeZoneCheck.Probe.Zone, Instant) => Either[com.histmersion.server.TimeZoneCheck.Report, ZoneOffset],
      zones: com.histmersion.server.TimeZoneCheck.ReportZones,
      version: Option[com.histmersion.server.TimeZoneCheck.ReportVersion],
      default: Either[com.histmersion.server.TimeZoneCheck.Probe.Zone, com.histmersion.server.TimeZoneCheck.Report]
  ): (Boolean, com.histmersion.server.TimeZoneCheck.Report) =
    val wrong = probes.flatMap { p =>
      offset(p.zone, p.at) match
        case Right(o) if o == p.offset => Nil
        case Right(o)                  => List(s"${p.zone} at ${p.at}: $o, expected ${p.offset}")
        case Left(err)                 => List(s"${p.zone}: $err")
    } ++ default.left.toOption.map(err => s"the default zone: $err")
    val problems = (if zones > 0 then Nil else List("no zone ids")) ++ wrong
    if problems.isEmpty then
      (
        true,
        s"time zones: $zones from the JDK's tzdb ${version.getOrElse("(no version)")} · default " +
          s"${default.getOrElse("?")} · ${probes.size} offsets checked"
      )
    else (false, s"time zones: BROKEN — ${problems.mkString("; ")}")

  /** The check against this JDK (or binary). */
  def jdk(): (Boolean, com.histmersion.server.TimeZoneCheck.Report) =
    def err(t: Throwable) = s"${t.getClass.getSimpleName}: ${t.getMessage}"
    val ids = Try(ZoneId.getAvailableZoneIds.size).getOrElse(0)
    val version = Try(ZoneRulesProvider.getVersions("Europe/Warsaw").lastKey).toOption
    report(
      Probes,
      (z, at) => Try(ZoneId.of(z).getRules.getOffset(at)).toEither.left.map(err),
      ids,
      version,
      Try(ZoneId.systemDefault().getId).toEither.left.map(err)
    )
