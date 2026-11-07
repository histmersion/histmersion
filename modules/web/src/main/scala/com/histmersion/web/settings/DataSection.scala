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
  type ActionLabel = ActionLabel.T
  object ActionLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ActionWhat = ActionWhat.T
  object ActionWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SharePercent = SharePercent.T
  object SharePercent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What Histmersion's files take, as a share of the free space left on the disk, 0–100. Pure. */


  def share(used: com.histmersion.api.ByteCount, free: com.histmersion.api.ByteCount): com.histmersion.web.settings.DataSection.SharePercent =
    if used.value <= 0 then SharePercent(0)
    else if free.value <= 0 then SharePercent(100)
    else SharePercent(math.min(100, math.round(used.value.toDouble * 100 / free.value.toDouble).toInt))

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
    def act(text: com.histmersion.web.settings.DataSection.ActionLabel, what: com.histmersion.web.settings.DataSection.ActionWhat, job: => Future[JobInfo], primary: Boolean = false): HtmlElement =
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
        case Left(why) => com.histmersion.web.ErrorLine.text(com.histmersion.web.ErrorLine.Problem(why))
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
                    s" · ${Plural.count(Plural.CountN(c.places.value), com.histmersion.api.Plural.CountSingular("place"))} · ${Plural.count(Plural.CountN(c.periods.value), com.histmersion.api.Plural.CountSingular("period"))}"
                  ),
              span(
                cls := (if a.flatMap(_.counts).exists(_.places.value > 0) then "data-state"
                        else "data-state data-state--off"),
                a.flatMap(_.counts).fold("")(c => if c.places.value > 0 then "Installed" else "Not loaded yet")
              ),
              act(com.histmersion.web.settings.DataSection.ActionLabel("Update now"), com.histmersion.web.settings.DataSection.ActionWhat("boundaries"), ApiClient.initData(fetch = com.histmersion.web.ApiClient.Fetch(true)))
            )
          ),
          children <-- sources.signal.map(
            _.filter(_.kind != Gazetteer.Kind.Lpf).map(s =>
              row(
                s.title.value,
                (s.licence.map(_.value).toList ++ s.places.map(n => Plural.count(Plural.CountN(n.value), com.histmersion.api.Plural.CountSingular("place")).value).toList).mkString(" · "),
                span(
                  cls := (if s.importedAt.isDefined then "data-state" else "data-state data-state--off"),
                  s.importedAt.fold("Not loaded")(t => s"Loaded ${t.value.take(10)}")
                ),
                act(
                  if s.importedAt.isDefined then com.histmersion.web.settings.DataSection.ActionLabel("Update now") else com.histmersion.web.settings.DataSection.ActionLabel("Load"),
                  com.histmersion.web.settings.DataSection.ActionWhat(s"places-${s.key}"),
                  ApiClient.loadPlaces(com.histmersion.api.Api.ExternalPlaces.Source(s.key.value))
                )
              )
            )
          ),
          children <-- cats.signal.map(
            _.map(c =>
              row(
                c.title.value,
                c.licence.value,
                span(cls := "data-state", ""),
                act(com.histmersion.web.settings.DataSection.ActionLabel("Update now"), com.histmersion.web.settings.DataSection.ActionWhat(s"events-${c.id}"), ApiClient.importEvents(ApiClient.Provider(c.id.value)))
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
                m.title.value,
                m.description.value,
                span(
                  cls := (if on then "data-state" else "data-state data-state--off"),
                  if on then "" else "Turned off: see Plugins and Keys"
                ),
                if on then act(com.histmersion.web.settings.DataSection.ActionLabel("Fetch new pictures"), com.histmersion.web.settings.DataSection.ActionWhat(s"media-${m.name}"), ApiClient.importMedia(ApiClient.Provider(m.name.value))) else span()
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
    def act(text: com.histmersion.web.settings.DataSection.ActionLabel, what: com.histmersion.web.settings.DataSection.ActionWhat, job: => Future[JobInfo], primary: Boolean = false): HtmlElement =
      button(
        typ := "button",
        cls := (if primary then "btn btn--primary data-action" else "btn data-action"),
        text.value,
        dataAttr("action") := what.value,
        disabled <-- Auth.writable.map(!_),
        onClick --> (_ => start(text, what, job))
      )
    private def start(text: com.histmersion.web.settings.DataSection.ActionLabel, what: com.histmersion.web.settings.DataSection.ActionWhat, job: => Future[JobInfo]): Unit =
      job.onComplete { r =>
        r.toEither.left.map(SettingsStore.message) match
          case Left(why) => said.set(Some(Left(why.value)))
          case Right(j)
              if j.detail.flatMap(JobDetail.Purpose.of).exists(_.value.startsWith("dry run")) || what.value.endsWith("-check") =>
            said.set(Some(Right(s"$text: checking…")))
            follow(text, j.id, 0)
          case Right(_) =>
            said.set(Some(Right(s"$text: started. It is listed under “Downloading now”.")))
            DownloadJobs.refresh()
      }

    /** A check (a dry run) is quick: follow it to its end and say what it found. */
    private def follow(text: com.histmersion.web.settings.DataSection.ActionLabel, id: JobInfo.Id, n: Int): Unit =
      val _ = scala.scalajs.js.timers.setTimeout(1000) {
        ApiClient.job(id).foreach { j =>
          if !j.isTerminal && n < 120 then follow(text, id, n + 1)
          else
            said.set(Some(if j.succeeded then Right(s"$text: ${j.progress.getOrElse("done")}")
            else Left(s"$text: ${j.error.getOrElse(j.status)}")))
        }
      }

  private def maps(b: Basemap.Status, starter: Starter): List[HtmlElement] =
    def act(text: com.histmersion.web.settings.DataSection.ActionLabel, what: com.histmersion.web.settings.DataSection.ActionWhat, job: => Future[JobInfo], primary: Boolean) =
      starter.act(text, what, job, primary)
    val main =
      b.layers.filter(l =>
        Set(Basemap.Layer.Vector.wire, Basemap.Layer.Terrain.wire, Basemap.Layer.Labels.wire)(l.layer)
      )
    val sea = b.layers.filter(_.layer == Basemap.Layer.Bathymetry.wire)
    def size(ls: List[Basemap.LayerInfo]) = Backup.humanBytes(com.histmersion.api.ByteCount(ls.filter(_.present.value).map(_.bytes.value).sum))
    def when(ls: List[Basemap.LayerInfo]) =
      ls.flatMap(_.fetchedAt).map(_.value).maxOption.map(t => s" · ${t.take(10)}").getOrElse("")
    val area = if b.bbox.value.trim.equalsIgnoreCase("world") then "the whole world" else b.bbox.value
    val removeAll = Var(false)
    List(
      row(
        s"Background map — $area",
        "Terrain, coastlines, rivers and physical names, for use offline" +
          (if main.exists(_.present.value) then s" · ${size(main)}" else ""),
        span(
          cls := (if main.exists(_.present.value) then "data-state" else "data-state data-state--off"),
          if main.exists(_.present.value) then s"Installed${when(main)}" else "Not downloaded"
        ),
        act(com.histmersion.web.settings.DataSection.ActionLabel("Check for update"), com.histmersion.web.settings.DataSection.ActionWhat("basemap-check"), ApiClient.basemapFetch(Basemap.FetchQuery(dryRun = com.histmersion.api.basemap.Basemap.FetchQuery.DryRun(true))), false),
        act(
          if main.exists(_.present.value) then com.histmersion.web.settings.DataSection.ActionLabel("Update") else com.histmersion.web.settings.DataSection.ActionLabel("Download"),
          com.histmersion.web.settings.DataSection.ActionWhat("basemap"),
          ApiClient.basemapFetch(Basemap.FetchQuery()),
          !main.exists(_.present.value)
        ),
        Option
          .when(main.exists(_.present.value))(
            button(
              typ := "button",
              cls := "btn btn--ghost data-remove",
              child.text <-- removeAll.signal.map(c => if c then "Remove — sure?" else "Remove"),
              disabled <-- Auth.writable.map(!_),
              onClick --> (_ =>
                if removeAll.now() then
                  ApiClient.basemapRemove(Basemap.RemoveQuery(com.histmersion.api.basemap.Basemap.RemoveQuery.Layer("all"))).foreach(_ => removeAll.set(false))
                else removeAll.set(true)
              )
            )
          )
          .getOrElse(span())
      ),
      row(
        "Sea depth",
        "The sea floor's depth, coloured on the Physical map" + (if sea.exists(_.present.value) then s" · ${size(sea)}"
                                                                 else ""),
        span(
          cls := (if sea.exists(_.present.value) then "data-state" else "data-state data-state--off"),
          if sea.exists(_.present.value) then s"Installed${when(sea)}" else "Not downloaded"
        ),
        act(
          if sea.exists(_.present.value) then com.histmersion.web.settings.DataSection.ActionLabel("Update") else com.histmersion.web.settings.DataSection.ActionLabel("Download"),
          com.histmersion.web.settings.DataSection.ActionWhat("bathymetry"),
          ApiClient.basemapFetch(Basemap.FetchQuery(layer = Basemap.Layer.Bathymetry.wire)),
          false
        )
      )
    )

  private def disk(r: Storage.Report): HtmlElement =
    val used = Storage.total(r.entries)
    val free = com.histmersion.api.ByteCount(r.volumes.map(_.freeBytes.value).minOption.getOrElse(0L))
    val pct = share(used, free)
    div(
      cls := "data-disk",
      div(
        cls := "data-disk-line",
        span("Disk used by Histmersion"),
        strong(if r.entries.isEmpty then (if r.measuring.value then "measuring…" else "unknown") else Backup.humanBytes(used))
      ),
      div(cls := "meter", styleAttr := s"--share:$pct%", div(cls := "meter-fill")),
      div(
        cls := "muted",
        if r.volumes.isEmpty then "" else s"$pct% of the free space on this disk · ${Backup.humanBytes(free)} free"
      )
    )
