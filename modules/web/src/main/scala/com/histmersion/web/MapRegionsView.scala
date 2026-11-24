package com.histmersion
package web
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.api.ServiceState
import com.raquo.laminar.api.L.*

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.backup.Backup
import com.histmersion.api.instance.UiConfig
import com.histmersion.api.jobs.{JobDetail, JobInfo}
import com.histmersion.api.basemap.Basemap
import com.histmersion.api.BBox
import com.histmersion.api.catalogue.MapArea
import com.histmersion.api.ByteCount
import com.histmersion.lint.{tolerated, LintRule}

/** The About window's "Map regions" panel: pick a named region or type a box, see what it would download zoom by zoom
  * (a dry run of the fetch job), download it and follow the job, and remove what was downloaded. Over the base map's
  * REST: `GET /api/basemap`, `POST /api/basemap/fetch`, `GET /api/jobs/{id}`, `DELETE /api/basemap`.
  */
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
object MapRegionsView:
  import MapRegions.*

  private var jobPollMs = 1000 // set from /api/config before any window opens

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def configure(ui: UiConfig): Unit = jobPollMs = math.max(200, ui.jobPollMs.value)

  private val status = Var(Option.empty[Basemap.Status])
  private val pick = Var(RegionChoice.Id("configured"))
  private val box = Var(BBox.Text(""))
  private val content = Var(Content.Map)
  private val depth = Var(ZoomText(""))
  private val job = Var(Option.empty[JobInfo])

  /** The fetch whose dry run succeeded last: downloading asks for the same, so its size was seen. */
  private val checked = Var(Option.empty[Basemap.FetchQuery])
  private val error = Var(Option.empty[String])
  private val confirmAll = Var(false)

  /** The window is open (no polling into a closed one); mutable like the About window's own. */
  private var mounted = false

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def load(): Unit =
    ApiClient
      .basemap()
      .onComplete(
        _.fold(
          e => error.set(Some(e.getMessage)),
          s => {
            status.set(Some(s))
            if box.now().value.isEmpty then box.set(s.bbox)
          }
        )
      )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def namedOf(id: RegionChoice.Id): Option[Basemap.Focus.Name] =
    status.now().flatMap(_.regions.find(_.name.value == id.value)).map(_.name)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def current(dryRun: Basemap.FetchQuery.DryRun): Either[Problem, Basemap.FetchQuery] =
    request(MapArea.Bbox(box.now().value), content.now(), namedOf(pick.now()), depth.now(), dryRun)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def choose(id: RegionChoice.Id): Unit =
    pick.set(id)
    status.now().toList.flatMap(choices).find(_.id == id).flatMap(_.bbox).foreach(box.set)
    checked.set(None)

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def start(dryRun: Basemap.FetchQuery.DryRun): Unit =
    current(dryRun) match
      case Left(m)  => error.set(Some(m.sentence))
      case Right(q) =>
        error.set(None)
        follow(ApiClient.basemapFetch(q), Option.when(dryRun.value)(q))

  /** Follow the job until it ends; a dry run that succeeds marks its fetch as checked. */
  private def follow(started: Future[JobInfo], dry: Option[Basemap.FetchQuery]): Unit =
    started.onComplete(_.fold(e => error.set(Some(e.getMessage)), j => { job.set(Some(j)); poll(j.id, dry) }))

  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def poll(id: JobInfo.Id, dry: Option[Basemap.FetchQuery]): Unit =
    val _ = scala.scalajs.js.timers.setTimeout(jobPollMs.toDouble) {
      if mounted then
        ApiClient
          .job(id)
          .onComplete(
            _.fold(
              e => error.set(Some(e.getMessage)),
              j => {
                job.set(Some(j))
                if !j.isTerminal then poll(id, dry)
                else
                  if j.succeeded then
                    dry.foreach(q => checked.set(Some(q.copy(dryRun = Basemap.FetchQuery.DryRun(false)))))
                  load()
              }
            )
          )
    }

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def remove(q: Basemap.RemoveQuery): Unit =
    ApiClient
      .basemapRemove(q)
      .onComplete(_.fold(e => error.set(Some(e.getMessage)), _ => { confirmAll.set(false); load() }))

  private val canDownload: Signal[Boolean] =
    checked.signal.combineWith(box.signal, content.signal, depth.signal, pick.signal, job.signal, Auth.writable).map {
      (ok, _, _, _, _, j, writable) =>
        writable && !j.exists(!_.isTerminal) && ok
          .exists(q => current(dryRun = Basemap.FetchQuery.DryRun(false)).toOption.contains(q))
    }

  private val busy: Signal[Boolean] = job.signal.map(_.exists(!_.isTerminal))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def panel: HtmlElement =
    div(
      cls := "about-section map-regions",
      onMountUnmountCallback(_ => { mounted = true; load() }, _ => mounted = false),
      h3(cls := "about-title", "Map regions"),
      child.maybe <-- Auth.writable.map(w =>
        Option.when(!w)(div(cls := "pane-note", Auth.keyNeeded("download map regions")))
      ),
      child <-- status.signal.map(_.fold[HtmlElement](p(cls := "muted", "Loading…"))(downloaded)),
      div(
        cls := "map-regions-form",
        div(
          cls := "form-field",
          label(
            "region",
            select(
              cls := "field map-region-pick",
              children <-- status.signal.map(
                _.toList.flatMap(choices).map(c => option(value := c.id.value, c.label))
              ),
              // the options are made again when the status is read again: the choice is set again after them
              controlled(
                value <-- status.signal.combineWith(pick.signal).map(_._2.value),
                onChange.mapToValue --> (v => choose(RegionChoice.Id(v)))
              )
            )
          )
        ),
        div(
          cls := "form-field",
          label(
            "box (west, south, east, north)",
            input(
              typ := "text",
              cls := "field map-region-bbox",
              placeholder := "minLon,minLat,maxLon,maxLat or world",
              controlled(
                value <-- box.signal.map(_.value),
                onInput.mapToValue --> { v =>
                  box.set(BBox.Text(v)); pick.set(RegionChoice.Id(OwnBox)); checked.set(None)
                }
              )
            )
          )
        ),
        div(
          cls := "form-field",
          label(
            "download",
            select(
              cls := "field map-region-content",
              Content.values.toList.map(c => option(value := c.toString, c.label)),
              controlled(
                value <-- content.signal.map(_.toString),
                onChange.mapToValue --> { v => content.set(Content.parse(Content.Name(v))); checked.set(None) }
              )
            )
          )
        ),
        div(
          cls := "form-field",
          label(
            "deepest zoom",
            input(
              typ := "text",
              cls := "field map-region-zoom",
              placeholder := "as configured",
              controlled(
                value <-- depth.signal.map(_.value),
                onInput.mapToValue --> { v => depth.set(ZoomText(v)); checked.set(None) }
              )
            )
          )
        ),
        child.maybe <-- error.signal.map(_.map(m => div(cls := "field-error", role := "alert", m))),
        div(
          cls := "form-actions",
          button(
            typ := "button",
            cls := "btn map-region-check",
            "check the size",
            disabled <-- busy,
            onClick --> (_ => start(dryRun = Basemap.FetchQuery.DryRun(true)))
          ),
          button(
            typ := "button",
            cls := "btn btn--primary map-region-download",
            "download",
            title := "check the size first",
            disabled <-- canDownload.map(!_),
            onClick --> (_ => start(dryRun = Basemap.FetchQuery.DryRun(false)))
          )
        )
      ),
      child.maybe <-- job.signal.map(_.map(jobView))
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def jobView(j: JobInfo): HtmlElement =
    val dry = j.detail.flatMap(JobDetail.Purpose.of).exists(_.value.startsWith("dry run"))
    val state = if j.succeeded then ServiceState.Ok else if j.isTerminal then ServiceState.Down else ServiceState.Busy
    div(
      cls := "map-region-job",
      p(
        span(
          cls := s"about-state state-${state.label}",
          if dry then s"size check: ${j.status}" else s"download: ${j.status}"
        ),
        j.detail.flatMap(JobDetail.Purpose.of).map(a => span(cls := "about-detail muted", a))
      ),
      j.problems.toList.flatMap(_.sentences).map(s => p(cls := "form-error", role := "alert", s)),
      j.progress.filter(_ => !j.isTerminal).map(t => p(cls := "muted map-region-progress", t)),
      Option
        .when(j.isTerminal)(j.progress)
        .flatten
        .map(t =>
          div(
            cls := "map-region-sizes",
            sizes(SizesText(t.value)).map { l =>
              div(
                p(cls := "map-region-layer", l.line),
                Option.when(l.zooms.nonEmpty)(ul(cls := "map-region-zooms", l.zooms.map(z => li(z))))
              )
            }
          )
        )
    )

  /** What is downloaded now: the map's region and each detailed-relief region, each with its remove. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def downloaded(s: Basemap.Status): HtmlElement =
    val map = s.layers.find(l => l.layer == Basemap.Layer.Vector.wire && l.present.value)
    val focus = s.layers.find(_.layer == Basemap.Layer.Terrain.wire).toList.flatMap(_.focus)
    dl(
      cls := "about-rows map-regions-downloaded",
      map.toList.flatMap { m =>
        List(
          dt("the map"),
          dd(
            span(s"${m.bbox.getOrElse("")}, zoom ≤ ${m.maxZoom.getOrElse(0)}"),
            span(
              cls := "about-detail muted",
              s"${Backup.humanBytes(ByteCount(s.layers.filter(_.present.value).map(_.bytes.value).sum))} in all"
            ),
            button(
              typ := "button",
              cls := "btn btn--sm btn--danger map-region-remove-all",
              child.text <-- confirmAll.signal.map(c => if c then "remove everything — sure?" else "remove everything"),
              disabled <-- Auth.writable.combineWith(busy).map((w, b) => !w || b),
              onClick --> (_ =>
                if confirmAll.now() then remove(Basemap.RemoveQuery(Basemap.RemoveQuery.Layer("all")))
                else confirmAll.set(true)
              )
            )
          )
        )
      } ++ focus.flatMap { f =>
        List(
          dt(s"relief: ${f.name}"),
          dd(
            span(s"${f.bbox}, zoom ${f.minZoom}–${f.maxZoom}"),
            span(cls := "about-detail muted", Backup.humanBytes(f.bytes)),
            button(
              typ := "button",
              cls := "btn btn--sm map-region-remove",
              dataAttr("region") := f.name,
              "remove",
              disabled <-- Auth.writable.combineWith(busy).map((w, b) => !w || b),
              onClick --> (_ => remove(Basemap.RemoveQuery(Basemap.RemoveQuery.Layer("none"), focus = Some(f.name))))
            )
          )
        )
      } ++ Option
        .when(map.isEmpty && focus.isEmpty)(
          List(dt("nothing"), dd(span(cls := "muted", "no region is downloaded yet")))
        )
        .toList
        .flatten
    )
