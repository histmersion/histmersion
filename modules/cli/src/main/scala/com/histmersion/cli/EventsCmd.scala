package com.histmersion
package cli
import com.histmersion.api.library.Filter
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.catalogue.EventsMarkdown

import com.histmersion.api.Api
import com.histmersion.config.*
import com.histmersion.api.catalogue.Historicity
import com.histmersion.api.catalogue.{Event, EventCorrection, EventSummary}
import com.histmersion.api.library.Citekey
import com.histmersion.api.personal.UnreadPage
import com.histmersion.api.catalogue.{
  Corrections,
  EventCandidatesRequest,
  EventGeometry,
  EventImportReport,
  EventImportRequest,
  EventLeads,
  EventLinksMarkdown,
  EventLinksRequest,
  EventPatch,
  EventRederiveReport,
  EventRederiveRequest,
  EventsApi,
  EventScale,
  EventTaxonomy
}
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.catalogue.Correction
import com.histmersion.util.console.Console
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.EventsJobKinds
import com.histmersion.util.problem.Problems

object EventsCmd:

  /** The filter flags are the shared ones: they select SOURCES, and an event is listed when its source is one of them;
    * `--from`/`--to` are matched against the event's own span, `--title` against its label. The positional query
    * searches the event's label and description.
    */

  /** The arguments of `events patch <id>`: the id and the patch they make (or why they make none). */
  /** The patch `events patch` flags spell, or why they spell none. Pure. */
  def patch(
      scale: Option[String],
      place: Option[String],
      lat: Option[Double],
      lon: Option[Double],
      geometry: Option[String],
      kind: Option[String],
      hist: Option[String],
      label: Option[String],
      parent: Option[String],
      desc: Option[String],
      ck: Option[String],
      wd: Option[String]
  ): Either[Problems, EventPatch] =
    for
      geom <- EventGeometry
        .of(
          geometry.map(EventGeometry.Geojson(_)),
          lat.map(Latitude(_)),
          lon.map(Longitude(_))
        )
      _ <- scale
        .filter(v => EventScale.parse(EventSummary.Scale(v)).isEmpty)
        .fold(Right(()))(v =>
          Left(
            Problem.Unclassified(Legacy.Text(s"--scale: '$v' is not local, regional or auto"))
          )
        )
      p = EventPatch(
        label = label.map(EventSummary.Label(_)),
        kind = kind.map(EventSummary.Kind(_)),
        historicity = hist.map(EventSummary.Historicity(_)),
        geometry = geom.map(g => EventPatch.Geometry(g.value)),
        placeId = place.map(Place.Id(_)),
        parentId = parent.map(Event.Id(_)),
        description = desc.map(EventSummary.Description(_)),
        citekey = ck.map(Citekey(_)),
        scale = scale.map(EventSummary.Scale(_)),
        wikidata = wd.map(EventPatch.Wikidata(_))
      )
      _ <- Either.cond(
        p != EventPatch(),
        (),
        Problem.Unclassified(Legacy.Text("nothing to change: give at least one field"))
      )
    yield p

  // ── catalogues, links and leads (#1449) ──

  /** Follow a job, then print what it left (an import's report). */
  private[cli] def follow(c: Client, j: JobInfo, following: Cli.Following)(using
      console: Console
  ): Either[Cli.Error, Unit] =
    Cli.out.println(
      s"job `${j.id}` started: ${j.detail.flatMap(EventsJobKinds.Catalogue.of).getOrElse(j.kind)} (`histmersion jobs` lists it)"
    )
    following.follow(
      IngestCmd.pollJob(c, j.id, 0).flatMap(_ => c.job(j.id)).map { done =>
        done.detail.flatMap(EventImportReport.Detail.of).foreach(r => Cli.out.println(EventImportReport.markdown(r)))
      }
    )

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    case Patch(id: String, patch: EventPatch)
    case Search(
        q: Option[String],
        kind: Option[String],
        hist: Option[String],
        place: Option[String],
        tier: Option[String],
        ctx: Boolean,
        f: Filter,
        json: Cli.JsonOutput
    )
    case Show(id: String, json: Cli.JsonOutput)
    case Import(
        p: String,
        dry: Boolean,
        limit: Option[Int],
        bbox: Option[String],
        from: Option[Long],
        to: Option[Long],
        vei: Option[Int],
        mag: Option[Double],
        refresh: Boolean,
        following: Cli.Following
    )
    case Rederive(p: Option[String], dry: Boolean, following: Cli.Following)
    case Corrections(st: Option[String], ev: Option[String], limit: Option[Int])
    case Catalogues()
    case Links(id: String, re: Boolean, confirm: List[String], reject: List[String])
    case Candidates(all: Boolean, ev: Option[String], limit: Option[Int], following: Cli.Following)
    case Leads(kind: Option[String], tier: Option[String], area: Option[String], f: Filter)

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.Search(q, kind, hist, place, tier, ctx, f, json) =>
          c.listEvents(
            Api.EventQuery(
              f,
              q.map(_.trim).filter(_.nonEmpty).map(Api.EventQuery.Text(_)),
              kind.map(Api.EventQuery.Kind(_)),
              hist.map(Api.EventQuery.Historicity(_)),
              place.map(Api.EventQuery.PlaceId(_)),
              tier.map(Api.EventQuery.Tier(_)),
              Option.when(ctx)(Api.EventQuery.Context(true))
            )
          ).map(r => Output.emit(json, r)(EventsMarkdown.eventList))
        case Command.Show(id, json) =>
          c.getEvent(Event.Uid(id)).map(d => Output.emit(json, d)(_.markdown.value))
        case Command.Import(p, dry, limit, bbox, from, to, vei, mag, refresh, following) =>
          c.call(EventsApi.importEvents)(
            EventImportRequest(
              EventImportRequest.Provider(p),
              EventImportRequest.DryRun(dry),
              limit.map(EventImportRequest.Limit(_)),
              bbox.map(EventImportRequest.Bbox(_)),
              from.map(EventImportRequest.From(_)),
              to.map(EventImportRequest.To(_)),
              vei.map(EventImportRequest.MinVei(_)),
              mag.map(EventImportRequest.MinMagnitude(_)),
              EventImportRequest.Refresh(refresh)
            )
          ).flatMap(follow(c, _, following))
        case Command.Rederive(p, dry, following) =>
          c.call(EventsApi.rederive)(
            EventRederiveRequest(p.map(EventRederiveRequest.Provider(_)), EventRederiveRequest.DryRun(dry))
          ).flatMap { j =>
            Cli.out.println(
              s"job `${j.id}` started: ${j.detail.flatMap(EventsJobKinds.Catalogue.of).getOrElse(j.kind)} (`histmersion jobs` lists it)"
            )
            following.follow(
              IngestCmd.pollJob(c, j.id, 0).flatMap(_ => c.job(j.id)).map { done =>
                done.detail
                  .flatMap(EventRederiveReport.Detail.of)
                  .foreach(r => Cli.out.println(EventRederiveReport.markdown(r)))
              }
            )
          }
        case Command.Corrections(st, ev, limit) =>
          c.call(EventsApi.corrections)(
            (st.map(Event.Id(_)), ev.map(EventCorrection.Status(_)), limit.map(UnreadPage.Offset(_)), None)
          ).map(l => Cli.out.println(Corrections.markdown(l)))
        case Command.Catalogues() =>
          c.call(EventsApi.catalogues)(())
            .map(cs =>
              Cli.out.println(
                cs.map(i =>
                  s"- **${i.id}** — ${i.title} (${i.home})\n  - licence: ${i.licence}\n  - cite as: ${i.citation}"
                ).mkString("\n")
              )
            )
        case Command.Links(id, re, confirm, reject) =>
          if re || confirm.nonEmpty || reject.nonEmpty then
            c.call(EventsApi.applyLinks)(
              (
                Event.Id(id),
                EventLinksRequest(
                  EventLinksRequest.Recompute(re),
                  confirm.map(EventLinksRequest.Confirm(_)),
                  reject.map(EventLinksRequest.Reject(_))
                )
              )
            ).map(r => Cli.out.println(EventLinksMarkdown.result(r)))
          else c.call(EventsApi.links)(Event.Id(id)).map(l => Cli.out.println(EventLinksMarkdown.list(l)))
        case Command.Candidates(all, ev, limit, following) =>
          c.call(EventsApi.candidatesJob)(
            EventCandidatesRequest(
              EventCandidatesRequest.All(all),
              ev.map(Event.Id(_)),
              limit.map(EventCandidatesRequest.Limit(_))
            )
          ).flatMap(follow(c, _, following))
        case Command.Leads(kind, tier, area, f) =>
          c.call(EventsApi.leads)(
            Api.EventQuery(
              f,
              kind = kind.map(Api.EventQuery.Kind(_)),
              tier = tier.map(Api.EventQuery.Tier(_)),
              area = area.map(Api.EventQuery.Area(_))
            )
          ).map(l => Cli.out.println(EventLeads.markdown(l)))
        case Command.Patch(id, p) => c.patchEvent(Event.Uid(id), p).map(d => Cli.out.println(d.markdown))
