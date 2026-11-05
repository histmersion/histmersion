package com.histmersion.web.settings

import com.histmersion.api.Plural
import com.histmersion.web.{ApiClient, Auth, MapRegionsView}
import com.raquo.laminar.api.L.*

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.backup.Backup
import com.histmersion.api.catalogue.CatalogProviderInfo
import com.histmersion.api.instance.{About, UiConfig}
import com.histmersion.api.jobs.{JobDetail, JobInfo}
import com.histmersion.api.maintenance.Storage
import com.histmersion.api.plugins.PluginState
import com.histmersion.api.basemap.Basemap
import com.histmersion.api.catalogue.Gazetteer
import com.histmersion.api.media.MediaProvider

/** Settings › Data and downloads: the reference data Histmersion keeps on this computer — the background map, places,
  * periods and events, pictures — each with what it is, whether it is there, and Update / Remove; the downloads under
  * way with Pause and Cancel; and how much of the disk it all takes. Every action is a job the command line can start
  * too.
  */
object DataSection:

  /** What Histmersion's files take, as a share of the free space left on the disk, 0–100. Pure. */
  def share(used: Long, free: Long): Int =
    if used <= 0 then 0
    else if free <= 0 then 100
    else math.min(100, math.round(used.toDouble * 100 / free.toDouble).toInt)

  def pane(ui: UiConfig): HtmlElement =
    val about = Var(Option.empty[About])
    val basemap = Var(Option.empty[Basemap.Status])
    val sources = Var(List.empty[Gazetteer.SourceInfo])
    val cats = Var(List.empty[CatalogProviderInfo])
    val media = Var(List.empty[MediaProvider.Info])
    val starter = Starter()
    def reload(): Unit =
      ApiClient.about().foreach(a => about.set(Some(a)))
      ApiClient.basemap().foreach(b => basemap.set(Some(b)))
      ApiClient.placeSources().foreach(sources.set)
      ApiClient.eventCatalogues().foreach(cats.set)
      ApiClient.mediaProviders().foreach(media.set)
    def act(text: String, what: String, job: => Future[JobInfo], primary: Boolean = false): HtmlElement =
      starter.act(text, what, job, primary)
    div(
      cls := "settings-section data-section",
      onMountCallback(_ => reload()),
      div(
        cls := "data-head",
        p(
          cls := "settings-lead",
          "Reference data Histmersion keeps on this computer. Everything here can also be done from the command line."
        ),
        child.maybe <-- about.signal.map(_.map(a => disk(a.storage)))
      ),
      child.maybe <-- Auth.writable.map(w =>
        Option.when(!w)(div(cls := "pane-note", Auth.keyNeeded("download or remove data")))
      ),
      child.maybe <-- starter.said.signal.map(_.map {
        case Left(why) => com.histmersion.web.ErrorLine.text(why)
        case Right(m)  => p(cls := "settings-saved-line", role := "status", m)
      }),
      card("Downloading now", DownloadJobs.panel),
      card(
        "Maps",
        div(
          child <-- basemap.signal.map {
            case None    => p(cls := "muted", "Loading…")
            case Some(b) => div(maps(b, starter))
          },
          detailsTag(
            cls := "data-more",
            summaryTag("Choose the area, the depth and detailed relief"),
            MapRegionsView.panel
          )
        )
      ),
      card(
        "Places, periods and events",
        div(
          child <-- about.signal.map(a =>
            row(
              "Ancient places, regions and named periods",
              "Pleiades, the historical boundaries and the named periods (PeriodO)" +
                a.flatMap(_.counts)
                  .fold("")(c =>
                    s" · ${Plural.count(c.places.toInt, "place")} · ${Plural.count(c.periods.toInt, "period")}"
                  ),
              span(
                cls := (if a.flatMap(_.counts).exists(_.places > 0) then "data-state"
                        else "data-state data-state--off"),
                a.flatMap(_.counts).fold("")(c => if c.places > 0 then "Installed" else "Not loaded yet")
              ),
              act("Update now", "boundaries", ApiClient.initData(fetch = true))
            )
          ),
          children <-- sources.signal.map(
            _.filter(_.kind != Gazetteer.Kind.Lpf).map(s =>
              row(
                s.title,
                (s.licence.toList ++ s.places.map(n => Plural.count(n, "place")).toList).mkString(" · "),
                span(
                  cls := (if s.importedAt.isDefined then "data-state" else "data-state data-state--off"),
                  s.importedAt.fold("Not loaded")(t => s"Loaded ${t.take(10)}")
                ),
                act(
                  if s.importedAt.isDefined then "Update now" else "Load",
                  s"places-${s.key}",
                  ApiClient.loadPlaces(s.key)
                )
              )
            )
          ),
          children <-- cats.signal.map(
            _.map(c =>
              row(
                c.title,
                c.licence,
                span(cls := "data-state", ""),
                act("Update now", s"events-${c.id}", ApiClient.importEvents(c.id))
              )
            )
          )
        )
      ),
      card(
        "Pictures of places and objects",
        div(
          children <-- media.signal.map(ms =>
            ms.map { m =>
              val on = ui.plugins.plugins.find(_.id == m.plugin).forall(_.state == PluginState.Registered.wire)
              row(
                m.title,
                m.description,
                span(
                  cls := (if on then "data-state" else "data-state data-state--off"),
                  if on then "" else "Turned off: see Plugins and Keys"
                ),
                if on then act("Fetch new pictures", s"media-${m.name}", ApiClient.importMedia(m.name)) else span()
              )
            }
          )
        )
      )
    )

  private def card(title: String, body: HtmlElement): HtmlElement =
    sectionTag(
      cls := "settings-card",
      aria.label := title,
      div(cls := "settings-card-head", h2(cls := "settings-card-title", title)),
      div(cls := "settings-card-body data-rows", body)
    )

  private def row(title: String, detail: String, state: HtmlElement, actions: HtmlElement*): HtmlElement =
    div(
      cls := "data-row",
      div(cls := "data-what", strong(title), Option.when(detail.nonEmpty)(div(cls := "muted", detail))),
      state,
      div(cls := "data-actions", actions)
    )

  /** Starts a download and says what became of the request. */
  final private class Starter:
    val said = Var(Option.empty[Either[String, String]])
    def act(text: String, what: String, job: => Future[JobInfo], primary: Boolean = false): HtmlElement =
      button(
        typ := "button",
        cls := (if primary then "btn btn--primary data-action" else "btn data-action"),
        text,
        dataAttr("action") := what,
        disabled <-- Auth.writable.map(!_),
        onClick --> (_ => start(text, what, job))
      )
    private def start(text: String, what: String, job: => Future[JobInfo]): Unit =
      job.onComplete { r =>
        r.toEither.left.map(SettingsStore.message) match
          case Left(why) => said.set(Some(Left(why)))
          case Right(j)
              if j.detail.flatMap(JobDetail.Purpose.of).exists(_.startsWith("dry run")) || what.endsWith("-check") =>
            said.set(Some(Right(s"$text: checking…")))
            follow(text, j.id, 0)
          case Right(_) =>
            said.set(Some(Right(s"$text: started. It is listed under “Downloading now”.")))
            DownloadJobs.refresh()
      }

    /** A check (a dry run) is quick: follow it to its end and say what it found. */
    private def follow(text: String, id: String, n: Int): Unit =
      val _ = scala.scalajs.js.timers.setTimeout(1000) {
        ApiClient.job(id).foreach { j =>
          if !j.isTerminal && n < 120 then follow(text, id, n + 1)
          else
            said.set(Some(if j.succeeded then Right(s"$text: ${j.progress.getOrElse("done")}")
            else Left(s"$text: ${j.error.getOrElse(j.status)}")))
        }
      }

  private def maps(b: Basemap.Status, starter: Starter): List[HtmlElement] =
    def act(text: String, what: String, job: => Future[JobInfo], primary: Boolean) =
      starter.act(text, what, job, primary)
    val main =
      b.layers.filter(l =>
        Set(Basemap.Layer.Vector.wire, Basemap.Layer.Terrain.wire, Basemap.Layer.Labels.wire)(l.layer)
      )
    val sea = b.layers.filter(_.layer == Basemap.Layer.Bathymetry.wire)
    def size(ls: List[Basemap.LayerInfo]) = Backup.humanBytes(ls.filter(_.present).map(_.bytes).sum)
    def when(ls: List[Basemap.LayerInfo]) =
      ls.flatMap(_.fetchedAt).maxOption.map(t => s" · ${t.take(10)}").getOrElse("")
    val area = if b.bbox.trim.equalsIgnoreCase("world") then "the whole world" else b.bbox
    val removeAll = Var(false)
    List(
      row(
        s"Background map — $area",
        "Terrain, coastlines, rivers and physical names, for use offline" +
          (if main.exists(_.present) then s" · ${size(main)}" else ""),
        span(
          cls := (if main.exists(_.present) then "data-state" else "data-state data-state--off"),
          if main.exists(_.present) then s"Installed${when(main)}" else "Not downloaded"
        ),
        act("Check for update", "basemap-check", ApiClient.basemapFetch(Basemap.FetchQuery(dryRun = true)), false),
        act(
          if main.exists(_.present) then "Update" else "Download",
          "basemap",
          ApiClient.basemapFetch(Basemap.FetchQuery()),
          !main.exists(_.present)
        ),
        Option
          .when(main.exists(_.present))(
            button(
              typ := "button",
              cls := "btn btn--ghost data-remove",
              child.text <-- removeAll.signal.map(c => if c then "Remove — sure?" else "Remove"),
              disabled <-- Auth.writable.map(!_),
              onClick --> (_ =>
                if removeAll.now() then
                  ApiClient.basemapRemove(Basemap.RemoveQuery("all")).foreach(_ => removeAll.set(false))
                else removeAll.set(true)
              )
            )
          )
          .getOrElse(span())
      ),
      row(
        "Sea depth",
        "The sea floor's depth, coloured on the Physical map" + (if sea.exists(_.present) then s" · ${size(sea)}"
                                                                 else ""),
        span(
          cls := (if sea.exists(_.present) then "data-state" else "data-state data-state--off"),
          if sea.exists(_.present) then s"Installed${when(sea)}" else "Not downloaded"
        ),
        act(
          if sea.exists(_.present) then "Update" else "Download",
          "bathymetry",
          ApiClient.basemapFetch(Basemap.FetchQuery(layer = Basemap.Layer.Bathymetry.wire)),
          false
        )
      )
    )

  private def disk(r: Storage.Report): HtmlElement =
    val used = Storage.total(r.entries).value.toLong
    val free = r.volumes.map(_.freeBytes.value.toLong).minOption.getOrElse(0L)
    val pct = share(used, free)
    div(
      cls := "data-disk",
      div(
        cls := "data-disk-line",
        span("Disk used by Histmersion"),
        strong(if r.entries.isEmpty then (if r.measuring then "measuring…" else "unknown") else Backup.humanBytes(used))
      ),
      div(cls := "meter", styleAttr := s"--share:$pct%", div(cls := "meter-fill")),
      div(
        cls := "muted",
        if r.volumes.isEmpty then "" else s"$pct% of the free space on this disk · ${Backup.humanBytes(free)} free"
      )
    )
