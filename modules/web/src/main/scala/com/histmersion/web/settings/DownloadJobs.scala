package com.histmersion
package web.settings

import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.automation.ListFilters
import com.histmersion.api.automation.Activity.Entry
import com.histmersion.web.{ActivityView, ApiClient}
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.basemap.BasemapJobKinds
import com.histmersion.api.exchange.ExchangeJobKinds
import com.histmersion.api.catalogue.PlacesJobKinds
import com.histmersion.api.catalogue.EventsJobKinds
import com.histmersion.api.media.ImportJobKinds
import com.histmersion.api.jobs.JobState
import com.histmersion.api.Paging
import com.histmersion.lint.{LintRule, tolerated}

/** The downloads under way — the background map, the gazetteers, the event catalogues, the pictures — each with Pause,
  * Resume and Cancel in place (the jobs' own controls). Asked again while one is going.
  */
object DownloadJobs:

  /** The job kinds Data and downloads follows. */
  type ConfigureMs = ConfigureMs.T
  object ConfigureMs extends Newtype[Int, Pure]

  val Kinds: List[JobInfo.Kind] =
    List(
      BasemapJobKinds.BasemapFetch.kind,
      ExchangeJobKinds.Init.kind,
      PlacesJobKinds.PlacesFetch.kind,
      EventsJobKinds.EventsImport.kind,
      ImportJobKinds.MediaImport.kind
    )

  private val jobs = Var(List.empty[JobInfo])
  private var mounted = false
  private var pollMs = 1500.0

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def configure(ms: ConfigureMs): Unit = pollMs = math.max(500, ms.value).toDouble

  /** Read again now (after a download was started here). */
  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  def refresh(): Unit =
    ApiClient
      .jobs(
        ListFilters.JobFilter(
          status = List(JobState.Wire("running"), JobState.Wire("queued"), JobState.Wire("paused")),
          kind = Kinds
        ),
        Some(Paging.Limit(20))
      )
      .foreach { (js, _) =>
        jobs.set(js)
        if mounted && js.exists(_.status != JobState.Paused.wire) then
          val _ = scala.scalajs.js.timers.setTimeout(pollMs)(if mounted then refresh())
      }

  /** Is a job of `kind` going now? */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def busy(kind: JobInfo.Kind): Signal[Boolean] = jobs.signal.map(_.exists(_.kind == kind))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
                Entry.Key(s"job:${j.id}"),
                Entry.Kind("job"),
                Entry.At(j.createdAt.value),
                state = Entry.State(j.status.value),
                what = Entry.What(j.kind.value),
                text = Entry.Text(j.progress.map(_.value).getOrElse("")),
                job = Some(j.id)
              )
              ActivityView.row(e, () => refresh())
            }
          )
      }
    )
