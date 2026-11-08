package com.histmersion
package cli

import com.histmersion.api.Problem
import com.histmersion.api.exchange.Bundle

import com.histmersion.api.catalogue.PlacesMarkdown

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.{Api, Dataset, Paging}
import com.histmersion.config.*

import java.nio.file.{Files, Path}
import com.histmersion.domain.catalogue.places.PlaceSeed
import com.histmersion.api.catalogue.{EventsApi, Lpf}
import com.histmersion.api.catalogue.{Latitude, Longitude, Place, Year}
import com.histmersion.api.catalogue.SiteTypes
import com.histmersion.util.console.Console
import com.histmersion.api.SpatialHierarchyResult

/** `histmersion places …` — the gazetteer subcommands (plan §1.6a). Kept in its own file so the shared `Cli` only gains
  * one line.
  *
  * Import is done in BATCHES: the parse is pure and happens on the HOST ([[Lpf]] lives in `core`, and the CLI depends
  * on `core` only), and each batch is re-rendered as Linked Places Format and POSTed, so a 5 MB Pleiades extract never
  * becomes one 5 MB request body and the user sees progress.
  */
object PlacesCmd:

  /** Config-driven defaults (no literals in code, plan §1.9); read on demand so `--help` never needs a config file to
    * exist.
    */

  private def cfg(using console: Console) = Settings.app.places
  private def defaultBatch(using console: Console): Paging.Limit = Paging.Limit(cfg.importBatch.value)
  private def seedDir(using console: Console): PlacesConfig.SeedDir = cfg.seedDir

  private val asOfOpt =
    Opts
      .option[Long]("as-of", "Signed year the gazetteer is read at (BCE negative, e.g. -1400)")
      .orNone
      .map(_.map(Api.AggregateQuery.AsOf(_)))

  private def show(rows: List[Place.Hit], asOf: Option[Api.AggregateQuery.AsOf])(using console: Console): Unit =
    Cli.out.println(PlacesMarkdown.places(rows.toVector, asOf.map(a => Year(a.value))))

  private def search(using console: Console) =
    Opts.subcommand("search", "Find places by name; the `id` it prints is what `--place` wants") {
      (Opts.argument[String]("query").orNone, asOfOpt, Opts.option[Int]("limit", "Max results").orNone)
        .mapN { (q, asOf, limit) => (c: Client) =>
          c.places(q.map(_.trim).filter(_.nonEmpty).map(Api.Search.Text(_)), asOf, limit.map(Paging.Limit(_)))
            .map(show(_, asOf))
        }
    }

  private def showCmd(using console: Console) =
    Opts.subcommand("show", "One place: geometry, validity, parents and the subtree") {
      (
        Opts.argument[String]("id"),
        asOfOpt,
        Opts.option[Int]("depth", "How deep the subtree goes").orNone,
        Opts.flag("no-tree", "Skip the hierarchy walk").orFalse
      )
        .mapN { (id, asOf, depth, noTree) => (c: Client) =>
          for
            d <- c.place(Place.Id(id), asOf)
            tree <- if noTree then Right(Nil) else c.placeTree(Place.Id(id), asOf, depth.map(Place.Depth(_)))
          yield Cli.out.println(PlacesMarkdown.place(d, tree.toVector))
        }
    }

  private def children(using console: Console) = Opts.subcommand("children", "What one place contains, as of a year") {
    (Opts.argument[String]("id"), asOfOpt).mapN { (id, asOf) => (c: Client) =>
      c.placeChildren(Place.Id(id), asOf).map { rows =>
        if rows.isEmpty then Cli.out.println("_No children._")
        else Cli.out.println(rows.map(x => s"- ${x.label} `${x.id}` _(${x.kind})_").mkString("\n"))
      }
    }
  }

  /** Read one LPF / GeoJSON file and upsert it, `batch` features per request. */
  private[cli] def importFile(using
      console: Console
  )(
      c: Client,
      file: Path,
      kind: Option[Place.KindValue],
      batch: Paging.Limit,
      dataset: Dataset = Dataset.Local
  ): Either[Cli.Error, Place.ImportResult] =
    if !Files.isRegularFile(file) then
      Left(Cli.Error(Problem(s"no such file: $file"), exitCode = ExitCode.Usage))
    else
      Lpf.parse(Files.readString(file), kind.getOrElse(Place.KindValue(Place.Kind.Region))) match
        case Left(e)      => Left(Cli.Error(Problem(s"$file: $e"), exitCode = ExitCode.Usage))
        case Right(items) =>
          val groups = items.grouped(batch.value.max(1)).toList
          val total = groups.size
          groups.zipWithIndex
            .foldLeft[Either[Cli.Error, Place.ImportResult]](
              Right(
                Place.ImportResult(
                  Place.ImportResult.Places(0),
                  Place.ImportResult.Names(0),
                  Place.ImportResult.Relations(0),
                  Nil
                )
              )
            ) { case (acc, (g, i)) =>
              acc.flatMap { sum =>
                if total > 1 then
                  Cli.err.print(s"\r${file.getFileName}: batch ${i + 1}/$total (${sum.places} place(s))")
                c.importPlaces(
                  kind.map(k => Api.ExternalPlaces.Source(k.value)),
                  Api.ExternalPlaces.Format(Lpf.render(g).value),
                  dataset
                ).map(r =>
                  Place.ImportResult(
                    Place.ImportResult.Places(sum.places.value + r.places.value),
                    Place.ImportResult.Names(sum.names.value + r.names.value),
                    Place.ImportResult.Relations(sum.relations.value + r.relations.value),
                    (sum.failed ++ r.failed).take(20)
                  )
                )
              }
            }
            .map { r =>
              if total > 1 then Cli.err.print("\r" + " " * 72 + "\r")
              r
            }

  /** `--dataset local|seed`: whose the places are (V052) — local ones travel in a bundle. */
  private[cli] val datasetOpt: Opts[Dataset] =
    Opts
      .option[String](
        "dataset",
        "Whose they are: local (this collection's own — the default; carried in a bundle) or seed (reference data every instance imports)"
      )
      .mapValidated(s => Dataset.parse(Dataset.Wire(s)).toValidNel(s"--dataset $s: expected local or seed"))
      .withDefault(Dataset.Local)

  private def importCmd(using console: Console) =
    Opts.subcommand("import", "Upsert a Linked Places / GeoJSON file into the gazetteer") {
      (
        Opts.argument[String]("file.json"),
        Opts
          .option[String]("kind", s"Fallback kind for features that declare none: ${Place.Kind.all.mkString("|")}")
          .orNone,
        Opts.option[Int]("batch", "Features per request").orNone,
        datasetOpt
      )
        .mapN { (file, kind, batch, dataset) => (c: Client) =>
          importFile(
            c,
            Path.of(file),
            kind.map(Place.KindValue(_)),
            batch.map(Paging.Limit(_)).getOrElse(defaultBatch),
            dataset
          )
            .map(r => Cli.out.println(PlacesMarkdown.placeImport(r)))
        }
    }

  /** The seed set in import order — [[PlaceSeed.files]], shared with the server's first-run import. */
  private[cli] def seedFiles(dir: Path): List[(Path, Option[Place.KindValue])] =
    PlaceSeed.files(entries(dir), entries).map((f, k) => f -> k.map(w => Place.KindValue(w.value)))

  /** What a directory on the host holds, as [[PlaceSeed]] orders it. */
  private def entries(dir: Path): List[PlaceSeed.Entry[Path]] =
    val st = java.nio.file.Files.list(dir)
    try
      import scala.jdk.CollectionConverters.*
      st.iterator.asScala.toList
        .map(p =>
          PlaceSeed.Entry(
            PlaceSeed.Entry.Name(p.getFileName.toString),
            PlaceSeed.Entry.Directory(java.nio.file.Files.isDirectory(p)),
            p
          )
        )
        .filter(e => e.directory.value || java.nio.file.Files.isRegularFile(e.at))
    finally st.close()

  /** Where `places seed` reads: an absolute path as given; a relative one (the default `db/places`) under the working
    * directory when it is there (a checkout), else under the install this executable sits in (`<home>/bin/histmersion`,
    * through the `~/bin` symlink), whose bundle ships `db/places`. Returns the directory picked and every candidate
    * tried, for the error message.
    */
  def resolveSeedDir(dir: PlacesConfig.SeedDir, cwd: Path, exe: Option[Path]): (Path, List[Path]) =
    val p = Path.of(dir.value)
    if p.isAbsolute then (p, List(p))
    else
      val home = exe
        .flatMap(e => scala.util.Try(e.toRealPath()).toOption)
        .flatMap(e => Option(e.getParent))
        .flatMap(b => Option(b.getParent))
      val candidates = (cwd.resolve(p) :: home.map(_.resolve(p)).toList).map(_.normalize).distinct
      (candidates.find(Files.isDirectory(_)).getOrElse(candidates.head), candidates)

  /** The whole committed seed set (`db/places`), imported in [[seedFiles]] order.
    *
    * A second pass re-imports every file that reported a DANGLING PARENT: a polygon may legitimately name a parent that
    * only exists once a later file has loaded (the traced polities hang off the macro-regions, which hang off Natural
    * Earth's continents), and by the end of pass 1 every place exists. That is why nothing here depends on a clever
    * file-name ordering.
    */
  private def seed(using console: Console) =
    Opts.subcommand("seed", "Import every gazetteer file under db/places (Natural Earth, Pleiades, traced polities)") {
      (
        Opts.option[String]("dir", "Directory of LPF/GeoJSON files (default: config places.seedDir)").orNone,
        Opts.option[Int]("batch", "Features per request").orNone,
        Opts.flag("no-rebuild", "Do not re-materialise finding_places afterwards").orFalse
      )
        .mapN { (dir, batch, noRebuild) => (c: Client) =>
          val exe = scala.util
            .Try(ProcessHandle.current().info().command().orElse(null))
            .toOption
            .flatMap(Option(_))
            .map(Path.of(_))
          val (d, tried) = resolveSeedDir(
            dir.filter(_.nonEmpty).map(PlacesConfig.SeedDir(_)).getOrElse(seedDir),
            Path.of("").toAbsolutePath,
            exe
          )
          val n = batch.map(Paging.Limit(_)).getOrElse(defaultBatch)
          if !Files.isDirectory(d) then
            Left(
              Cli.Error(
                Problem(s"not a directory: ${tried.mkString(", ")} — pass --dir <path to db/places>"),
                exitCode = ExitCode.Usage
              )
            )
          else
            val files = seedFiles(d)
            if files.isEmpty then
              Left(
                Cli.Error(
                  Problem(s"no JSON files under $d — run db/places/fetch.sh first"),
                  exitCode = ExitCode.Usage
                )
              )
            else
              def pass(
                  fs: List[(Path, Option[Place.KindValue])],
                  label: String
              )(using console: Console): Either[Cli.Error, List[(Path, Option[Place.KindValue])]] =
                fs.foldLeft[Either[Cli.Error, List[(Path, Option[Place.KindValue])]]](Right(Nil)) {
                  case (acc, (f, kind)) =>
                    acc.flatMap { retry =>
                      importFile(c, f, kind, n, Dataset.Seed).map { r =>
                        Cli.out.println(s"$label${d.relativize(f)}: ${PlacesMarkdown.placeImport(r)}")
                        if r.failed.isEmpty then retry else retry :+ (f -> kind)
                      }
                    }
                }
              for
                retry <- pass(files, "")
                _ <-
                  if retry.isEmpty then Right(Nil)
                  else pass(retry, "re-resolving parents: ")
                _ <-
                  if noRebuild then Right(())
                  else c.rebuildPlaces.map(r => Cli.out.println(PlacesMarkdown.placeRebuild(r)))
              yield ()
        }
    }

  private def rebuild(using console: Console) =
    Opts.subcommand("rebuild", "Re-materialise which findings fall in which places") {
      Opts.unit.map(_ => (c: Client) => c.rebuildPlaces.map(r => Cli.out.println(PlacesMarkdown.placeRebuild(r))))
    }

  private val noWait = Opts.flag("no-wait", "Print the job id and return instead of following it").orFalse

  /** A places job started on the server (#1946): say so, and follow it unless `noWait`. */
  private def started(c: Client, id: SpatialHierarchyResult.Job.Id, noWait: Boolean)(using
      console: Console
  ): Either[Cli.Error, Unit] =
    Cli.out.println(s"job `$id` started (`histmersion jobs $id` follows it)")
    if noWait then Right(()) else IngestCmd.pollJob(c, id, 0)

  private def buildHierarchy(using console: Console) = Opts.subcommand(
    "build-hierarchy",
    "Compute parent-child edges from spatial containment (point inside polygon). Idempotent."
  ) {
    noWait.map(nw =>
      (c: Client) =>
        c.buildHierarchy.flatMap { r =>
          r.job match
            // a server that runs it as a job (#1946) answers the job; the counts are its summary
            case Some(id) => started(c, id, nw)
            case None     =>
              Right(
                Cli.out.println(
                  s"**${r.edgesCreated}** spatial containment edges created " +
                    s"(${r.polygonPlaces} polygon places, ${r.pointPlaces} point places)"
                )
              )
        }
    )
  }

  private def fetch(using console: Console) = Opts.subcommand(
    "fetch",
    "Download and import an external gazetteer (cliopatria, awmc, chronontology) or the coastline (land). Re-runnable and idempotent."
  ) {
    (Opts.argument[String]("source"), Opts.flag("dry-run", "Parse and transform only — do not upsert").orFalse, noWait)
      .mapN { (source, dryRun, nw) => (c: Client) =>
        c.fetchExternalPlaces(Api.ExternalPlaces.Source(source), Cli.DryRun(dryRun)).flatMap { r =>
          r.job match
            case Some(id) => started(c, SpatialHierarchyResult.Job.Id(id.value), nw)
            case None     =>
              Right(Cli.out.println(s"${if dryRun then "[dry run] " else ""}${PlacesMarkdown.placeImport(r)}"))
        }
      }
  }

  // ── the external sources (#1064) ───────────────────────────────────────────────────────────────

  private def sources(using console: Console) = Opts.subcommand(
    "sources",
    "The external gazetteer sources: the pinned GeoNames and Wikidata extracts, the LPF files in the drop folder, and what was imported"
  ) {
    Opts.unit.map(_ =>
      (c: Client) => c.placeSources.map(rows => Cli.out.println(PlacesMarkdown.gazetteerSources(rows)))
    )
  }

  private def load(using console: Console) = Opts.subcommand(
    "load",
    "Import an external source — geonames, wikidata, tgn (Getty) or lpf (the drop folder) — joined to the gazetteer by shared ids; a dry run unless --apply"
  ) {
    (
      Opts.argument[String]("geonames|wikidata|tgn|lpf"),
      Opts
        .flag("apply", "Import for real (without it: a dry run that reports what would change and keeps nothing)")
        .orFalse,
      Opts.flag("no-wait", "Print the job id and return instead of following it").orFalse
    )
      .mapN { (source, apply, noWait) => (c: Client) =>
        c.loadPlaces(Api.ExternalPlaces.Source(source), Api.Apply(apply)).flatMap { j =>
          Cli.out.println(
            s"${if apply then "import" else "dry run"} of $source: job `${j.id}` started (`histmersion jobs` lists it)"
          )
          if noWait then Right(()) else IngestCmd.pollJob(c, j.id, 0)
        }
      }
  }

  // ── a person's edits (#987) ────────────────────────────────────────────────────────────────────

  private val langOpt =
    Opts.option[String]("lang", "Language tag of the name: en, grc, la, pl … (omit when unknown)").orNone

  private def nameAdd(using console: Console) = Opts.subcommand(
    "add",
    "Give a place a further name (kept by every re-seed): `places name add pleiades_678430 'Lake Kinneret' --lang en`"
  ) {
    (Opts.argument[String]("place-id"), Opts.argument[String]("name"), langOpt).mapN { (id, name, lang) => (c: Client) =>
      c.addPlaceName(Place.Id(id), Place.NameAdd(Place.NameAdd.Name(name), lang.map(Place.NameAdd.Lang(_))))
        .map(r => Cli.out.println(PlacesMarkdown.placeNameChanged(r)))
    }
  }

  private def nameRemove(using console: Console) =
    Opts.subcommand("rm", "Remove a name added by hand (a name the seed gives cannot be removed)") {
      (Opts.argument[String]("place-id"), Opts.argument[String]("name"), langOpt).mapN {
        (id, name, lang) => (c: Client) =>
          c.removePlaceName(
            Place.Id(id),
            Place.NameRemove(Place.NameRemove.Name(name), lang.map(Place.NameRemove.Lang(_)))
          ).map(r => Cli.out.println(PlacesMarkdown.placeNameChanged(r)))
      }
    }

  private def nameList(using console: Console) = Opts.subcommand(
    "list",
    "The names added by hand; --csv prints them as db/places/supplement/names.csv lines, to promote into the seed"
  ) {
    (
      Opts.option[String]("place", "Only this place's (an id)").orNone,
      Opts.flag("csv", "Print CSV (place_id,name,lang,why) instead of Markdown").orFalse
    )
      .mapN { (place, csv) => (c: Client) =>
        c.placeNames(place.map(Place.Id(_)))
          .map(rows =>
            Cli.out.print(if csv then Place.Edits.csv(rows)
            else PlacesMarkdown.manualPlaceNames(rows) + "\n")
          )
      }
  }

  private def name(using console: Console) =
    Opts.subcommand("name", "Names added by hand to gazetteer places: add, rm, list") {
      nameAdd orElse nameRemove orElse nameList
    }

  private def add(using console: Console) = Opts.subcommand(
    "add",
    "Add a point place of this collection's own (never touched by a re-seed); --source says where the point comes from"
  ) {
    (
      Opts.argument[String]("label"),
      Opts.option[String]("kind", s"One of ${Place.Kind.all.mkString(", ")}").withDefault(Place.Kind.Site),
      Opts.option[Double]("lon", "Longitude, decimal degrees (east positive)"),
      Opts.option[Double]("lat", "Latitude, decimal degrees (north positive)"),
      Opts.option[String](
        "source",
        "Where the coordinates come from: a gazetteer or Wikidata id, a map, a publication"
      ),
      Opts
        .option[String]("id", s"The place id (default ${Place.Edits.LocalPrefix}<label>)")
        .orNone,
      Opts.options[String]("name", "A further name (repeatable)").orEmpty,
      langOpt,
      Opts.option[Long]("from", "Signed year the place begins (BCE negative)").orNone,
      Opts.option[Long]("to", "Signed year the place ends (BCE negative)").orNone
    )
      .mapN { (label, kind, lon, lat, source, id, names, lang, from, to) => (c: Client) =>
        c.addPlace(
          Place.Add(
            Place.Add.Label(label),
            Place.Add.Kind(kind),
            Longitude(lon),
            Latitude(lat),
            Place.Add.Source(source),
            id.map(Place.Id(_)),
            names.map(Place.Add.Names(_)),
            lang.map(Place.Add.Lang(_)),
            from.map(Year(_)),
            to.map(Year(_))
          )
        ).map(p => Cli.out.println(PlacesMarkdown.placeAdded(p)))
      }
  }

  // ── a person's kind, period and point for a place (V080, V081) ───────────────────────────────────

  private def set(using console: Console) = Opts.subcommand(
    "set",
    "Set what a place IS over what its source says — kind, period and/or point; kept by every re-seed, its findings matched again: " +
      "`places set pleiades_886355306 --kind region --reason 'one of the 14 Augustan regiones'`"
  ) {
    (
      Opts.argument[String]("place-id"),
      Opts.option[String]("kind", s"The kind it is: ${Place.Kind.all.mkString("|")}").orNone,
      Opts
        .option[Long]("from", "Signed year it begins (BCE negative); the end stays as set (open the first time)")
        .orNone,
      Opts
        .option[Long]("to", "Signed year it ends (BCE negative); the start stays as set (open the first time)")
        .orNone,
      Opts
        .option[String](
          "point",
          "Its corrected point, latitude,longitude (a place known by a point only; --reason must name the gazetteer value)"
        )
        .mapValidated(p => Place.Overrides.parsePoint(Place.Overrides.PointText(p)).left.map(_.value).toValidatedNel)
        .orNone,
      Opts.option[String]("reason", "Why: the evidence the decision rests on").orNone,
      Opts.flag("reset", "Drop the override: the kind, period and point its source states come back").orFalse
    )
      .mapN { (id, kind, from, to, point, reason, reset) => (c: Client) =>
        c.setPlaceOverride(
          Place.Id(id),
          Place
            .OverrideSet(
              kind.map(Place.OverrideSet.Kind(_)),
              from.map(Year(_)),
              to.map(Year(_)),
              reason.map(api.Problem(_)),
              Place.OverrideSet.Reset(reset),
              point.map(_._1),
              point.map(_._2)
            )
        ).map(r => Cli.out.println(PlacesMarkdown.placeOverrideChanged(r)))
      }
  }

  private def merge(using console: Console) = Opts.subcommand(
    "merge",
    "Merge a place into the one it is the same as — findings, links, children and references move; its id resolves there, and no re-seed brings it back"
  ) {
    (
      Opts.argument[String]("place-id"),
      Opts.argument[String]("into-id"),
      Opts.option[String]("reason", "Why: the evidence the decision rests on").orNone
    )
      .mapN { (id, into, reason) => (c: Client) =>
        c.mergePlace(Place.Id(id), Place.MergeIn(Place.Id(into), reason.map(api.Problem(_))))
          .map(r => Cli.out.println(PlacesMarkdown.placeGone(r)))
      }
  }

  private def rm(using console: Console) = Opts.subcommand(
    "rm",
    "Remove a place nothing refers to (refused while an event, attestation, child, hand-added name or a finding with no other place does); no re-seed brings it back"
  ) {
    (Opts.argument[String]("place-id"), Opts.option[String]("reason", "Why: the evidence the decision rests on").orNone)
      .mapN { (id, reason) => (c: Client) =>
        c.removePlace(Place.Id(id), Place.RemoveIn(reason.map(api.Problem(_))))
          .map(r => Cli.out.println(PlacesMarkdown.placeGone(r)))
      }
  }

  private def overrides(using console: Console) = Opts.subcommand(
    "overrides",
    "The kinds, periods and points set by hand (`places set`), with what each source says"
  ) {
    Opts.option[String]("place", "Only this place's (an id)").orNone.map { place => (c: Client) =>
      c.placeOverrides(place.map(Place.Id(_))).map(rows => Cli.out.println(PlacesMarkdown.placeOverrides(rows)))
    }
  }

  /** `places site-types` (#1449): refine what the Wikidata sites on the map are from their items. */
  private def siteTypes(using console: Console) = Opts.subcommand(
    "site-types",
    "Refine the type (settlement, fortification, burial…) of the Wikidata sites on the map from their items' " +
      "classes, as a server job; Pleiades and GeoNames sites are typed from their own fields already"
  ) {
    (
      Opts.flag("dry-run", "Count the sites it would ask about").orFalse,
      Opts.option[Int]("limit", "Sites at most").orNone,
      Opts.flag("refresh", "Ask again about sites asked before").orFalse,
      Opts.flag("no-wait", "Print the job id and return").orFalse
    )
      .mapN { (dry, limit, refresh, noWait) => (c: Client) =>
        c.call(EventsApi.refineSiteTypes)(
          SiteTypes.Request(
            SiteTypes.Request.DryRun(dry),
            limit.map(SiteTypes.Request.Limit(_)),
            SiteTypes.Request.Refresh(refresh)
          )
        ).flatMap(EventsCmd.follow(c, _, Cli.NoWait(noWait)))
      }
  }

  def command(using console: Console): Opts[Client => Either[Cli.Error, Unit]] =
    Opts.subcommand(
      "places",
      "The gazetteer: search, show, rename, name, set, merge, rm, add, import, load external sources and materialise places"
    ) {
      search orElse showCmd orElse children orElse siteTypes orElse LabelRenameCmd(
        Bundle.Kind.Place
      ) orElse name orElse set orElse overrides orElse merge orElse rm orElse add orElse importCmd orElse seed orElse fetch orElse sources orElse load orElse rebuild orElse buildHierarchy
    }
