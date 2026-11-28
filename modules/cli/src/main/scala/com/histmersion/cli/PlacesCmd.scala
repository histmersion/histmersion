package com.histmersion
package cli
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.catalogue.PlacesMarkdown

import com.histmersion.api.{Api, Dataset, Paging}
import com.histmersion.config.*

import java.nio.file.{Files, Path}
import com.histmersion.domain.catalogue.places.PlaceSeed
import com.histmersion.api.catalogue.{EventsApi, Lpf}
import com.histmersion.api.catalogue.{Latitude, Longitude, Place, Year}
import com.histmersion.api.catalogue.SiteTypes
import com.histmersion.util.console.Console
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** `histmersion places …` — the gazetteer subcommands (plan §1.6a). Kept in its own file so the shared `Cli` only gains
  * one line.
  *
  * Import is done in BATCHES: the parse is pure and happens on the HOST ([[Lpf]] lives in `core`, and the CLI depends
  * on `core` only), and each batch is re-rendered as Linked Places Format and POSTed, so a 5 MB Pleiades extract never
  * becomes one 5 MB request body and the user sees progress.
  */
@atBoundary(BoundaryKind.CliArgs, "parses `histmersion places` arguments")
object PlacesCmd:

  /** Config-driven defaults (no literals in code, plan §1.9); read on demand so `--help` never needs a config file to
    * exist.
    */

  private def cfg(using console: Console) = Settings.app.places
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def defaultBatch(using console: Console): Paging.Limit = Paging.Limit(cfg.importBatch.value)
  private def seedDir(using console: Console): PlacesConfig.SeedDir = cfg.seedDir

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def show(rows: List[Place.Hit], asOf: Option[Api.AggregateQuery.AsOf])(using console: Console): Unit =
    Cli.out.println(PlacesMarkdown.places(rows.toVector, asOf.map(a => a)))

  /** Read one LPF / GeoJSON file and upsert it, `batch` features per request. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private[cli] def importFile(using
      console: Console
  )(
      c: Client,
      file: Path,
      kind: Option[Place.KindValue],
      batch: Paging.Limit,
      dataset: Dataset = Dataset.Local
  ): Either[Cli.Error, Place.ImportResult] =
    if !Files.isRegularFile(file) then Left(Cli.Error(Problem.Unclassified(Legacy.Text(s"no such file: $file"))))
    else
      Lpf.parse(Files.readString(file), kind.getOrElse(Place.KindValue(Place.Kind.Region))) match
        case Left(e) =>
          Left(Cli.Error(Problem.Unclassified(Legacy.Text(s"$file: ${e.sentence}"))))
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

  /** The seed set in import order — [[PlaceSeed.files]], shared with the server's first-run import. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private[cli] def seedFiles(dir: Path): List[(Path, Option[Place.KindValue])] =
    PlaceSeed.files(entries(dir), entries).map((f, k) => f -> k.map(w => Place.KindValue(w.value)))

  /** What a directory on the host holds, as [[PlaceSeed]] orders it. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
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

  /** A places job started on the server (#1946): say so, and follow it unless told to return. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def started(c: Client, id: SpatialHierarchyResult.Job.Id, following: Cli.Following)(using
      console: Console
  ): Either[Cli.Error, Unit] =
    Cli.out.println(s"job `$id` started (`histmersion jobs $id` follows it)")
    following.follow(IngestCmd.pollJob(c, id, 0))

  // ── the external sources (#1064) ───────────────────────────────────────────────────────────────

  // ── a person's edits (#987) ────────────────────────────────────────────────────────────────────

  // ── a person's kind, period and point for a place (V080, V081) ───────────────────────────────────

  /** `places site-types` (#1449): refine what the Wikidata sites on the map are from their items. */

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    case Search(q: Option[String], asOf: Option[Api.AggregateQuery.AsOf], limit: Option[Int])
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case Show(id: String, asOf: Option[Api.AggregateQuery.AsOf], depth: Option[Int], noTree: Boolean)
    case Children(id: String, asOf: Option[Api.AggregateQuery.AsOf])
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case SiteTypes(writes: Cli.Writes, limit: Option[Int], refresh: Boolean, following: Cli.Following)
    case NameAdd(id: String, name: String, lang: Option[String])
    case NameRm(id: String, name: String, lang: Option[String])
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case NameListing(place: Option[String], csv: Boolean)
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case SetCmd(
        id: String,
        kind: Option[String],
        from: Option[Long],
        to: Option[Long],
        point: Option[(Latitude, Longitude)],
        reason: Option[String],
        reset: Boolean
    )
    case Overrides(place: Option[String])
    case Merge(id: String, into: String, reason: Option[String])
    case Rm(id: String, reason: Option[String])
    case Add(
        label: String,
        kind: String,
        lon: Double,
        lat: Double,
        source: String,
        id: Option[String],
        names: List[String],
        lang: Option[String],
        from: Option[Long],
        to: Option[Long]
    )
    case Import(file: String, kind: Option[String], batch: Option[Int], dataset: Dataset)
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case Seed(dir: Option[String], batch: Option[Int], noRebuild: Boolean)
    case Fetch(source: String, writes: Cli.Writes, following: Cli.Following)
    case Sources()
    case Load(source: String, writes: Cli.Writes, following: Cli.Following)
    case Rebuild()
    case BuildHierarchy(following: Cli.Following)

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.Search(q, asOf, limit) =>
          c.places(q.map(_.trim).filter(_.nonEmpty).map(Api.Search.Text(_)), asOf, limit.map(Paging.Limit(_)))
            .map(show(_, asOf))
        case Command.Show(id, asOf, depth, noTree) =>
          for
            d <- c.place(Place.Id(id), asOf)
            tree <- if noTree then Right(Nil) else c.placeTree(Place.Id(id), asOf, depth.map(Place.Depth(_)))
          yield Cli.out.println(PlacesMarkdown.place(d, tree.toVector))
        case Command.Children(id, asOf) =>
          c.placeChildren(Place.Id(id), asOf).map { rows =>
            if rows.isEmpty then Cli.out.println("_No children._")
            else Cli.out.println(rows.map(x => s"- ${x.label} `${x.id}` _(${x.kind})_").mkString("\n"))
          }
        case Command.SiteTypes(writes, limit, refresh, following) =>
          c.call(EventsApi.refineSiteTypes)(
            SiteTypes.Request(
              (if writes.dry then SiteTypes.Request.DryRun.DryRun else SiteTypes.Request.DryRun.Apply),
              limit.map(SiteTypes.Request.Limit(_)),
              (if refresh then SiteTypes.Request.Refresh.Refresh else SiteTypes.Request.Refresh.Reuse)
            )
          ).flatMap(EventsCmd.follow(c, _, following))
        case Command.NameAdd(id, name, lang) =>
          c.addPlaceName(Place.Id(id), Place.NameAdd(Place.NameAdd.Name(name), lang.map(Place.NameAdd.Lang(_))))
            .map(r => Cli.out.println(PlacesMarkdown.placeNameChanged(r)))
        case Command.NameRm(id, name, lang) =>
          c.removePlaceName(
            Place.Id(id),
            Place.NameRemove(Place.NameRemove.Name(name), lang.map(Place.NameRemove.Lang(_)))
          ).map(r => Cli.out.println(PlacesMarkdown.placeNameChanged(r)))
        case Command.NameListing(place, csv) =>
          c.placeNames(place.map(Place.Id(_)))
            .map(rows =>
              Cli.out.print(if csv then Place.Edits.csv(rows)
              else PlacesMarkdown.manualPlaceNames(rows) + "\n")
            )
        case Command.SetCmd(id, kind, from, to, point, reason, reset) =>
          c.setPlaceOverride(
            Place.Id(id),
            Place
              .OverrideSet(
                kind.map(Place.OverrideSet.Kind(_)),
                from.map(Year(_)),
                to.map(Year(_)),
                reason.map((text => Place.Rationale(text))),
                (if reset then Place.OverrideSet.Reset.Reset else Place.OverrideSet.Reset.Set),
                point.map(_._1),
                point.map(_._2)
              )
          ).map(r => Cli.out.println(PlacesMarkdown.placeOverrideChanged(r)))
        case Command.Overrides(place) =>
          c.placeOverrides(place.map(Place.Id(_))).map(rows => Cli.out.println(PlacesMarkdown.placeOverrides(rows)))
        case Command.Merge(id, into, reason) =>
          c.mergePlace(
            Place.Id(id),
            Place.MergeIn(Place.Id(into), reason.map((text => Place.Rationale(text))))
          ).map(r => Cli.out.println(PlacesMarkdown.placeGone(r)))
        case Command.Rm(id, reason) =>
          c.removePlace(Place.Id(id), Place.RemoveIn(reason.map((text => Place.Rationale(text)))))
            .map(r => Cli.out.println(PlacesMarkdown.placeGone(r)))
        case Command.Add(label, kind, lon, lat, source, id, names, lang, from, to) =>
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
        case Command.Import(file, kind, batch, dataset) =>
          importFile(
            c,
            Path.of(file),
            kind.map(Place.KindValue(_)),
            batch.map(Paging.Limit(_)).getOrElse(defaultBatch),
            dataset
          )
            .map(r => Cli.out.println(PlacesMarkdown.placeImport(r)))
        case Command.Seed(dir, batch, noRebuild) =>
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
                Problem.Unclassified(
                  Legacy.Text(s"not a directory: ${tried.mkString(", ")} — pass --dir <path to db/places>")
                )
              )
            )
          else
            val files = seedFiles(d)
            if files.isEmpty then
              Left(
                Cli.Error(
                  Problem.Unclassified(Legacy.Text(s"no JSON files under $d — run db/places/fetch.sh first"))
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
        case Command.Fetch(source, writes, following) =>
          c.fetchExternalPlaces(Api.ExternalPlaces.Source(source), writes).flatMap { r =>
            r.job match
              case Some(id) => started(c, SpatialHierarchyResult.Job.Id(id.value), following)
              case None     =>
                Right(Cli.out.println(s"${if writes.dry then "[dry run] " else ""}${PlacesMarkdown.placeImport(r)}"))
          }
        case Command.Sources() =>
          c.placeSources.map(rows => Cli.out.println(PlacesMarkdown.gazetteerSources(rows)))
        case Command.Load(source, writes, following) =>
          c.loadPlaces(
            Api.ExternalPlaces.Source(source),
            (if writes.applies then Api.Apply.Apply else Api.Apply.DryRun)
          ).flatMap { j =>
            Cli.out.println(
              s"${if writes.applies then "import" else "dry run"} of $source: job `${j.id}` started (`histmersion jobs` lists it)"
            )
            following.follow(IngestCmd.pollJob(c, j.id, 0))
          }
        case Command.Rebuild() =>
          c.rebuildPlaces.map(r => Cli.out.println(PlacesMarkdown.placeRebuild(r)))
        case Command.BuildHierarchy(following) =>
          c.buildHierarchy.flatMap { r =>
            r.job match
              // a server that runs it as a job (#1946) answers the job; the counts are its summary
              case Some(id) => started(c, id, following)
              case None     =>
                Right(
                  Cli.out.println(
                    s"**${r.edgesCreated}** spatial containment edges created " +
                      s"(${r.polygonPlaces} polygon places, ${r.pointPlaces} point places)"
                  )
                )
          }
