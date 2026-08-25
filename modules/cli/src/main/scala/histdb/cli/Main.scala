package histdb.cli

import cats.syntax.all.*
import com.monovore.decline.*
import histdb.core.*

import java.nio.file.{Files, Path}

/** hist-db CLI — a pure HTTP client of the server (no DB access on the host). Every command maps to
  * one `Api` endpoint and prints the shared Markdown rendering, so CLI and MCP output agree. */
object Main:

  private val serverOpt =
    Opts.option[String]("server", "hist-db server URL (default: config cli.serverUrl / $HISTDB_SERVER_URL)").orNone

  private val version = Opts.subcommand("version", "Print the CLI and server versions") {
    Opts.unit.map(_ => (c: Client) =>
      println(s"cli    ${Version.name} ${Version.version}")
      c.version.map(v => println(s"server ${v.name} ${v.version}")))
  }

  private val health = Opts.subcommand("health", "Server health (db, schema version)") {
    Opts.unit.map(_ => (c: Client) => c.health.map(h => println(s"${h.status} · db ${h.db} · schema ${h.schemaVersion.getOrElse("?")}")))
  }

  private def report(r: SeedResult): Unit =
    println(s"imported ${r.imported}, skipped ${r.skipped.size} (newer in DB), " +
      s"failed ${r.failed.size}, edges unified ${r.edgesResolved}")
    r.skipped.take(10).foreach(s => println(s"  ~ $s"))
    r.failed.take(10).foreach(f => Console.err.println(s"  ! $f"))

  private val seed = Opts.subcommand("seed", "Import a wiki/sources directory — read on the HOST when that path exists here, otherwise asked of the SERVER (empty = configured corpus dir)") {
    (Opts.argument[String]("dir").withDefault(""), Opts.flag("force", "Overwrite rows newer than the files").orFalse)
      .mapN { (dir, force) => (c: Client) =>
        Option(dir).filter(_.nonEmpty).map(Path.of(_)).filter(Files.isDirectory(_)) match
          case Some(p) => hostSeed(c, p, force)
          case None    => c.seed(SeedRequest(dir, force)).map(report) // server-side (bind-mounted) corpus
      }
  }

  /** Seed from a directory on THIS machine: the CLI reads each note and posts its text, so a corpus
    * the container cannot see still gets in. Progress is printed per file; the edge-unification pass
    * a server-side seed does at the end is requested explicitly once every note is stored. */
  private def hostSeed(c: Client, dir: Path, force: Boolean): Either[CliError, Unit] =
    val files = Notes.markdownFiles(dir)
    val total = files.size
    println(s"seeding $total note(s) from $dir")
    val acc = files.zipWithIndex.foldLeft(SeedResult(0, Nil, 0, Nil)) { case (sum, (f, i)) =>
      val ck = f.getFileName.toString.stripSuffix(".md")
      println(s"${i + 1}/$total $ck")
      c.importNote(Notes.toImport(f, force)) match
        case Right(r) =>
          sum.copy(imported = sum.imported + r.imported,
            failed = sum.failed ++ r.failed, skipped = sum.skipped ++ r.skipped)
        case Left(e) => sum.copy(failed = sum.failed :+ s"$ck: ${e.message}")
    }
    c.resolveEdges.map(e => report(acc.copy(edgesResolved = e.resolved)))

  private val put = Opts.subcommand("put", "Send ONE note file (frontmatter + body) as a full create/replace") {
    Opts.argument[String]("file.md").map { file => (c: Client) =>
      val p = Path.of(file)
      if !Files.isRegularFile(p) then Left(CliError(s"no such file: $file", exitCode = 1))
      // an explicit `put` is always deliberate → force past the "newer in DB" guard
      else c.importNote(Notes.toImport(p, force = true)).map(report)
    }
  }

  private val exportCmd = Opts.subcommand("export", "Write each source to <dir>/<citekey>.md (frontmatter + notes body)") {
    (Opts.argument[String]("dir"), Opts.arguments[String]("citekey").orEmpty)
      .mapN { (dir, only) => (c: Client) =>
        val out = Path.of(dir)
        val _   = Files.createDirectories(out)
        (if only.nonEmpty then Right(only) else c.citekeys).flatMap { ks =>
          val total  = ks.size
          val failed = ks.zipWithIndex.flatMap { (ck, i) =>
            println(s"${i + 1}/$total $ck")
            c.exportSource(ck) match
              case Right(text) => val _ = Files.writeString(out.resolve(s"$ck.md"), text); None
              case Left(e)     => Some(s"$ck: ${e.message}")
          }
          println(s"exported ${total - failed.size} note(s) to $out, failed ${failed.size}")
          failed.take(10).foreach(f => Console.err.println(s"  ! $f"))
          // a partial export must not look like success — the mirror on disk would be silently stale
          if failed.isEmpty then Right(())
          else Left(CliError(s"${failed.size} of $total source(s) could not be exported", exitCode = 2))
        }
      }
  }

  private val patch = Opts.subcommand("patch", "Selectively enrich one source — only the parts you pass are changed") {
    (
      Opts.argument[String]("citekey"),
      Opts.option[String]("json", "A whole SourcePatch as JSON (the flags below override its parts)").orNone,
      Opts.option[String]("tags-add", "Comma-separated tags to add").orNone,
      Opts.option[String]("tags-remove", "Comma-separated tags to remove").orNone,
      Opts.option[String]("tags-replace", "Comma-separated tags replacing the current set").orNone,
      Opts.option[String]("topics", "Comma-separated topics replacing the current list").orNone,
      Opts.option[String]("regions", "Comma-separated regions replacing the current list").orNone,
      Opts.option[String]("summary", "New summary").orNone,
      Opts.option[String]("status", "foundational|current|contested|superseded|debunked|retracted|unassessed").orNone,
      Opts.option[String]("title", "New title").orNone,
      Opts.option[String]("notes-file", "File whose Markdown becomes this source's notes article").orNone,
      Opts.option[String]("period", "start,end as signed years, BCE negative (e.g. -3300,-2600)").orNone,
      Opts.options[String]("add-finding", "A FindingInput as JSON (repeatable)").orEmpty,
      Opts.options[String]("remove-finding", "Finding id to drop, e.g. 'haak2015_massive#2' (repeatable)").orEmpty,
      Opts.option[String]("if-updated-at", "Precondition: the updatedAt you last read (fails when the row moved on)").orNone
    ).mapN { (ck, json, tAdd, tRem, tRep, tops, regs, summary, status, title, notesFile, period, addF, remF, ifUpd) =>
      (c: Client) =>
        Patches
          .build(json, tAdd, tRem, tRep, tops, regs, summary, status, title, notesFile, period, addF, remF, ifUpd)
          .flatMap(p =>
            c.patchSource(ck, p).map { sw =>
              println(Markdown.source(sw.source, sw.findings.toVector, false))
              sw.source.updatedAt.foreach(u => println(s"\nupdatedAt: $u"))
            })
    }
  }

  private val search = Opts.subcommand("search", "Search the corpus (text query)") {
    (Opts.argument[String]("query").orNone, Opts.option[Int]("limit", "Max results").orNone)
      .mapN((q, limit) => (c: Client) => c.search(q, limit).map(hits => println(Markdown.searchResults(hits.toVector))))
  }

  private val get = Opts.subcommand("get", "Show one source as Markdown") {
    (Opts.argument[String]("citekey"), Opts.flag("full", "Include full text").orFalse)
      .mapN((ck, full) => (c: Client) => c.getSource(ck).map(sw => println(Markdown.source(sw.source, sw.findings.toVector, full))))
  }

  private val geoWhen = Opts.subcommand("geo-when", "Findings by region and/or deep-time range and/or bbox") {
    (
      Opts.option[String]("region", "Region-name substring").orNone,
      Opts.option[Long]("from", "Period start (signed year, BCE negative)").orNone,
      Opts.option[Long]("to", "Period end (signed year)").orNone,
      Opts.option[String]("bbox", "minLon,minLat,maxLon,maxLat").orNone,
      Opts.option[Int]("limit", "Max results").orNone
    ).mapN { (region, from, to, bbox, limit) => (c: Client) =>
      val b = bbox.map(_.split(",").map(_.trim.toDouble)).filter(_.length == 4)
      val q = Api.FindingsQuery(region, b.map(_(0)), b.map(_(1)), b.map(_(2)), b.map(_(3)), from, to, limit)
      c.findings(q).map(hits => println(Markdown.geoResults(hits.toVector)))
    }
  }

  private val neighbors = Opts.subcommand("neighbors", "Typed references to/from a source") {
    Opts.argument[String]("citekey").map(ck => (c: Client) => c.neighbors(ck).map(rows => println(Markdown.neighbors(ck, rows.toVector))))
  }

  private val command: Command[(Option[String], Client => Either[CliError, Unit])] =
    Command(name = "histdb", header = "hist-db — CLI over the hist-db server (MCP twin)") {
      (serverOpt,
        version orElse health orElse seed orElse search orElse get orElse geoWhen orElse neighbors
          orElse put orElse patch orElse exportCmd).tupled
    }

  def main(args: Array[String]): Unit =
    command.parse(args.toList, sys.env) match
      case Left(help) => System.err.println(help); sys.exit(1)
      case Right((server, run)) =>
        val url = server.getOrElse(AppConfig.load().cli.serverUrl)
        run(Client(url)) match
          case Right(()) => ()
          case Left(e)   => System.err.println(s"error: ${e.message}"); sys.exit(e.exitCode)
