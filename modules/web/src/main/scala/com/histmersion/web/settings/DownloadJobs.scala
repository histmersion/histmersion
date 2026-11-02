package com.histmersion.web.settings

import com.histmersion.api.{JobInfo, ListFilters}
import com.histmersion.values.JobKind
import com.histmersion.api.Activity.Entry
import com.histmersion.web.{ActivityView, ApiClient}
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The downloads under way — the background map, the gazetteers, the event catalogues, the pictures — each with Pause,
  * Resume and Cancel in place (the jobs' own controls). Asked again while one is going.
  */
object DownloadJobs:

  /** The job kinds Data and downloads follows. */
  val Kinds: List[JobKind] =
    List(
      com.histmersion.util.basemap.BasemapJobKinds.BasemapFetch.kind,
      com.histmersion.util.data.exchange.ExchangeJobKinds.Init.kind,
      com.histmersion.api.catalogue.places.PlacesJobKinds.PlacesFetch.kind,
      com.histmersion.api.catalogue.events.EventsJobKinds.EventsImport.kind,
      com.histmersion.api.media.imports.ImportJobKinds.MediaImport.kind
    )

  private val jobs = Var(List.empty[JobInfo])
  private var mounted = false
  private var pollMs = 1500.0

  def configure(ms: Int): Unit = pollMs = math.max(500, ms).toDouble

  /** Read again now (after a download was started here). */
  def refresh(): Unit =
    ApiClient
      .jobs(ListFilters.JobFilter(status = List("running", "queued", "paused"), kind = Kinds.map(_.value)), Some(20))
      .foreach { (js, _) =>
        jobs.set(js)
        if mounted && js.exists(_.status != "paused") then
          val _ = scala.scalajs.js.timers.setTimeout(pollMs)(if mounted then refresh())
      }

  /** Is a job of `kind` going now? */
  def busy(kind: JobKind): Signal[Boolean] = jobs.signal.map(_.exists(_.kind == kind.value))

  def panel: HtmlElement =
    div(
      cls := "download-jobs",
      onMountUnmountCallback(_ => { mounted = true; refresh() }, _ => mounted = false),
      child <-- jobs.signal.map {
        case Nil => p(cls := "muted download-jobs-none", "Nothing is downloading now.")
        case js  =>
          ul(
            cls := "activity-list download-jobs-list",
            js.map { j =>
              val e = Entry(
                s"job:${j.id}",
                "job",
                j.createdAt,
                state = j.status,
                what = j.kind,
                text = j.progress.getOrElse(""),
                job = Some(j.id)
              )
              ActivityView.row(e, () => refresh())
            }
          )
      }
    )
