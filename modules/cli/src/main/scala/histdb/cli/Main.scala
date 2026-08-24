package histdb.cli

import com.monovore.decline.*
import histdb.core.{Db, Importer, Markdown, SourceRepo, Version}
import java.nio.file.Path

/** hist-db CLI — direct-style (decline core). Commands share `core` with the server.
  * Next: `serve`, `calibre`, `ingest`, `geo-when`, `neighbors`, plus an sttp client to a live server. */
object Main:

  private val version =
    Opts.subcommand("version", "Print the hist-db version") {
      Opts.unit.map(_ => println(s"${Version.name} ${Version.version}"))
    }

  private val seed =
    Opts.subcommand("seed", "Import a wiki/sources directory into Postgres (idempotent)") {
      Opts.argument[String]("dir").map { dir =>
        val res = Importer.importDir(Db.fromConfig(), Path.of(dir))
        println(s"imported ${res.imported}, failed ${res.failed.size}")
        res.failed.take(10).foreach(f => Console.err.println(s"  ! $f"))
      }
    }

  private val search =
    Opts.subcommand("search", "Search the corpus (text query)") {
      Opts.argument[String]("query").map { q =>
        println(Markdown.searchResults(SourceRepo.search(Db.fromConfig(), Some(q), 20)))
      }
    }

  private val command: Command[Unit] =
    Command(name = "histdb", header = "hist-db — CLI over the hist-db server + local Calibre/file ops") {
      version orElse seed orElse search
    }

  def main(args: Array[String]): Unit =
    command.parse(args.toList) match
      case Left(help) => System.err.println(help); sys.exit(1)
      case Right(_)   => ()
