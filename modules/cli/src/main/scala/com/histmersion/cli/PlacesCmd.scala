package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.core.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** `histmersion places …` — the gazetteer subcommands (plan §1.6a). Kept in its own file so the shared
  * `Main` only gains one line.
  *
  * Import is done in BATCHES: the parse is pure and happens on the HOST ([[Lpf]] lives in `core`, and
  * the CLI depends on `core` only), and each batch is re-rendered as Linked Places Format and POSTed,
  * so a 5 MB Pleiades extract never becomes one 5 MB request body and the user sees progress. */
object PlacesCmd:

  /** Config-driven defaults (no literals in code, plan §1.9); read on demand so `--help` never
    * needs a config file to exist. */
  private def cfg               = AppConfig.load().places
  private def defaultBatch: Int = cfg.importBatch
  private def seedDir: String   = cfg.seedDir

  private val asOfOpt =
    Opts.option[Long]("as-of", "Signed year the gazetteer is read at (BCE negative, e.g. -1400)").orNone

  private def show(rows: List[PlaceHit], asOf: Option[Long]): Unit =
    println(Markdown.places(rows.toVector, asOf))

  private val search = Opts.subcommand("search", "Find places by name; the `id` it prints is what `--place` wants") {
    (Opts.argument[String]("query").orNone, asOfOpt,
      Opts.option[Int]("limit", "Max results").orNone)
      .mapN { (q, asOf, limit) => (c: Client) =>
        c.places(q.map(_.trim).filter(_.nonEmpty), asOf, limit).map(show(_, asOf))
      }
  }

  private val showCmd = Opts.subcommand("show", "One place: geometry, validity, parents and the subtree") {
    (Opts.argument[String]("id"), asOfOpt,
      Opts.option[Int]("depth", "How deep the subtree goes").orNone,
      Opts.flag("no-tree", "Skip the hierarchy walk").orFalse)
      .mapN { (id, asOf, depth, noTree) => (c: Client) =>
        for
          d    <- c.place(id, asOf)
          tree <- if noTree then Right(Nil) else c.placeTree(id, asOf, depth)
        yield println(Markdown.place(d, tree.toVector))
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
  private[cli] def importFile(c: Client, file: Path, kind: Option[String], batch: Int)
      : Either[CliError, PlaceImportResult] =
    if !Files.isRegularFile(file) then Left(CliError(s"no such file: $file", exitCode = 1))
    else
      Lpf.parse(Files.readString(file), kind.getOrElse(PlaceKind.Region)) match
        case Left(e) => Left(CliError(s"$file: $e", exitCode = 1))
        case Right(items) =>
          val groups = items.grouped(batch.max(1)).toList
          val total  = groups.size
          groups.zipWithIndex.foldLeft[Either[CliError, PlaceImportResult]](
            Right(PlaceImportResult(0, 0, 0, Nil))) { case (acc, (g, i)) =>
            acc.flatMap { sum =>
              if total > 1 then
                Console.err.print(s"\r${file.getFileName}: batch ${i + 1}/$total (${sum.places} place(s))")
              c.importPlaces(kind, Lpf.render(g)).map(r =>
                PlaceImportResult(sum.places + r.places, sum.names + r.names,
                  sum.relations + r.relations, (sum.failed ++ r.failed).take(20)))
            }
          }.map { r =>
            if total > 1 then Console.err.print("\r" + " " * 72 + "\r")
            r
          }

  private val importCmd = Opts.subcommand("import", "Upsert a Linked Places / GeoJSON file into the gazetteer") {
    (Opts.argument[String]("file.json"),
      Opts.option[String]("kind", s"Fallback kind for features that declare none: ${PlaceKind.all.mkString("|")}").orNone,
      Opts.option[Int]("batch", "Features per request").orNone)
      .mapN { (file, kind, batch) => (c: Client) =>
        importFile(c, Path.of(file), kind, batch.getOrElse(defaultBatch))
          .map(r => println(Markdown.placeImport(r)))
      }
  }

  /** Every JSON/GeoJSON file under a seed directory, in import order: the top-level files by name
    * first (Natural Earth defines the `continent_*` ids everything else hangs off), then each
    * subdirectory by name. The fallback `kind` for a file comes from its directory — `polities/` →
    * `polity` — and a feature that declares its own `kind` always wins over it. */
  private[cli] def seedFiles(dir: Path): List[(Path, Option[String])] =
    def listing(d: Path): List[Path] =
      val st = Files.list(d)
      try st.iterator.asScala.toList.sortBy(_.getFileName.toString) finally st.close()
    def isSeed(f: Path) =
      val n = f.getFileName.toString
      Files.isRegularFile(f) && (n.endsWith(".json") || n.endsWith(".geojson"))
    /** `polities` → `polity`, `sites` → `site`, `regions` → `region`; anything else → no override. */
    def dirKind(name: String) =
      val singular = if name.endsWith("ies") then name.dropRight(3) + "y"
        else if name.endsWith("s") then name.dropRight(1) else name
      PlaceKind.parse(singular)
    val entries = listing(dir)
    entries.filter(isSeed).map(_ -> None) ++
      entries.filter(Files.isDirectory(_)).flatMap { sub =>
        listing(sub).filter(isSeed).map(_ -> dirKind(sub.getFileName.toString))
      }

  /** The whole committed seed set (`db/places`), imported in [[seedFiles]] order.
    *
    * A second pass re-imports every file that reported a DANGLING PARENT: a polygon may legitimately
    * name a parent that only exists once a later file has loaded (the traced polities hang off the
    * macro-regions, which hang off Natural Earth's continents), and by the end of pass 1 every place
    * exists. That is why nothing here depends on a clever file-name ordering. */
  private val seed = Opts.subcommand("seed", "Import every gazetteer file under db/places (Natural Earth, Pleiades, traced polities)") {
    (Opts.option[String]("dir", "Directory of LPF/GeoJSON files (default: config places.seedDir)").orNone,
      Opts.option[Int]("batch", "Features per request").orNone,
      Opts.flag("no-rebuild", "Do not re-materialise finding_places afterwards").orFalse)
      .mapN { (dir, batch, noRebuild) => (c: Client) =>
        val d = Path.of(dir.filter(_.nonEmpty).getOrElse(seedDir))
        val n = batch.getOrElse(defaultBatch)
        if !Files.isDirectory(d) then Left(CliError(s"not a directory: $d", exitCode = 1))
        else
          val files = seedFiles(d)
          if files.isEmpty then
            Left(CliError(s"no JSON files under $d — run db/places/fetch.sh first", exitCode = 1))
          else
            def pass(fs: List[(Path, Option[String])], label: String)
                : Either[CliError, List[(Path, Option[String])]] =
              fs.foldLeft[Either[CliError, List[(Path, Option[String])]]](Right(Nil)) {
                case (acc, (f, kind)) =>
                  acc.flatMap { retry =>
                    importFile(c, f, kind, n).map { r =>
                      println(s"$label${d.relativize(f)}: ${Markdown.placeImport(r)}")
                      if r.failed.isEmpty then retry else retry :+ (f -> kind)
                    }
                  }
              }
            for
              retry <- pass(files, "")
              _     <- if retry.isEmpty then Right(Nil)
                       else pass(retry, "re-resolving parents: ")
              _     <- if noRebuild then Right(())
                       else c.rebuildPlaces.map(r => println(Markdown.placeRebuild(r)))
            yield ()
      }
  }

  private val rebuild = Opts.subcommand("rebuild", "Re-materialise which findings fall in which places") {
    Opts.unit.map(_ => (c: Client) => c.rebuildPlaces.map(r => println(Markdown.placeRebuild(r))))
  }

  private val buildHierarchy = Opts.subcommand("build-hierarchy",
      "Compute parent-child edges from spatial containment (point inside polygon). Idempotent.") {
    Opts.unit.map(_ => (c: Client) =>
      c.buildHierarchy.map { r =>
        println(s"**${r.edgesCreated}** spatial containment edges created " +
          s"(${r.polygonPlaces} polygon places, ${r.pointPlaces} point places)")
      }
    )
  }

  private val fetch = Opts.subcommand("fetch",
      "Download and import an external gazetteer (cliopatria, awmc, chronontology) or the coastline (land). Re-runnable and idempotent.") {
    (Opts.argument[String]("source"),
      Opts.flag("dry-run", "Parse and transform only — do not upsert").orFalse)
      .mapN { (source, dryRun) => (c: Client) =>
        c.fetchExternalPlaces(source, dryRun).map { r =>
          println(s"${if dryRun then "[dry run] " else ""}${Markdown.placeImport(r)}")
        }
      }
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("places", "The gazetteer: search, show, import and materialise places") {
      search orElse showCmd orElse children orElse importCmd orElse seed orElse fetch orElse rebuild orElse buildHierarchy
    }
