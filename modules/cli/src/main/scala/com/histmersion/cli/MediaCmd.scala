package com.histmersion
package cli

import com.histmersion.api.library.repairacquire.RepairRequest
import com.histmersion.cli.AllOrFirst.traverse

import com.histmersion.api.media.MediaMarkdown

import com.histmersion.api.Api
import com.histmersion.config.*
import com.histmersion.api.media.{
  AssetKind,
  AssetPatch,
  MediaCoversReport,
  MediaCoversRequest,
  MediaImportRequest,
  MediaItem,
  MediaProvider
}
import com.histmersion.api.catalogue.{Event, Latitude, Longitude, Place, Year}
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.library.{Citekey, RepairAcquire}
import com.histmersion.util.console.Console
import com.histmersion.api.media.ImportJobKinds
import com.histmersion.api.Paging
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

@atBoundary(BoundaryKind.CliArgs, "parses `histmersion media` arguments")
object MediaCmd:

  // ── media from outside providers (#1446) ──

  /** `media import <provider>`: the same flags and the same job for every provider — the built-in ones and any a plugin
    * of the server registered (`media providers` lists them; the server decides).
    */

  /** `media providers`: what `media import` can run on this server — its plugins' registered collections. */

  /** `histmersion media covers` — the sources' pictures: Calibre covers (read here, host-side), Open Library covers and
    * the papers' illustrations (the server).
    */

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case Import(
        name: String,
        writes: Cli.Writes,
        limit: Option[Int],
        place: Option[String],
        event: Option[String],
        refresh: Boolean,
        following: Cli.Following
    )
    case Providers()
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case Covers(
        writes: Cli.Writes,
        limit: Option[Int],
        ck: Option[String],
        refresh: Boolean,
        noCalibre: Boolean,
        noOl: Boolean,
        noIll: Boolean,
        following: Cli.Following
    )
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case ListCmd(
        provider: Option[String],
        licence: Option[String],
        place: Option[String],
        event: Option[String],
        q: Option[String],
        limit: Option[Int],
        offset: Option[Int],
        facets: Boolean
    )
    case Show(uid: String)
    case FillLicences(writes: Cli.Writes)
    case Detail(ck: String, name: String)
    case Patch(
        ck: String,
        name: String,
        kind: Option[String],
        caption: Option[String],
        license: Option[String],
        lat: Option[Double],
        lon: Option[Double],
        ps: Option[Long],
        pe: Option[Long]
    )

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.Import(name, writes, limit, place, event, refresh, following) =>
          val dry = writes.dry
          val req = MediaImportRequest(
            MediaItem.Provider(name),
            (if dry then MediaImportRequest.DryRun.DryRun else MediaImportRequest.DryRun.Apply),
            limit.map(MediaImportRequest.Limit(_)),
            place.map(Place.Id(_)),
            event.map(Event.Id(_)),
            (if refresh then MediaImportRequest.Refresh.Refresh else MediaImportRequest.Refresh.Reuse)
          )
          if dry then c.mediaImport(req).map(r => Cli.out.println(MediaMarkdown.mediaImportReport(r)))
          else
            c.mediaImportJob(req).flatMap { j =>
              Cli.out.println(
                s"media import job `${j.id}` started: ${j.detail.flatMap(ImportJobKinds.Media.of).getOrElse("")} (`histmersion jobs` lists it)"
              )
              following.follow(IngestCmd.pollJob(c, j.id, 0))
            }
        case Command.Providers() =>
          c.mediaProviders.map(ps => Cli.out.println(MediaMarkdown.mediaProviders(ps)))
        case Command.Covers(writes, limit, ck, refresh, noCalibre, noOl, noIll, following) =>
          val dry = writes.dry
          val only = ck.map(_.trim).filter(_.nonEmpty)
          for
            // 1. the Calibre library, here on the host (the server may not see it)
            cal <-
              if noCalibre then
                Right(
                  (
                    MediaCoversReport((if dry then RepairRequest.DryRun.DryRun
                                       else RepairRequest.DryRun.Apply)),
                    Nil
                  )
                )
              else
                MediaCovers.calibreStep(
                  c,
                  Calibre(Settings.app.cli.calibre),
                  only.map(Citekey(_)),
                  (if refresh then MediaImportRequest.Refresh.Refresh else MediaImportRequest.Refresh.Reuse),
                  (if dry then RepairRequest.DryRun.DryRun else RepairRequest.DryRun.Apply),
                  limit.map(Paging.Limit(_))
                )
            (calReport, asks) = cal
            // 2. Calibre knew an ISBN but kept no cover: Open Library, by that ISBN, one source at a time
            olReports <-
              if noOl then Right(Nil)
              else
                asks.traverse((k, isbn) =>
                  c.mediaCovers(
                    MediaCoversRequest(
                      dryRun = (if dry then RepairRequest.DryRun.DryRun
                                else RepairRequest.DryRun.Apply),
                      citekey = Some(k),
                      refresh =
                        (if refresh then MediaImportRequest.Refresh.Refresh else MediaImportRequest.Refresh.Reuse),
                      illustrations = MediaCoversRequest.Illustrations.Skipped,
                      isbn = Some(isbn)
                    )
                  )
                )
            _ = if !noCalibre then
              Cli.out.println(MediaMarkdown.mediaCoversReport(olReports.foldLeft(calReport)(MediaCoversReport.combine)))
            // 3. the rest is the server's: Open Library by the records' ISBNs, and the illustrations
            req = MediaCoversRequest(
              (if dry then RepairRequest.DryRun.DryRun else RepairRequest.DryRun.Apply),
              limit.map(MediaCoversRequest.Limit(_)),
              only.map(Citekey(_)),
              (if refresh then MediaImportRequest.Refresh.Refresh else MediaImportRequest.Refresh.Reuse),
              covers = (if !noOl then MediaCoversRequest.Covers.Included else MediaCoversRequest.Covers.Skipped),
              illustrations =
                (if !noIll then MediaCoversRequest.Illustrations.Included else MediaCoversRequest.Illustrations.Skipped)
            )
            _ <-
              if !req.covers.included && !req.illustrations.included then Right(())
              else if dry || only.isDefined then
                c.mediaCovers(req).map(r => Cli.out.println(MediaMarkdown.mediaCoversReport(r)))
              else
                c.mediaCoversJob(req).flatMap { j =>
                  Cli.out.println(
                    s"media covers job `${j.id}` started: ${j.detail.flatMap(ImportJobKinds.Media.of).getOrElse("")} (`histmersion jobs` lists it)"
                  )
                  following.follow(IngestCmd.pollJob(c, j.id, 0))
                }
          yield ()
        case Command.ListCmd(provider, licence, place, event, q, limit, offset, facets) =>
          if facets then c.mediaFacets.map(f => Cli.out.println(MediaMarkdown.mediaFacets(f)))
          else
            c.mediaList(
              Api.MediaQuery(
                provider.map(Api.MediaQuery.Provider(_)),
                licence.map(Api.MediaQuery.License(_)),
                place.map(Api.MediaQuery.Place(_)),
                event.map(Api.MediaQuery.Event(_)),
                q.map(Api.MediaQuery.Q(_)),
                limit.map(Api.MediaQuery.Limit(_)),
                offset.map(Api.MediaQuery.Offset(_))
              )
            ).map(l => Cli.out.println(MediaMarkdown.mediaList(l)))
        case Command.Show(uid) =>
          c.mediaShow(Bundle.Asset.Uid(uid.stripPrefix("media:"))).map(m => Cli.out.println(MediaMarkdown.mediaItem(m)))
        case Command.FillLicences(writes) =>
          c.fillFigureLicences((if writes.applies then Api.Apply.Apply else Api.Apply.DryRun))
            .map(f => Cli.out.println(MediaMarkdown.figureLicenceFill(f)))
        case Command.Detail(ck, name) =>
          c.assetDetail(Citekey(ck), Bundle.Asset.Variant(name))
            .map(d => Cli.out.println(MediaMarkdown.assetDetail(Citekey(ck), d)))
        case Command.Patch(ck, name, kind, caption, license, lat, lon, ps, pe) =>
          c.patchAsset(
            Citekey(ck),
            Bundle.Asset.Variant(name),
            AssetPatch(
              kind.map(MediaItem.Kind(_)),
              caption.map(MediaItem.Caption(_)),
              license.map(MediaItem.License(_)),
              lat.map(Latitude(_)),
              lon.map(Longitude(_)),
              ps.map(Year(_)),
              pe.map(Year(_))
            )
          ).map(d => Cli.out.println(MediaMarkdown.assetDetail(Citekey(ck), d)))
