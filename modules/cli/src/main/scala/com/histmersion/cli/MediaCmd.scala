package com.histmersion
package cli

import com.histmersion.api.media.MediaMarkdown

import cats.syntax.all.*
import com.monovore.decline.*
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

object MediaCmd:

  private def detail (using console: Console) = Opts.subcommand("detail", "Show asset metadata including spatial/temporal") {
    (Opts.argument[String]("citekey"), Opts.argument[String]("name"))
      .mapN { (ck, name) => (c: Client) =>
        c.assetDetail(Citekey(ck), Bundle.Asset.Variant(name)).map(d => Cli.out.println(MediaMarkdown.assetDetail(Citekey(ck), d)))
      }
  }

  private def patch (using console: Console) = Opts.subcommand("patch", "Update asset metadata") {
    (
      Opts.argument[String]("citekey"),
      Opts.argument[String]("name"),
      Opts.option[String]("kind", s"Asset kind: ${AssetKind.all.mkString("|")}").orNone,
      Opts.option[String]("caption", "Caption text").orNone,
      Opts.option[String]("license", "License identifier").orNone,
      Opts.option[Double]("lat", "Latitude").orNone,
      Opts.option[Double]("lon", "Longitude").orNone,
      Opts.option[Long]("period-start", "Period start (signed year)").orNone,
      Opts.option[Long]("period-end", "Period end (signed year)").orNone
    )
      .mapN { (ck, name, kind, caption, license, lat, lon, ps, pe) => (c: Client) =>
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
        )
          .map(d => Cli.out.println(MediaMarkdown.assetDetail(Citekey(ck), d)))
      }
  }

  // ── media from outside providers (#1446) ──

  /** `media import <provider>`: the same flags and the same job for every provider — the built-in ones and any a plugin
    * of the server registered (`media providers` lists them; the server decides).
    */
  private def importCmd (using console: Console) = Opts.subcommand(
    "import",
    "Bring in pictures from an outside provider (legally reusable only): wikidata (the default), depicts, met, " +
      "cleveland, aic, loc, pleiades, opencontext, smithsonian and europeana (off by default: a key of your own), or a " +
      "plugin's collection — `histmersion media providers` " +
      "lists them with their terms. --dry-run asks about one batch and prints the counts per licence (and a " +
      "collection's matches, with their reasons); otherwise a server JOB asks about every place (or event) not " +
      "asked yet, batch by batch, and resumes where it stopped"
  ) {
    (
      Opts.argument[String]("provider"),
      Opts
        .flag(
          "dry-run",
          "Read one batch, store nothing; print the counts per licence (and the matches, with their reasons)"
        )
        .orFalse,
      Opts
        .option[Int]("limit", "Targets to ask about (a dry run: default media.default-limit; a job: all not asked yet)")
        .orNone,
      Opts.option[String]("place", "Only this place").orNone,
      Opts.option[String]("event", "Only this event (its --wikidata item; providers that ask about events)").orNone,
      Opts.flag("refresh", "Ask again about ones already asked, and re-read the licences of stored pictures").orFalse,
      Opts.flag("no-wait", "Print the job id and return instead of following it").orFalse
    )
      .mapN { (name, dry, limit, place, event, refresh, noWait) => (c: Client) =>
        val req = MediaImportRequest(
          MediaItem.Provider(name),
          MediaImportRequest.DryRun(dry),
          limit.map(MediaImportRequest.Limit(_)),
          place.map(Place.Id(_)),
          event.map(Event.Id(_)),
          MediaImportRequest.Refresh(refresh)
        )
        if dry then c.mediaImport(req).map(r => Cli.out.println(MediaMarkdown.mediaImportReport(r)))
        else
          c.mediaImportJob(req).flatMap { j =>
            Cli.out.println(
              s"media import job `${j.id}` started: ${j.detail.flatMap(ImportJobKinds.Media.of).getOrElse("")} (`histmersion jobs` lists it)"
            )
            if noWait then Right(()) else IngestCmd.pollJob(c, j.id, 0)
          }
      }
  }

  /** `media providers`: what `media import` can run on this server — its plugins' registered collections. */
  private def providersCmd (using console: Console) = Opts.subcommand(
    "providers",
    "The providers `media import` can run on this server: the built-in ones and any a plugin registered, " +
      "with what each asks about, the plugin that provides it and the licences it may store"
  ) {
    Opts.unit.map(_ => (c: Client) => c.mediaProviders.map(ps => Cli.out.println(MediaMarkdown.mediaProviders(ps))))
  }

  private def listCmd (using console: Console) =
    Opts.subcommand("list", "Stored pictures — source figures and providers' — with licence and credit") {
      (
        Opts.option[String]("provider", s"${MediaProvider.all.mkString("|")}").orNone,
        Opts.option[String]("licence", "An SPDX id (CC-BY-SA-4.0), public-domain, or none").orNone,
        Opts.option[String]("place", "Linked to this place").orNone,
        Opts.option[String]("event", "Linked to this event").orNone,
        Opts.option[String]("q", "Text in the name, file or credit").orNone,
        Opts.option[Int]("limit", "How many to list").orNone,
        Opts.option[Int]("offset", "Skip this many (the list's footer names the next offset)").orNone,
        Opts.flag("facets", "Only the counts per provider and licence").orFalse
      )
        .mapN { (provider, licence, place, event, q, limit, offset, facets) => (c: Client) =>
          if facets then c.mediaFacets.map(f => Cli.out.println(MediaMarkdown.mediaFacets(f)))
          else
            c.mediaList(Api.MediaQuery(
                provider.map(Api.MediaQuery.Provider(_)),
                licence.map(Api.MediaQuery.License(_)),
                place.map(Api.MediaQuery.Place(_)),
                event.map(Api.MediaQuery.Event(_)),
                q.map(Api.MediaQuery.Q(_)),
                limit.map(Api.MediaQuery.Limit(_)),
                offset.map(Api.MediaQuery.Offset(_))
              ))
              .map(l => Cli.out.println(MediaMarkdown.mediaList(l)))
        }
    }

  private def showCmd (using console: Console) = Opts.subcommand("show", "One picture: origin, licence, credit, and what it shows") {
    Opts.argument[String]("uid").map { uid => (c: Client) =>
      c.mediaShow(Bundle.Asset.Uid(uid.stripPrefix("media:"))).map(m => Cli.out.println(MediaMarkdown.mediaItem(m)))
    }
  }

  private def fillCmd (using console: Console) = Opts.subcommand(
    "fill-licences",
    "Give a source's figures the licence the source records (only figures with none); counts unless --apply"
  ) {
    Opts.flag("apply", "Write the licences").orFalse.map { apply => (c: Client) =>
      c.fillFigureLicences(Api.Apply(apply)).map(f => Cli.out.println(MediaMarkdown.figureLicenceFill(f)))
    }
  }

  /** `histmersion media covers` — the sources' pictures: Calibre covers (read here, host-side), Open Library covers and
    * the papers' illustrations (the server).
    */
  private def coversCmd (using console: Console) = Opts.subcommand(
    "covers",
    "Pictures for the sources' hover cards: a book's cover from the local Calibre library (read-only), else " +
      "Open Library's by ISBN/OCLC; a paper's illustration chosen among its own figures. --dry-run says what " +
      "would be linked; otherwise the server part runs as a job"
  ) {
    (
      Opts.flag("dry-run", "Say what would be linked, store nothing").orFalse,
      Opts.option[Int]("limit", "Sources to look at per step").orNone,
      Opts.option[String]("citekey", "Only this source").orNone,
      Opts.flag("refresh", "Redo the sources that already have their cover / illustration").orFalse,
      Opts.flag("no-calibre", "Skip the Calibre library").orFalse,
      Opts.flag("no-open-library", "Do not ask Open Library").orFalse,
      Opts.flag("no-illustrations", "Covers only").orFalse,
      Opts.flag("no-wait", "Print the job id and return instead of following it").orFalse
    )
      .mapN { (dry, limit, ck, refresh, noCalibre, noOl, noIll, noWait) => (c: Client) =>
        val only = ck.map(_.trim).filter(_.nonEmpty)
        for
          // 1. the Calibre library, here on the host (the server may not see it)
          cal <-
            if noCalibre then Right((MediaCoversReport(RepairAcquire.RepairRequest.DryRun(dry)), Nil))
            else MediaCovers.calibreStep(
              c,
              Calibre(Settings.app.cli.calibre),
              only.map(Citekey(_)),
              MediaImportRequest.Refresh(refresh),
              RepairAcquire.RepairRequest.DryRun(dry),
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
                    dryRun = RepairAcquire.RepairRequest.DryRun(dry),
                    citekey = Some(k),
                    refresh = MediaImportRequest.Refresh(refresh),
                    illustrations = MediaCoversRequest.Illustrations(false),
                    isbn = Some(isbn)
                  )
                )
              )
          _ = if !noCalibre then
            Cli.out.println(MediaMarkdown.mediaCoversReport(olReports.foldLeft(calReport)(MediaCoversReport.combine)))
          // 3. the rest is the server's: Open Library by the records' ISBNs, and the illustrations
          req = MediaCoversRequest(
            RepairAcquire.RepairRequest.DryRun(dry),
            limit.map(MediaCoversRequest.Limit(_)),
            only.map(Citekey(_)),
            MediaImportRequest.Refresh(refresh),
            covers = MediaCoversRequest.Covers(!noOl),
            illustrations = MediaCoversRequest.Illustrations(!noIll)
          )
          _ <-
            if !req.covers.value && !req.illustrations.value then Right(())
            else if dry || only.isDefined then c.mediaCovers(req).map(r => Cli.out.println(MediaMarkdown.mediaCoversReport(r)))
            else
              c.mediaCoversJob(req).flatMap { j =>
                Cli.out.println(
                  s"media covers job `${j.id}` started: ${j.detail.flatMap(ImportJobKinds.Media.of).getOrElse("")} (`histmersion jobs` lists it)"
                )
                if noWait then Right(()) else IngestCmd.pollJob(c, j.id, 0)
              }
        yield ()
      }
  }

  def command(using console: Console) : Opts[Client => Either[Cli.Error, Unit]] =
    Opts.subcommand(
      "media",
      "Pictures: import from providers, covers and illustrations of sources, list, show, fill-licences; a figure's detail, patch"
    ) {
      importCmd orElse providersCmd orElse coversCmd orElse listCmd orElse showCmd orElse fillCmd orElse detail orElse patch
    }
