package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `GET /api/about` — who the running server is and how it is doing, in ONE read: the web UI's About panel and
  * `histmersion about`. Read-only; every number is a count the store already keeps.
  */

/** The running server's build. `gitCommit`/`commitDate` are stamped at compile time from the checkout it was built from
  * (`None` when it was built outside a git checkout); `dirty` = built with uncommitted changes, so the commit alone
  * does not say what is running. `licence` is the SPDX id the program is distributed under and `sourceUrl` where its
  * source can be had (`None`: an older server).
  */
final case class BuildStamp(
    version: String,
    gitCommit: Option[String] = None,
    commitDate: Option[String] = None,
    dirty: Boolean = false,
    licence: Option[String] = None,
    sourceUrl: Option[String] = None
) derives KindlingsCodecAsObject
object BuildStamp:
  given Schema[BuildStamp] = KindlingsSchema.derived[BuildStamp].schema

/** The AGPL (section 13) has a server offer everyone who uses it over a network the source of the very version that is
  * running: the repository at the commit it was built from, or the repository itself when the commit is unknown. The
  * `/tree/<commit>` form is the one GitHub, GitLab and Forgejo share.
  */
object CorrespondingSource:
  def url(repository: String, commit: Option[String]): String =
    val repo = repository.trim.stripSuffix("/").stripSuffix(".git")
    commit.map(_.trim).filter(_.nonEmpty).fold(repo)(c => s"$repo/tree/$c")

/** The OCR job queue, one count per [[JobState]]. */
final case class JobCounts(
    queued: Int = 0,
    running: Int = 0,
    done: Int = 0,
    failed: Int = 0,
    paused: Int = 0,
    cancelled: Int = 0
) derives KindlingsCodecAsObject:
  def of(s: JobState): Int = s match
    case JobState.Queued    => queued
    case JobState.Running   => running
    case JobState.Done      => done
    case JobState.Failed    => failed
    case JobState.Paused    => paused
    case JobState.Cancelled => cancelled
object JobCounts:
  given Schema[JobCounts] = KindlingsSchema.derived[JobCounts].schema

  /** Pure: fold `(status, count)` rows into the counts; a status that is no [[JobState]] is dropped. */
  def of(rows: List[(String, Int)]): JobCounts =
    rows.foldLeft(JobCounts()) { case (acc, (s, n)) =>
      JobState.parse(s) match
        case Some(JobState.Queued)    => acc.copy(queued = acc.queued + n)
        case Some(JobState.Running)   => acc.copy(running = acc.running + n)
        case Some(JobState.Done)      => acc.copy(done = acc.done + n)
        case Some(JobState.Failed)    => acc.copy(failed = acc.failed + n)
        case Some(JobState.Paused)    => acc.copy(paused = acc.paused + n)
        case Some(JobState.Cancelled) => acc.copy(cancelled = acc.cancelled + n)
        case None                     => acc
    }

/** How big the store is: the corpus, the compiled wiki and the reference data. */
final case class CorpusCounts(
    sources: Int,
    findings: Int,
    articles: Int,
    events: Int,
    /** gazetteer places */
    places: Int,
    /** historical periods (every row of the `periods` table) */
    periods: Int,
    /** of those, the ones the timeline offers as presets (`preset` set, V062) */
    presets: Int = 0
) derives KindlingsCodecAsObject
object CorpusCounts:
  given Schema[CorpusCounts] = KindlingsSchema.derived[CorpusCounts].schema

/** The user config file the server read at startup (`reference.conf` < this file < the environment). */
final case class ConfigFileInfo(
    /** Where the file is looked for — `$HISTMERSION_CONFIG`, else the per-user default; `None` when no location could
      * be worked out (HOME unset).
      */
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
        val keys = if c.unknownKeys.isEmpty then ""
        else s"; ignored unknown ${Plural.noun(c.unknownKeys.size, "key")}: ${c.unknownKeys.mkString(", ")}"
        s"$p ($state$keys)"

/** Who holds the agent lease (docs/research-process-managers.md §2): only that server runs the agent engine, the
  * schedules and the managers on this database.
  */
final case class AgentLeaseInfo(owner: String, until: String, expired: Boolean, thisServer: Boolean)
    derives KindlingsCodecAsObject:
  def describe: String =
    s"$owner${if thisServer then " (this server)" else ""} until $until${if expired then " — expired" else ""}"
object AgentLeaseInfo:
  given Schema[AgentLeaseInfo] = KindlingsSchema.derived[AgentLeaseInfo].schema

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
    configFile: ConfigFileInfo = ConfigFileInfo(),
    /** The agent lease's holder (None: no server has claimed it — agents off everywhere). */
    agentLease: Option[AgentLeaseInfo] = None,
    /** The plugins in this binary and what became of each (secrets as references, never values). */
    plugins: PluginsReport = PluginsReport(),
    /** Where the data is on disk and how big each part is (measured in the background, see [[StorageReport]]); empty
      * from an older server.
      */
    storage: StorageReport = StorageReport(),
    /** The metered upstreams' budgets today (CORE); empty before one was first asked, or from an older server.
      */
    upstreams: List[UpstreamQuota] = Nil,
    /** The acquisition list's legal re-check; absent when it could not be read, or from an older server. */
    acquireRecheck: Option[AcquireChecks.RecheckStatus] = None
) derives KindlingsCodecAsObject
object About:
  given Schema[About] = KindlingsSchema.derived[About].schema

