package com.histmersion.cli

import com.histmersion.api.catalogue.PlacesMarkdown

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.Dataset
import com.histmersion.config.*

import java.nio.file.{Files, Path}
import com.histmersion.domain.catalogue.places.PlaceSeed
import com.histmersion.api.catalogue.{EventsApi, Lpf}
import com.histmersion.domain.catalogue.Place
import com.histmersion.util.data.exchange.Bundle
import com.histmersion.api.catalogue.SiteTypes

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
  private def cfg = Settings.app.places
  private def defaultBatch: Int = cfg.importBatch
  private def seedDir: String = cfg.seedDir

  private val asOfOpt =
    Opts.option[Long]("as-of", "Signed year the gazetteer is read at (BCE negative, e.g. -1400)").orNone

  private def show(rows: List[com.histmersion.api.catalogue.Place.Hit], asOf: Option[Long]): Unit =
    println(PlacesMarkdown.places(rows.toVector, asOf))

  private val search = Opts.subcommand("search", "Find places by name; the `id` it prints is what `--place` wants") {
    (Opts.argument[String]("query").orNone, asOfOpt, Opts.option[Int]("limit", "Max results").orNone)
      .mapN { (q, asOf, limit) => (c: Client) =>
        c.places(q.map(_.trim).filter(_.nonEmpty), asOf, limit).map(show(_, asOf))
      }
  }

  private val showCmd = Opts.subcommand("show", "One place: geometry, validity, parents and the subtree") {
    (
      Opts.argument[String]("id"),
      asOfOpt,
      Opts.option[Int]("depth", "How deep the subtree goes").orNone,
      Opts.flag("no-tree", "Skip the hierarchy walk").orFalse
    )
      .mapN { (id, asOf, depth, noTree) => (c: Client) =>
        for
          d <- c.place(id, asOf)
          tree <- if noTree then Right(Nil) else c.placeTree(id, asOf, depth)
        yield println(PlacesMarkdown.place(d, tree.toVector))
      }
  }

  private val children = Opts.subcommand("children", "What one place contains, as of a year") {
    (Opts.argument[String]("id"), asOfOpt).mapN { (id, asOf) => (c: Client) =>
      c.placeChildren(id, asOf).map { rows =>
        if rows.isEmpty then println("_No children._")
        else println(rows.map(x => s"- ${x.label} `${x.id}` _(${x.kind})_").mkString("\n"))
      }
    }
  }

  /** Read one LPF / GeoJSON file and upsert it, `batch` features per request. */
  private[cli] def importFile(
      c: Client,
      file: Path,
      kind: Option[String],
      batch: Int,
      dataset: Dataset = Dataset.Local
  ): Either[CliError, com.histmersion.api.catalogue.Place.ImportResult] =
    if !Files.isRegularFile(file) then Left(CliError(s"no such file: $file", exitCode = ExitCode.Usage))
    else
      Lpf.parse(Files.readString(file), kind.getOrElse(Place.Kind.Region)) match
        case Left(e)      => Left(CliError(s"$file: $e", exitCode = ExitCode.Usage))
        case Right(items) =>
          val groups = items.grouped(batch.max(1)).toList
          val total = groups.size
          groups.zipWithIndex
            .foldLeft[Either[CliError, com.histmersion.api.catalogue.Place.ImportResult]](
              Right(com.histmersion.api.catalogue.Place.ImportResult(0, 0, 0, Nil))
            ) { case (acc, (g, i)) =>
              acc.flatMap { sum =>
                if total > 1 then
                  Console.err.print(s"\r${file.getFileName}: batch ${i + 1}/$total (${sum.places} place(s))")
                c.importPlaces(kind, Lpf.render(g), dataset)
                  .map(r =>
                    com.histmersion.api.catalogue.Place.ImportResult(
                      sum.places + r.places,
                      sum.names + r.names,
                      sum.relations + r.relations,
                      (sum.failed ++ r.failed).take(20)
                    )
                  )
              }
            }
            .map { r =>
              if total > 1 then Console.err.print("\r" + " " * 72 + "\r")
              r
            }

  /** `--dataset local|seed`: whose the places are (V052) — local ones travel in a bundle. */
  private[cli] val datasetOpt: Opts[Dataset] =
    Opts
      .option[String](
        "dataset",
        "Whose they are: local (this collection's own — the default; carried in a bundle) or seed (reference data every instance imports)"
      )
      .mapValidated(s => Dataset.parse(s).toValidNel(s"--dataset $s: expected local or seed"))
      .withDefault(Dataset.Local)

  private val importCmd = Opts.subcommand("import", "Upsert a Linked Places / GeoJSON file into the gazetteer") {
    (
      Opts.argument[String]("file.json"),
      Opts
        .option[String]("kind", s"Fallback kind for features that declare none: ${Place.Kind.all.mkString("|")}")
        .orNone,
      Opts.option[Int]("batch", "Features per request").orNone,
      datasetOpt
    )
      .mapN { (file, kind, batch, dataset) => (c: Client) =>
        importFile(c, Path.of(file), kind, batch.getOrElse(defaultBatch), dataset)
          .map(r => println(PlacesMarkdown.placeImport(r)))
      }
  }

  /** The seed set in import order — [[PlaceSeed.files]], shared with the server's first-run import. */
  private[cli] def seedFiles(dir: Path): List[(Path, Option[String])] = PlaceSeed.files(entries(dir), entries)

  /** What a directory on the host holds, as [[PlaceSeed]] orders it. */
  private def entries(dir: Path): List[PlaceSeed.Entry[Path]] =
    val st = java.nio.file.Files.list(dir)
    try
      import scala.jdk.CollectionConverters.*
      st.iterator.asScala.toList
        .map(p => PlaceSeed.Entry(p.getFileName.toString, java.nio.file.Files.isDirectory(p), p))
        .filter(e => e.directory || java.nio.file.Files.isRegularFile(e.at))
    finally st.close()

  /** Where `places seed` reads: an absolute path as given; a relative one (the default `db/places`) under the working
    * directory when it is there (a checkout), else under the install this executable sits in (`<home>/bin/histmersion`,
    * through the `~/bin` symlink), whose bundle ships `db/places`. Returns the directory picked and every candidate
    * tried, for the error message.
    */
  def resolveSeedDir(dir: String, cwd: Path, exe: Option[Path]): (Path, List[Path]) =
    val p = Path.of(dir)
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
  private val seed =
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
          val (d, tried) = resolveSeedDir(dir.filter(_.nonEmpty).getOrElse(seedDir), Path.of("").toAbsolutePath, exe)
          val n = batch.getOrElse(defaultBatch)
          if !Files.isDirectory(d) then
            Left(
              CliError(
                s"not a directory: ${tried.mkString(", ")} — pass --dir <path to db/places>",
                exitCode = ExitCode.Usage
              )
            )
          else
            val files = seedFiles(d)
            if files.isEmpty then
              Left(CliError(s"no JSON files under $d — run db/places/fetch.sh first", exitCode = ExitCode.Usage))
            else
              def pass(
                  fs: List[(Path, Option[String])],
                  label: String
              ): Either[CliError, List[(Path, Option[String])]] =
                fs.foldLeft[Either[CliError, List[(Path, Option[String])]]](Right(Nil)) { case (acc, (f, kind)) =>
                  acc.flatMap { retry =>
                    importFile(c, f, kind, n, Dataset.Seed).map { r =>
                      println(s"$label${d.relativize(f)}: ${PlacesMarkdown.placeImport(r)}")
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
                  else c.rebuildPlaces.map(r => println(PlacesMarkdown.placeRebuild(r)))
              yield ()
        }
    }

  private val rebuild = Opts.subcommand("rebuild", "Re-materialise which findings fall in which places") {
    Opts.unit.map(_ => (c: Client) => c.rebuildPlaces.map(r => println(PlacesMarkdown.placeRebuild(r))))
  }

  private val noWait = Opts.flag("no-wait", "Print the job id and return instead of following it").orFalse

  /** A places job started on the server (#1946): say so, and follow it unless `noWait`. */
  private def started(c: Client, id: String, noWait: Boolean): Either[CliError, Unit] =
    println(s"job `$id` started (`histmersion jobs $id` follows it)")
    if noWait then Right(()) else IngestCmd.pollJob(c, id, 0)

  private val buildHierarchy = Opts.subcommand(
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
                println(
                  s"**${r.edgesCreated}** spatial containment edges created " +
                    s"(${r.polygonPlaces} polygon places, ${r.pointPlaces} point places)"
                )
              )
        }
    )
  }

  private val fetch = Opts.subcommand(
    "fetch",
    "Download and import an external gazetteer (cliopatria, awmc, chronontology) or the coastline (land). Re-runnable and idempotent."
  ) {
    (Opts.argument[String]("source"), Opts.flag("dry-run", "Parse and transform only — do not upsert").orFalse, noWait)
      .mapN { (source, dryRun, nw) => (c: Client) =>
        c.fetchExternalPlaces(source, dryRun).flatMap { r =>
          r.job match
            case Some(id) => started(c, id, nw)
            case None     => Right(println(s"${if dryRun then "[dry run] " else ""}${PlacesMarkdown.placeImport(r)}"))
        }
      }
  }

  // ── the external sources (#1064) ───────────────────────────────────────────────────────────────

  private val sources = Opts.subcommand(
    "sources",
    "The external gazetteer sources: the pinned GeoNames and Wikidata extracts, the LPF files in the drop folder, and what was imported"
  ) {
    Opts.unit.map(_ => (c: Client) => c.placeSources.map(rows => println(PlacesMarkdown.gazetteerSources(rows))))
  }

  private val load = Opts.subcommand(
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
        c.loadPlaces(source, apply).flatMap { j =>
          println(
            s"${if apply then "import" else "dry run"} of $source: job `${j.id}` started (`histmersion jobs` lists it)"
          )
          if noWait then Right(()) else IngestCmd.pollJob(c, j.id, 0)
        }
      }
  }

  // ── a person's edits (#987) ────────────────────────────────────────────────────────────────────

  private val langOpt =
    Opts.option[String]("lang", "Language tag of the name: en, grc, la, pl … (omit when unknown)").orNone

  private val nameAdd = Opts.subcommand(
    "add",
    "Give a place a further name (kept by every re-seed): `places name add pleiades_678430 'Lake Kinneret' --lang en`"
  ) {
    (Opts.argument[String]("place-id"), Opts.argument[String]("name"), langOpt).mapN { (id, name, lang) => (c: Client) =>
      c.addPlaceName(id, com.histmersion.api.catalogue.Place.NameAdd(name, lang))
        .map(r => println(PlacesMarkdown.placeNameChanged(r)))
    }
  }

  private val nameRemove =
    Opts.subcommand("rm", "Remove a name added by hand (a name the seed gives cannot be removed)") {
      (Opts.argument[String]("place-id"), Opts.argument[String]("name"), langOpt).mapN {
        (id, name, lang) => (c: Client) =>
          c.removePlaceName(id, com.histmersion.api.catalogue.Place.NameRemove(name, lang))
            .map(r => println(PlacesMarkdown.placeNameChanged(r)))
      }
    }

  private val nameList = Opts.subcommand(
    "list",
    "The names added by hand; --csv prints them as db/places/supplement/names.csv lines, to promote into the seed"
  ) {
    (
      Opts.option[String]("place", "Only this place's (an id)").orNone,
      Opts.flag("csv", "Print CSV (place_id,name,lang,why) instead of Markdown").orFalse
    )
      .mapN { (place, csv) => (c: Client) =>
        c.placeNames(place)
          .map(rows =>
            print(if csv then com.histmersion.api.catalogue.Place.Edits.csv(rows)
            else PlacesMarkdown.manualPlaceNames(rows) + "\n")
          )
      }
  }

  private val name = Opts.subcommand("name", "Names added by hand to gazetteer places: add, rm, list") {
    nameAdd orElse nameRemove orElse nameList
  }

  private val add = Opts.subcommand(
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
        .option[String]("id", s"The place id (default ${com.histmersion.api.catalogue.Place.Edits.LocalPrefix}<label>)")
        .orNone,
      Opts.options[String]("name", "A further name (repeatable)").orEmpty,
      langOpt,
      Opts.option[Long]("from", "Signed year the place begins (BCE negative)").orNone,
      Opts.option[Long]("to", "Signed year the place ends (BCE negative)").orNone
    )
      .mapN { (label, kind, lon, lat, source, id, names, lang, from, to) => (c: Client) =>
        c.addPlace(com.histmersion.api.catalogue.Place.Add(label, kind, lon, lat, source, id, names, lang, from, to))
          .map(p => println(PlacesMarkdown.placeAdded(p)))
      }
  }

  // ── a person's kind, period and point for a place (V080, V081) ───────────────────────────────────

  private val set = Opts.subcommand(
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
        .mapValidated(p => com.histmersion.api.catalogue.Place.Overrides.parsePoint(p).toValidatedNel)
        .orNone,
      Opts.option[String]("reason", "Why: the evidence the decision rests on").orNone,
      Opts.flag("reset", "Drop the override: the kind, period and point its source states come back").orFalse
    )
      .mapN { (id, kind, from, to, point, reason, reset) => (c: Client) =>
        c.setPlaceOverride(
          id,
          com.histmersion.api.catalogue.Place
            .OverrideSet(kind, from, to, reason, reset, point.map(_._1), point.map(_._2))
        ).map(r => println(PlacesMarkdown.placeOverrideChanged(r)))
      }
  }

  private val merge = Opts.subcommand(
    "merge",
    "Merge a place into the one it is the same as — findings, links, children and references move; its id resolves there, and no re-seed brings it back"
  ) {
    (
      Opts.argument[String]("place-id"),
      Opts.argument[String]("into-id"),
      Opts.option[String]("reason", "Why: the evidence the decision rests on").orNone
    )
      .mapN { (id, into, reason) => (c: Client) =>
        c.mergePlace(id, com.histmersion.api.catalogue.Place.MergeIn(into, reason))
          .map(r => println(PlacesMarkdown.placeGone(r)))
      }
  }

  private val rm = Opts.subcommand(
    "rm",
    "Remove a place nothing refers to (refused while an event, attestation, child, hand-added name or a finding with no other place does); no re-seed brings it back"
  ) {
    (Opts.argument[String]("place-id"), Opts.option[String]("reason", "Why: the evidence the decision rests on").orNone)
      .mapN { (id, reason) => (c: Client) =>
        c.removePlace(id, com.histmersion.api.catalogue.Place.RemoveIn(reason))
          .map(r => println(PlacesMarkdown.placeGone(r)))
      }
  }

  private val overrides = Opts.subcommand(
    "overrides",
    "The kinds, periods and points set by hand (`places set`), with what each source says"
  ) {
    Opts.option[String]("place", "Only this place's (an id)").orNone.map { place => (c: Client) =>
      c.placeOverrides(place).map(rows => println(PlacesMarkdown.placeOverrides(rows)))
    }
  }

  /** `places site-types` (#1449): refine what the Wikidata sites on the map are from their items. */
  private val siteTypes = Opts.subcommand(
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
        c.call(EventsApi.refineSiteTypes)(SiteTypes.Request(dry, limit, refresh))
          .flatMap(EventsCmd.follow(c, _, noWait))
      }
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand(
      "places",
      "The gazetteer: search, show, rename, name, set, merge, rm, add, import, load external sources and materialise places"
    ) {
      search orElse showCmd orElse children orElse siteTypes orElse LabelRenameCmd(
        Bundle.Kind.Place
      ) orElse name orElse set orElse overrides orElse merge orElse rm orElse add orElse importCmd orElse seed orElse fetch orElse sources orElse load orElse rebuild orElse buildHierarchy
    }
