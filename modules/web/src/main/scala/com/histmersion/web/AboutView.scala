package com.histmersion.web

import com.histmersion.core.*
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The About window: `GET /api/about` — the server's build, its health, the job queue, the newest
  * backup and how big the store is. Read once when the window opens, and again on "refresh". */
object AboutView:

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** One line of the panel. `state` marks a health check with its badge. */
  final case class Row(label: String, value: String, state: Option[ServiceState] = None, detail: Option[String] = None)
  final case class Section(title: String, rows: List[Row])

  /** A health text as a row: its state, and the text itself as the detail unless it only says "ok". */
  private[web] def check(label: String, text: Option[String]): Row =
    val st = ServiceState.of(text)
    Row(label, st.label, Some(st), text.map(_.trim).filter(t => t.nonEmpty && !t.equalsIgnoreCase(st.label)))

  /** The panel's content, as data — so what it says is a unit test. */
  def sections(a: About): List[Section] =
    val b = a.build
    val build = Section("Server", List(
      Row("version", s"${a.name} ${b.version}"),
      Row("commit", b.gitCommit.fold("unknown")(c => if b.dirty then s"$c (with uncommitted changes)" else c)),
      Row("commit date", b.commitDate.getOrElse("unknown")),
      Row("schema version", a.health.schemaVersion.getOrElse("unknown"))))
    val health = Section("Health", List(
      Row("overall", a.health.status, Some(ServiceState.of(Some(a.health.status)))),
      check("database", Some(a.health.db)),
      check("converter", a.health.converter)))
    val j = a.jobs
    val jobs = Section("Job queue",
      JobState.values.toList.map(s => Row(s.wire, j.of(s).toString)))
    val backup = Section("Backups", List(
      a.lastBackup.fold(Row("last backup", "none yet"))(bk =>
        Row("last backup", a.lastBackupAgeSeconds.fold("unknown age")(s => s"${AboutMarkdown.age(s)} ago"),
          detail = Some(s"${bk.file} · ${Backup.humanBytes(bk.bytes)}")))))
    val store = a.counts.toList.map(c => Section("Store", List(
      Row("sources", c.sources.toString),
      Row("findings", c.findings.toString),
      Row("articles", c.articles.toString),
      Row("events", c.events.toString),
      Row("gazetteer places", c.places.toString),
      Row("period presets", c.periods.toString))))
    val problems = Option.when(a.problems.nonEmpty)(
      Section(if a.problems.size == 1 then "Problem" else "Problems", a.problems.map(p => Row("", p))))
    List(build, health, jobs, backup) ++ store ++ problems.toList

  // ── view ────────────────────────────────────────────────────────────────────────────────────────

  private val about   = Var(Option.empty[About])
  private val failure = Var(Option.empty[String])

  private def refresh(): Unit =
    ApiClient.about().onComplete(_.fold(
      e => failure.set(Some(e.getMessage)),
      a => { failure.set(None); about.set(Some(a)) }))

  def pane: HtmlElement =
    div(
      cls := "tool-pane about-pane",
      onMountCallback(_ => refresh()),
      div(cls := "pane-bar",
        button(typ := "button", cls := "btn", "refresh", onClick --> (_ => refresh()))),
      child.maybe <-- failure.signal.map(_.map(m => p(cls := "form-error", role := "alert", s"Could not reach the server: $m"))),
      child <-- about.signal.map {
        case None    => p(cls := "muted", "Loading…")
        case Some(a) => div(cls := "about-sections", sections(a).map(section))
      }
    )

  private def section(s: Section): HtmlElement =
    div(
      cls := "about-section",
      h3(cls := "about-title", s.title),
      dl(cls := "about-rows", s.rows.flatMap { r =>
        List(
          dt(r.label),
          dd(
            r.state.fold(span(r.value))(st => span(cls := s"about-state state-${st.label}", r.value)),
            r.detail.map(d => span(cls := "about-detail muted", d))))
      })
    )