/** What a dependency's health text means. The converter reports free text (`ok`, `busy: 2 jobs`, `degraded: …`,
  * `converter at … is unreachable`); a client shows the state and keeps the text as the detail, so a wording it has
  * never seen still renders — as [[ServiceState.Unknown]] at worst.
  */
enum ServiceState:
  case Ok, Busy, Down, Unknown

  def label: String = this match
    case Ok      => "ok"
    case Busy    => "busy"
    case Down    => "down"
    case Unknown => "unknown"

object ServiceState:
  private val DownWords = List(
    "degraded",
    "error",
    "unreachable",
    "down",
    "failed",
    "refused",
    "timeout",
    "did not",
    "no answer",
    "returned",
    "answered"
  )

  /** Pure: the state behind a health text. */
  def of(text: Option[String]): ServiceState =
    text.map(_.trim.toLowerCase).filter(_.nonEmpty) match
      case None                                                               => Unknown
      case Some(t) if t == "ok" || t.startsWith("ok ") || t.startsWith("ok:") => Ok
      case Some(t) if t.startsWith("busy") || t.contains(" busy")             => Busy
      case Some(t) if t == "unknown"                                          => Unknown
      case Some(t) if DownWords.exists(t.contains)                            => Down
      case Some(_)                                                            => Unknown

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
    val b = a.build
    val commit = b.gitCommit.fold("unknown")(c => if b.dirty then s"$c (with uncommitted changes)" else c)
    val conv = a.health.converter
    val backup = a.lastBackup.fold("none")(bk =>
      s"${bk.file} · ${Backup.humanBytes(bk.bytes)}" + a.lastBackupAgeSeconds.fold("")(s => s" · ${age(s)} ago")
    )
    val j = a.jobs
    val lines = List(
      s"# ${a.name} ${b.version}",
      "",
      s"- commit: $commit",
      s"- commit date: ${b.commitDate.getOrElse("unknown")}",
      s"- licence: ${b.licence.getOrElse("unknown")}",
      s"- source: ${b.sourceUrl.getOrElse("unknown")}",
      s"- schema version: ${a.health.schemaVersion.getOrElse("unknown")}",
      s"- status: ${a.health.status}",
      s"- database: ${a.health.db}",
      s"- converter: ${ServiceState.of(conv).label}" + conv
        .filterNot(_.trim.equalsIgnoreCase("ok"))
        .fold("")(t => s" ($t)"),
      s"- jobs: ${j.queued} queued · ${j.running} running · ${j.done} done · ${j.failed} failed",
      s"- last backup: $backup",
      s"- config file: ${ConfigFileInfo.describe(a.configFile)}",
      s"- agent lease: ${a.agentLease.fold("none (no server runs agents on this database)")(_.describe)}"
    ) ++ a.upstreams.map(q => s"- ${UpstreamQuotaMarkdown.line(q)}") ++
      a.acquireRecheck.map(r => s"- ${AcquireChecksMarkdown.line(r)}").toList ++ PluginsMarkdown.summary(
        a.plugins
      ) ++ a.counts.toList.flatMap(c =>
        List(
          s"- corpus: ${Plural.count(c.sources, "source")} · ${Plural.count(c.findings, "finding")} · " +
            s"${Plural.count(c.articles, "article")} · ${Plural.count(c.events, "event")}",
          s"- reference data: ${Plural.count(c.places, "place")} · ${Plural.count(c.periods, "period")} · ${Plural.count(c.presets, "preset")}"
        )
      ) ++ StorageMarkdown.render(a.storage) ++
      (if a.problems.isEmpty then Nil else "" :: "Problems:" :: a.problems.map(p => s"- $p"))
    lines.mkString("\n")
