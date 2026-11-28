package com.histmersion
package web

import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.{ByteCount, ServiceState, Wording}
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.backup.Backup
import com.histmersion.api.instance.{About, AboutMarkdown}
import com.histmersion.api.jobs.JobState
import com.histmersion.api.maintenance.Storage
import com.histmersion.api.plugins.{PluginsReport, PluginState}
import com.histmersion.lint.{tolerated, LintRule}

/** The About window: `GET /api/about` — the server's build, its health, the job queue, the newest backup and how big
  * the store is. Read once when the window opens, and again on "refresh".
  */
object AboutView:

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** One line of the panel. `state` marks a health check with its badge; `depth` > 0 is a location inside the one above
    * it (the Storage section); `href` makes the value a link.
    */
  final case class Row(
      label: String,
      value: String,
      state: Option[ServiceState] = None,
      detail: Option[String] = None,
      depth: Int = 0,
      href: Option[String] = None
  )

  final case class Section(title: String, rows: List[Row])

  /** A health text as a row: its state, and the text itself as the detail unless it only says "ok". */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[web] def check(label: String, text: Option[String]): Row =
    val st = ServiceState.of(text.map(ServiceState.Wire(_)))
    Row(
      label,
      st.label.value,
      Some(st),
      text.map(_.trim).filter(t => t.nonEmpty && !t.equalsIgnoreCase(st.label.value))
    )

  /** The panel's content, as data — so what it says is a unit test. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def sections(a: About): List[Section] =
    val b = a.build
    val build = Section(
      "Server",
      List(
        Row("version", shown"${a.name} ${b.version}"),
        Row(
          "commit",
          b.gitCommit.fold("unknown")(c => if b.dirty.dirty then shown"$c (with uncommitted changes)" else shown"$c")
        ),
        Row("commit date", b.commitDate.fold("unknown")(_.value)),
        Row("licence", b.licence.fold("unknown")(_.value)),
        Row(
          "source",
          b.sourceUrl.fold("unknown")(_ => "the code of this version"),
          href = b.sourceUrl.map(_.value),
          detail = b.sourceUrl.map(_.value)
        ),
        Row("schema version", a.health.schemaVersion.fold("unknown")(_.value)),
        Row(
          "config file",
          a.configFile.path.fold("none")(_ => if a.configFile.loaded.loaded then "read" else "not found"),
          detail = a.configFile.path.map(p =>
            if a.configFile.unknownKeys.isEmpty then shown"$p"
            else shown"$p · ignored unknown: ${a.configFile.unknownKeys.mkString(", ")}"
          )
        )
      )
    )
    val health = Section(
      "Health",
      List(
        Row(
          "overall",
          a.health.status.value,
          Some(ServiceState.of(Some(ServiceState.Wire(a.health.status.value))))
        ),
        check("database", Some(a.health.db.value)),
        check("converter", a.health.converter.map(_.value))
      )
    )
    val j = a.jobs
    val jobs = Section("Job queue", JobState.values.toList.map(s => Row(s.wire.value, j.of(s).toString)))
    val backup = Section(
      "Backups",
      List(
        a.lastBackup.fold(Row("last backup", "none yet"))(bk =>
          Row(
            "last backup",
            a.lastBackupAgeSeconds.fold("unknown age")(s => s"${AboutMarkdown.age(s.value.toLong)} ago"),
            detail = Some(shown"${bk.file} · ${Backup.humanBytes(bk.bytes)}")
          )
        )
      )
    )
    val store = a.counts.toList.map(c =>
      Section(
        "Store",
        List(
          Row("sources", c.sources.toString),
          Row("findings", c.findings.toString),
          Row("articles", c.articles.toString),
          Row("events", c.events.toString),
          Row("gazetteer places", c.places.toString),
          Row("periods", c.periods.toString),
          Row("period presets", c.presets.toString)
        )
      )
    )
    val problems = Option.when(a.problems.nonEmpty)(
      Section(if a.problems.size == 1 then "Problem" else "Problems", a.problems.map(p => Row("", p.value)))
    )
    List(build, health, jobs, backup) ++ store ++ storage(a.storage) ++ plugins(a.plugins).toList ++ problems.toList

  /** Storage (where the data is on disk, how big each part is) and Disk space (the free space of each volume, and what
    * to delete when it runs low). Nothing from an older server; "measuring…" while the first measurement runs (the
    * window asks again).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[web] def storage(r: Storage.Report): List[Section] =
    if r.entries.isEmpty && !r.measuring.measuring && r.problems.isEmpty then Nil
    else
      val when = (r.ageSeconds.map(_.value.toLong).map(s => s"measured ${AboutMarkdown.age(s)} ago") ++
        Option.when(r.measuring.measuring)("measuring…")).mkString(" · ")
      val total = Option.when(r.entries.nonEmpty)(
        Row(
          "in all",
          Backup.humanBytes(ByteCount(Storage.total(r.entries).value.toLong)),
          detail = Option.when(when.nonEmpty)(when)
        )
      )
      val rows = Storage.shown(r.entries).map { (e, depth) =>
        Row(
          if e.host.value == "server" then e.label.value else shown"${e.label} (this machine)",
          Storage.size(e),
          state = Option.when(e.partial.partial)(ServiceState.Busy),
          detail = Option(Storage.details(r, e) ++ e.path.toList).filter(_.nonEmpty).map(_.mkString(" · ")),
          depth = depth.value
        )
      }
      val waiting = Option.when(r.entries.isEmpty && r.measuring.measuring)(Row("", "measuring…"))
      val disks = r.volumes.map { v =>
        val low = v.freeBytes.value.toLong < r.lowFreeBytes.value.toLong
        Row(
          v.id.value,
          s"${Backup.humanBytes(ByteCount(v.freeBytes.value.toLong))} free",
          state = Some(if low then ServiceState.Down else ServiceState.Ok),
          detail = Some(s"of ${Backup.humanBytes(ByteCount(v.totalBytes.value.toLong))}")
        )
      }
      val notes = r.hints.map(h => Row("", h.value)) ++ r.problems.map(p => Row("", p.value))
      List(Section("Storage", total.toList ++ waiting.toList ++ rows)) ++
        Option.when(disks.nonEmpty || notes.nonEmpty)(Section("Disk space", disks ++ notes)).toList

  /** The plugins in this binary (#2018): one row each — its id, what became of it (a badge: registered ok, disabled
    * grey, failed or refused down), its version, what it registered and its settings. A secret is only ever its
    * reference there (`env:NAME`, `vault:ref`, `value:***`): the server never puts a value into the report. None when
    * the report lists no plugin (an older server).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  private[web] def plugins(r: PluginsReport): Option[Section] =
    Option.when(r.plugins.nonEmpty || r.warnings.nonEmpty)(
      Section(
        "Plugins",
        r.plugins.map { p =>
          val st = PluginState.parse(p.state) match
            case Some(PluginState.Registered)                   => ServiceState.Ok
            case Some(PluginState.Failed | PluginState.Refused) => ServiceState.Down
            case _                                              => ServiceState.Unknown
          val detail = List(
            Some(shown"v${p.version}"),
            Option.when(p.provides.nonEmpty)(p.provides.mkString(", ")),
            Option.when(p.settings.nonEmpty)(p.settings.map(s => shown"${s.key} = ${s.value}").mkString(", ")),
            Option.when(p.errors.nonEmpty)(p.errors.map(_.sentence).mkString("; ")),
            Option.when(p.warnings.nonEmpty)(p.warnings.mkString("; "))
          ).flatten.mkString(" · ")
          Row(
            Wording.plugin(Wording.PluginId(p.id.value)),
            Wording.capitalize(p.state.value),
            Some(st),
            Some(shown"${p.id} · $detail")
          )
        } ++ r.warnings.map(w => Row("", w.value))
      )
    )

  // ── view ────────────────────────────────────────────────────────────────────────────────────────

  private val about = Var(Option.empty[About])
  private val failure = Var(Option.empty[String])

  /** Asks again while the server is still measuring storage (a few times, never forever). Mutable: the window's one
    * poll counter, reset by an explicit refresh.
    */
  private var polls = 0
  private val MaxPolls = 20
  private val PollMs = 1500.0

  private def refresh(): Unit =
    polls = 0
    load()

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def load(): Unit =
    ApiClient
      .about()
      .onComplete(
        _.fold(
          e => failure.set(Some(e.getMessage)),
          a => {
            failure.set(None); about.set(Some(a))
            if a.storage.measuring.measuring && polls < MaxPolls then
              polls += 1
              val _ = scala.scalajs.js.timers.setTimeout(PollMs)(if mounted then load())
          }
        )
      )

  /** The window is open (no polling into a closed one). Mutable for the same reason. */
  private var mounted = false

  def pane: HtmlElement =
    div(
      cls := "tool-pane about-pane",
      onMountUnmountCallback(_ => { mounted = true; refresh() }, _ => mounted = false),
      div(cls := "pane-bar", button(typ := "button", cls := "btn", "refresh", onClick --> (_ => refresh()))),
      child.maybe <-- failure.signal.map(
        _.map(m => p(cls := "form-error", role := "alert", s"Could not reach the server: $m"))
      ),
      child <-- about.signal.map {
        case None    => p(cls := "muted", "Loading…")
        case Some(a) => div(cls := "about-sections", sections(a).map(section))
      }
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def section(s: Section): HtmlElement =
    div(
      cls := "about-section",
      h3(cls := "about-title", s.title),
      dl(
        cls := "about-rows",
        s.rows.flatMap { r =>
          List(
            dt(
              cls := (if r.depth > 0 then s"about-nested depth-${math.min(r.depth, 3)}" else ""),
              Wording.heading(r.label)
            ),
            dd(
              r.href.fold(r.state.fold(span(r.value))(st => span(cls := s"about-state state-${st.label}", r.value)))(
                url => a(href := url, target := "_blank", rel := "noopener", r.value)
              ),
              r.detail.map(d => span(cls := "about-detail muted", d))
            )
          )
        }
      )
    )
