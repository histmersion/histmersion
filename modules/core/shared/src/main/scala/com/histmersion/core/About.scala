package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `GET /api/about` — who the running server is and how it is doing, in ONE read: the web UI's About
  * panel and `histmersion about`. Read-only; every number is a count the store already keeps. */

/** The running server's build. `gitCommit`/`commitDate` are stamped at compile time from the checkout
  * it was built from (`None` when it was built outside a git checkout); `dirty` = built with
  * uncommitted changes, so the commit alone does not say what is running. */
final case class BuildStamp(
    version: String,
    gitCommit: Option[String] = None,
    commitDate: Option[String] = None,
    dirty: Boolean = false
) derives KindlingsCodecAsObject
object BuildStamp:
  given Schema[BuildStamp] = KindlingsSchema.derived[BuildStamp].schema

/** The OCR job queue, one count per [[JobState]]. */
final case class JobCounts(queued: Int = 0, running: Int = 0, done: Int = 0, failed: Int = 0)
    derives KindlingsCodecAsObject:
  def of(s: JobState): Int = s match
    case JobState.Queued  => queued
    case JobState.Running => running
    case JobState.Done    => done
    case JobState.Failed  => failed
object JobCounts:
  given Schema[JobCounts] = KindlingsSchema.derived[JobCounts].schema

  /** Pure: fold `(status, count)` rows into the counts; a status that is no [[JobState]] is dropped. */
  def of(rows: List[(String, Int)]): JobCounts =
    rows.foldLeft(JobCounts()) { case (acc, (s, n)) =>
      JobState.parse(s) match
        case Some(JobState.Queued)  => acc.copy(queued = acc.queued + n)
        case Some(JobState.Running) => acc.copy(running = acc.running + n)
        case Some(JobState.Done)    => acc.copy(done = acc.done + n)
        case Some(JobState.Failed)  => acc.copy(failed = acc.failed + n)
        case None                   => acc
    }

/** How big the store is: the corpus, the compiled wiki and the reference data. */
final case class CorpusCounts(
    sources: Int,
    findings: Int,
    articles: Int,
    events: Int,
    /** gazetteer places */
    places: Int,
    /** historical period presets (the `periods` table) */
    periods: Int
) derives KindlingsCodecAsObject
object CorpusCounts:
  given Schema[CorpusCounts] = KindlingsSchema.derived[CorpusCounts].schema

/** The user config file the server read at startup (`reference.conf` < this file < the environment). */
final case class ConfigFileInfo(
    /** Where the file is looked for — `$HISTMERSION_CONFIG`, else the per-user default; `None` when
      * no location could be worked out (HOME unset). */
    path: Option[String] = None,
    /** It was there and was read. */
    loaded: Boolean = false,
    /** Keys of the file that are no setting (a typo), ignored. */
    unknownKeys: List[String] = Nil
) derives KindlingsCodecAsObject
object ConfigFileInfo:
  given Schema[ConfigFileInfo] = KindlingsSchema.derived[ConfigFileInfo].schema

  /** `path (read)` / `path (not found: defaults and environment only)` / `none`. Pure. */
  def describe(c: ConfigFileInfo): String =
    c.path match
      case None    => "none (no location: HOME is unset)"
      case Some(p) =>
        val state = if c.loaded then "read" else "not found: defaults and environment only"
        val keys  = if c.unknownKeys.isEmpty then ""
          else s"; ignored unknown ${Plural.noun(c.unknownKeys.size, "key")}: ${c.unknownKeys.mkString(", ")}"
        s"$p ($state$keys)"

