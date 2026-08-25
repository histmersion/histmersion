package histdb.cli

import cats.syntax.all.*
import com.monovore.decline.*
import histdb.core.{Db, Importer, Markdown, SourceRepo, Version}
import java.nio.file.Path

/** hist-db CLI — direct-style (decline core). Commands share `core` with the server.
  * Next: `serve`, `calibre`, `ingest`, plus an sttp client to a live server. */
object Main:

  private val version =
    Opts.subcommand("version", "Print the hist-db version") {
      Opts.unit.map(_ => println(s"${Version.name} ${Version.version}"))
    }

  private val seed =
    Opts.subcommand("seed", "Import a wiki/sources directory into Postgres (idempotent)") {
      Opts.argument[String]("dir").map { dir =>
        val res = Importer.importDir(Db.fromConfig(), Path.of(dir))
        println(s"imported ${res.imported}, failed ${res.failed.size}, edges unified ${res.edgesResolved}")
        res.failed.take(10).foreach(f => Console.err.println(s"  ! $f"))
      }
    }

  private val search =
    Opts.subcommand("search", "Search the corpus (text query)") {
      Opts.argument[String]("query").map { q =>
        println(Markdown.searchResults(SourceRepo.search(Db.fromConfig(), Some(q), 20)))
      }
    }

  private val get =
    Opts.subcommand("get", "Show one source as Markdown") {
      (Opts.argument[String]("citekey"), Opts.flag("full", "Include full text").orFalse).mapN { (ck, full) =>
        SourceRepo.get(Db.fromConfig(), ck) match
          case Some((s, fs)) => println(Markdown.source(s, fs, full))
          case None          => Console.err.println(s"no source '$ck'")
      }
    }

  private val geoWhen =
    Opts.subcommand("geo-when", "Findings by region and/or deep-time range") {
      (
        Opts.option[String]("region", "Region-name substring").orNone,
        Opts.option[Long]("from", "Period start (signed year, BCE negative)").orNone,
        Opts.option[Long]("to", "Period end (signed year)").orNone
      ).mapN { (region, from, to) =>
        println(Markdown.geoResults(SourceRepo.geoWhen(Db.fromConfig(), region, None, from, to, 50)))
      }
    }

  private val neighbors =
    Opts.subcommand("neighbors", "Typed references to/from a source") {
      Opts.argument[String]("citekey").map { ck =>
        println(Markdown.neighbors(ck, SourceRepo.neighbors(Db.fromConfig(), ck)))
      }
    }

  private val command: Command[Unit] =
    Command(name = "histdb", header = "hist-db — CLI over the hist-db server + local Calibre/file ops") {
      version orElse seed orElse search orElse get orElse geoWhen orElse neighbors
    }

  def main(args: Array[String]): Unit =
    command.parse(args.toList) match
      case Left(help) => System.err.println(help); sys.exit(1)
      case Right(_)   => ()
