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

  private val exportCmd = Opts.subcommand("export", "Write each source to <dir>/<citekey>.md (frontmatter + notes body) and the bookkeeping queues to <dir>/meta/*.md") {
    (
      Opts.argument[String]("dir"),
      Opts.arguments[String]("citekey").orEmpty,
      Opts.option[String]("meta-dir", "Where ACQUIRE.md/TASKS.md/LOG.md go (default: <dir>/meta)").orNone,
      Opts.flag("no-meta", "Do not write the bookkeeping queues").orFalse
    ).mapN { (dir, only, metaDir, noMeta) => (c: Client) =>
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
          // the bookkeeping queues are part of the same mirror on disk (plan §5.2 stream 2.8)
          val meta =
            if noMeta then Right(())
            else
              Ops.writeMeta(c, metaDir.map(Path.of(_)).getOrElse(out.resolve("meta")))
                .map(fs => println(s"wrote ${fs.mkString(", ")}"))
          meta.flatMap { _ =>
            if failed.isEmpty then Right(())
            else Left(CliError(s"${failed.size} of $total source(s) could not be exported", exitCode = 2))
          }
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

  // ── the shared Filter flags (plan §1.0): identical set on `search` and `facets` ──────────────
  private val filterFlags: Opts[Filter] = (
    Opts.options[String]("tag", "Tag that must be present (repeatable; ALL are required)").orEmpty,
    Opts.options[String]("author", "Author substring (repeatable; ANY matches)").orEmpty,
    Opts.options[String]("place", "Place/region name (repeatable; ANY matches)").orEmpty,
    Opts.options[String]("topic", "Topic that must be present (repeatable; ALL are required)").orEmpty,
    Opts.options[String]("status", s"Source status (repeatable; ANY matches)").orEmpty,
    Opts.option[Long]("year-from", "Earliest publication year").orNone,
    Opts.option[Long]("year-to", "Latest publication year").orNone,
    Opts.option[Long]("from", "Deep-time start (signed year, BCE negative)").orNone,
    Opts.option[Long]("to", "Deep-time end (signed year)").orNone,
    Opts.option[String]("bbox", "minLon,minLat,maxLon,maxLat (minLon > maxLon crosses the antimeridian)").orNone,
    Opts.option[String]("sort", s"Ordering: ${Sort.all.mkString("|")}").orNone,
    Opts.option[Int]("limit", "Max results").orNone,
    Opts.option[Int]("offset", "Skip this many results").orNone
  ).mapN { (tags, authors, places, topics, status, yearFrom, yearTo, from, to, bbox, sort, limit, offset) =>
    val b = bbox.flatMap(BBox.parse)
    Filter.of(None, tags, authors, places, topics, status, yearFrom, yearTo, from, to,
      b.map(_.minLon), b.map(_.minLat), b.map(_.maxLon), b.map(_.maxLat), sort, limit, offset)
  }

  private val search = Opts.subcommand("search", "Search the corpus (websearch syntax: \"phrase\", -not, or): sources with the findings that matched") {
    (Opts.argument[String]("query").orNone, filterFlags).mapN { (q, f) => (c: Client) =>
      c.sources(f.copy(q = q.map(_.trim).filter(_.nonEmpty)))
        .map(r => println(Markdown.searchResults(r)))
    }
  }

  private val facets = Opts.subcommand("facets", s"Vocabulary of a dimension (${FacetField.all.mkString("|")}) with distinct-source counts") {
    (
      Opts.argument[String]("field"),
      Opts.option[String]("prefix", "Case-insensitive prefix match").orNone,
      Opts.argument[String]("query").orNone,
      filterFlags
    ).mapN { (field, prefix, q, f) => (c: Client) =>
      c.facets(field, prefix, f.copy(q = q.map(_.trim).filter(_.nonEmpty)))
        .map(rows => println(Markdown.facets(field, rows.toVector)))
    }
  }

  /** The server re-chunks a bounded slice per call (a whole 89 MB corpus takes minutes — far past the
    * HTTP timeout), so the CLI drives the cursor and reports progress until nothing is left. */
  @annotation.tailrec
  private def reindexLoop(c: Client, after: Option[String], sources: Int, chunks: Int): Either[CliError, Unit] =
    c.reindex(after, None) match
      case Left(e) => Left(e)
      case Right(r) =>
        val (s, ch) = (sources + r.sources, chunks + r.chunks)
        if r.remaining > 0 && r.lastCitekey.isDefined then
          Console.err.print(s"\rreindexing… $s source(s), $ch chunk(s), ${r.remaining} to go")
          reindexLoop(c, r.lastCitekey, s, ch)
        else
          if sources > 0 then Console.err.print("\r" + " " * 60 + "\r")
          println(s"reindexed $s source(s) → $ch chunk(s)")
          Right(())

  private val reindex = Opts.subcommand("reindex", "Rebuild the full-text chunk index for every source") {
    Opts.unit.map(_ => (c: Client) => reindexLoop(c, None, 0, 0))
  }


  private val get = Opts.subcommand("get", "Show one source as Markdown") {
    (Opts.argument[String]("citekey"), Opts.flag("full", "Include full text").orFalse)
      .mapN((ck, full) => (c: Client) => c.getSource(ck).map(sw => println(Markdown.source(sw.source, sw.findings.toVector, full))))
  }

  /** The map feed: individual findings, filtered by the SAME flags as `search` (`--tag`, `--place`,
    * `--from/--to`, `--bbox`, …). `--region` is kept as an alias of `--place`. */
  private val geoWhen = Opts.subcommand("geo-when", "Findings (the map feed) by place, deep time, bbox — the same filters as `search`") {
    (Opts.option[String]("region", "Region-name substring (alias of --place)").orNone, filterFlags)
      .mapN { (region, f) => (c: Client) =>
        c.findings(Api.FindingsQuery(f, region)).map(hits => println(Markdown.geoResults(hits.toVector)))
      }
  }

  private val histogram = Opts.subcommand("histogram", "When is the evidence? Sources per adaptive time bucket (Markdown table + bar chart), for the same filters as search") {
    (
      Opts.argument[String]("query").orNone,
      Opts.option[String]("region", "Region-name substring (alias of --place)").orNone,
      Opts.option[String]("unit", "sources (default) | findings").orNone,
      filterFlags
    ).mapN { (q, region, unit, f0) => (c: Client) =>
      val f = f0.copy(q = q.map(_.trim).filter(_.nonEmpty), places = f0.places ++ region.toList)
      c.aggregate(Api.AggregateQuery.time(f, unit))
        .map(h => println(Markdown.histogram(h, unit.getOrElse("sources"))))
    }
  }

  private val neighbors = Opts.subcommand("neighbors", "Typed references to/from a source") {
    Opts.argument[String]("citekey").map(ck => (c: Client) => c.neighbors(ck).map(rows => println(Markdown.neighbors(ck, rows.toVector))))
  }

  private val command: Command[(Option[String], Client => Either[CliError, Unit])] =
    Command(name = "histdb", header = "hist-db — CLI over the hist-db server (MCP twin)") {
      (serverOpt,
        version orElse health orElse seed orElse reindex orElse search orElse facets orElse get orElse geoWhen
          orElse histogram orElse neighbors orElse put orElse patch orElse exportCmd
          // stream 2.8 — lint · queue · read-only query
          orElse Ops.lint orElse Ops.queue orElse Ops.query orElse Ops.schema).tupled
    }

  def main(args: Array[String]): Unit =
    command.parse(args.toList, sys.env) match
      case Left(help) => System.err.println(help); sys.exit(1)
      case Right((server, run)) =>
        val url = server.getOrElse(AppConfig.load().cli.serverUrl)
        run(Client(url)) match
          case Right(()) => ()
          case Left(e)   => System.err.println(s"error: ${e.message}"); sys.exit(e.exitCode)
