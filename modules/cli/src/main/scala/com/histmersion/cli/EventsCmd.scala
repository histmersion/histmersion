package com.histmersion
package cli
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
  ): Either[Cli.Error, EventPatch] =
    for
      geom <- EventGeometry
        .of(
          geometry.map(EventGeometry.Geojson(_)),
          lat.map(Latitude(_)),
          lon.map(Longitude(_))
        )
        .left
        .map(m => Cli.Error(m, exitCode = ExitCode.Usage))
      _ <- scale
        .filter(v => EventScale.parse(EventSummary.Scale(v)).isEmpty)
        .fold(Right(()))(v =>
          Left(
            Cli.Error(
              Problem.Unclassified(Legacy.Text(s"--scale: '$v' is not local, regional or auto")),
              exitCode = ExitCode.Usage
            )
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
        Cli.Error(
          Problem.Unclassified(Legacy.Text("nothing to change: give at least one field")),
          exitCode = ExitCode.Usage
        )
      )
    yield p

  // ── catalogues, links and leads (#1449) ──

  /** Follow a job, then print what it left (an import's report). */
  private[cli] def follow(c: Client, j: JobInfo, noWait: Cli.NoWait)(using console: Console): Either[Cli.Error, Unit] =
    Cli.out.println(
      s"job `${j.id}` started: ${j.detail.flatMap(EventsJobKinds.Catalogue.of).getOrElse(j.kind)} (`histmersion jobs` lists it)"
    )
    if noWait.value then Right(())
    else
      IngestCmd.pollJob(c, j.id, 0).flatMap(_ => c.job(j.id)).map { done =>
        done.detail.flatMap(EventImportReport.Detail.of).foreach(r => Cli.out.println(EventImportReport.markdown(r)))
      }

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
        f: com.histmersion.api.library.Filter,
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
        noWait: Boolean
    )
    case Rederive(p: Option[String], dry: Boolean, noWait: Boolean)
    case Corrections(st: Option[String], ev: Option[String], limit: Option[Int])
    case Catalogues()
    case Links(id: String, re: Boolean, confirm: List[String], reject: List[String])
    case Candidates(all: Boolean, ev: Option[String], limit: Option[Int], noWait: Boolean)
    case Leads(kind: Option[String], tier: Option[String], area: Option[String], f: com.histmersion.api.library.Filter)

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = EventsCmd.run(c, this)

  /** Carry out one command against the server. */
  def run(c: Client, cmd: Command)(using console: Console): Either[Cli.Error, Unit] =
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
      case Command.Import(p, dry, limit, bbox, from, to, vei, mag, refresh, noWait) =>
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
        ).flatMap(follow(c, _, Cli.NoWait(noWait)))
      case Command.Rederive(p, dry, noWait) =>
        c.call(EventsApi.rederive)(
          EventRederiveRequest(p.map(EventRederiveRequest.Provider(_)), EventRederiveRequest.DryRun(dry))
        ).flatMap { j =>
          Cli.out.println(
            s"job `${j.id}` started: ${j.detail.flatMap(EventsJobKinds.Catalogue.of).getOrElse(j.kind)} (`histmersion jobs` lists it)"
          )
          if noWait then Right(())
          else
            IngestCmd.pollJob(c, j.id, 0).flatMap(_ => c.job(j.id)).map { done =>
              done.detail
                .flatMap(EventRederiveReport.Detail.of)
                .foreach(r => Cli.out.println(EventRederiveReport.markdown(r)))
            }
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
      case Command.Candidates(all, ev, limit, noWait) =>
        c.call(EventsApi.candidatesJob)(
          EventCandidatesRequest(
            EventCandidatesRequest.All(all),
            ev.map(Event.Id(_)),
            limit.map(EventCandidatesRequest.Limit(_))
          )
        ).flatMap(follow(c, _, Cli.NoWait(noWait)))
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