final case class About(
    name: String,
    build: BuildStamp,
    /** The same report `/api/health` gives — database, schema version, converter. */
    health: Health,
    jobs: JobCounts,
    /** `None` when the store could not be counted (the database is down). */
    counts: Option[CorpusCounts],
    /** The newest archive in `backups.dir`, if there is one. */
    lastBackup: Option[BackupInfo],
    /** Seconds between that archive and the server's clock when it answered. */
    lastBackupAgeSeconds: Option[Long],
    /** What could not be read (backups dir unreadable, a count failed) — said, never hidden. */
    problems: List[String] = Nil,
    /** The user config file this server started with. */
    configFile: ConfigFileInfo = ConfigFileInfo()
) derives KindlingsCodecAsObject
object About:
  given Schema[About] = KindlingsSchema.derived[About].schema

/** What a dependency's health text means. The converter reports free text (`ok`, `busy: 2 jobs`,
  * `degraded: …`, `converter at … is unreachable`); a client shows the state and keeps the text as
  * the detail, so a wording it has never seen still renders — as [[ServiceState.Unknown]] at worst. */
enum ServiceState:
  case Ok, Busy, Down, Unknown

  def label: String = this match
    case Ok      => "ok"
    case Busy    => "busy"
    case Down    => "down"
    case Unknown => "unknown"

object ServiceState:
  private val DownWords = List("degraded", "error", "unreachable", "down", "failed", "refused", "timeout",
    "did not", "no answer", "returned", "answered")

  /** Pure: the state behind a health text. */
  def of(text: Option[String]): ServiceState =
    text.map(_.trim.toLowerCase).filter(_.nonEmpty) match
      case None                                             => Unknown
      case Some(t) if t == "ok" || t.startsWith("ok ") || t.startsWith("ok:") => Ok
      case Some(t) if t.startsWith("busy") || t.contains(" busy") => Busy
      case Some(t) if t == "unknown"                       => Unknown
      case Some(t) if DownWords.exists(t.contains)         => Down
      case Some(_)                                          => Unknown

/** `histmersion about` — the About report as Markdown, like every other surface. Pure. */
object AboutMarkdown:

  /** `42 s`, `5 min`, `3 h`, `2 d` — how long ago, coarsely. */
  def age(seconds: Long): String =
    val s = math.max(0L, seconds)
    if s < 60 then s"$s s"
    else if s < 3600 then s"${s / 60} min"
    else if s < 86400 then s"${s / 3600} h"
    else s"${s / 86400} d"

  def render(a: About): String =
    val b      = a.build
    val commit = b.gitCommit.fold("unknown")(c => if b.dirty then s"$c (with uncommitted changes)" else c)
    val conv   = a.health.converter
    val backup = a.lastBackup.fold("none")(bk =>
      s"${bk.file} · ${Backup.humanBytes(bk.bytes)}" + a.lastBackupAgeSeconds.fold("")(s => s" · ${age(s)} ago"))
    val j = a.jobs
    val lines = List(
      s"# ${a.name} ${b.version}",
      "",
      s"- commit: $commit",
      s"- commit date: ${b.commitDate.getOrElse("unknown")}",
      s"- schema version: ${a.health.schemaVersion.getOrElse("unknown")}",
      s"- status: ${a.health.status}",
      s"- database: ${a.health.db}",
      s"- converter: ${ServiceState.of(conv).label}" + conv.filterNot(_.trim.equalsIgnoreCase("ok")).fold("")(t => s" ($t)"),
      s"- jobs: ${j.queued} queued · ${j.running} running · ${j.done} done · ${j.failed} failed",
      s"- last backup: $backup",
      s"- config file: ${ConfigFileInfo.describe(a.configFile)}"
    ) ++ a.counts.toList.flatMap(c => List(
      s"- corpus: ${Plural.count(c.sources, "source")} · ${Plural.count(c.findings, "finding")} · " +
        s"${Plural.count(c.articles, "article")} · ${Plural.count(c.events, "event")}",
      s"- reference data: ${Plural.count(c.places, "place")} · ${Plural.count(c.periods, "period preset")}"
    )) ++ (if a.problems.isEmpty then Nil else "" :: "Problems:" :: a.problems.map(p => s"- $p"))
    lines.mkString("\n")
