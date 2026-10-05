package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.core.*

import java.nio.file.{Files, Path}

/** Histmersion CLI — a pure HTTP client of the server (no DB access on the host). Every command maps to
  * one `Api` endpoint and prints the shared Markdown rendering, so CLI and MCP output agree. */
object Main:

  private val serverOpt =
    Opts.option[String]("server", "Histmersion server URL (default: config cli.serverUrl / $HISTMERSION_SERVER_URL)").orNone
  private val tokenOpt =
    Opts.option[String]("token", "API key for authenticated servers (or $HISTMERSION_TOKEN)").orNone

  private val version = Opts.subcommand("version", "Print the CLI and server versions") {
    Opts.unit.map(_ => (c: Client) =>
      println(s"cli    ${Version.name} ${Version.version}")
      c.version.map(v => println(s"server ${v.name} ${v.version}")))
  }

  private val health = Opts.subcommand("health", "Server health (db, schema version)") {
    Opts.unit.map(_ => (c: Client) => c.health.map(h => println(s"${h.status} · db ${h.db} · schema ${h.schemaVersion.getOrElse("?")}")))
  }

  private val about = Opts.subcommand("about", "Server build (version, commit), health, job queue, last backup and store counts") {
    Opts.unit.map(_ => (c: Client) => c.about.map(a => println(AboutMarkdown.render(a))))
  }

  private def report(r: SeedResult): Unit =
    println(s"imported ${r.imported}, skipped ${r.skipped.size} (newer in DB), " +
      s"failed ${r.failed.size}, edges unified ${r.edgesResolved}")
    r.skipped.take(10).foreach(s => println(s"  ~ $s"))
    r.failed.take(10).foreach(f => Console.err.println(s"  ! $f"))

  private val seed = Opts.subcommand("seed", "Import a wiki/ or wiki/sources directory (articles beside it come too) — read on the HOST when that path exists here, otherwise asked of the SERVER (empty = configured corpus dir)") {
    (Opts.argument[String]("dir").withDefault(""), Opts.flag("force", "Overwrite rows newer than the files").orFalse)
      .mapN { (dir, force) => (c: Client) =>
        Option(dir).filter(_.nonEmpty).map(Path.of(_)).filter(Files.isDirectory(_)) match
          case Some(p) => hostSeed(c, p, force)
          case None    => c.seed(SeedRequest(dir, force)).map(report) // server-side (bind-mounted) corpus
      }
  }

  /** Seed from a directory on THIS machine: the CLI reads each note and posts its text, so a corpus
    * the container cannot see still gets in. Progress is printed per file; the edge-unification pass
    * a server-side seed does at the end is requested explicitly once every note is stored.
    *
    * `dir` may be the source-note directory (`wiki/sources`) or the wiki ROOT (`wiki`, which holds
    * `sources/` plus the article directories) — see [[ArticleCli.WikiLayout]]. Whenever the layout
    * has a root, the compiled articles beside `sources/` are imported too (plan §5.2 stream 2.7). */
  private def hostSeed(c: Client, dir: Path, force: Boolean): Either[CliError, Unit] =
    val layout = ArticleCli.WikiLayout.of(dir)
    val files  = Notes.markdownFiles(layout.sources)
    val total = files.size
    println(s"seeding $total note(s) from ${layout.sources}")
    val acc = files.zipWithIndex.foldLeft(SeedResult(0, Nil, 0, Nil)) { case (sum, (f, i)) =>
      val ck = f.getFileName.toString.stripSuffix(".md")
      println(s"${i + 1}/$total $ck")
      c.importNote(Notes.toImport(f, force)) match
        case Right(r) =>
          // the server cannot see the host's `md/<ck>/figures`, so the CLI uploads them (plan §1.8)
          val figureFailure =
            if r.imported == 0 then None
            else
              Notes.figures(f, ck, FigureBytes, FigureCount) match
                case Nil => None
                case fs  => c.putAssets(ck, fs).left.toOption.map(e => s"$ck (figures): ${e.message}")
          sum.copy(imported = sum.imported + r.imported,
            failed = sum.failed ++ r.failed ++ figureFailure, skipped = sum.skipped ++ r.skipped)
        case Left(e) => sum.copy(failed = sum.failed :+ s"$ck: ${e.message}")
    }
    val done = c.resolveEdges.map(e => report(acc.copy(edgesResolved = e.resolved)))
    layout.root.fold(done)(root =>
      done.map(_ => ArticleCli.report(ArticleCli.importAll(c, root, force, Set(ArticleCli.WikiLayout.SourcesDir)))))

  /** Host-side caps on what `seed` uploads per note. Deliberately conservative: the SERVER enforces
    * its own `limits.asset-bytes`, this only keeps one request from becoming enormous. */
  private val FigureBytes = 8L * 1024 * 1024
  private val FigureCount = 200

  private val put = Opts.subcommand("put", "Send ONE note file (frontmatter + body) as a full create/replace") {
    Opts.argument[String]("file.md").map { file => (c: Client) =>
      val p = Path.of(file)
      if !Files.isRegularFile(p) then Left(CliError(s"no such file: $file", exitCode = 1))
      // an explicit `put` is always deliberate → force past the "newer in DB" guard
      else c.importNote(Notes.toImport(p, force = true)).map(report)
    }
  }

  private val exportCmd = Opts.subcommand("export", "Write sources to <dir>/sources/, articles to <dir>/<kind>/, bookkeeping to <dir>/meta/ — the seed layout") {
    (
      Opts.argument[String]("dir"),
      Opts.arguments[String]("citekey").orEmpty,
      Opts.option[String]("sources-dir", "Where source notes go (default: <dir>/sources)").orNone,
      Opts.option[String]("articles-dir", "Where article notes go (default: <dir>)").orNone,
      Opts.option[String]("meta-dir", "Where ACQUIRE.md/TASKS.md/LOG.md go (default: <dir>/meta)").orNone,
      Opts.flag("no-meta", "Do not write the bookkeeping queues").orFalse
    ).mapN { (dir, only, srcDir, artDir, metaDir, noMeta) => (c: Client) =>
        val out      = Path.of(dir)
        val sources  = srcDir.map(Path.of(_)).getOrElse(out.resolve("sources"))
        val articles = artDir.map(Path.of(_)).getOrElse(out)
        val _        = Files.createDirectories(sources)
        (if only.nonEmpty then Right(only) else c.citekeys).flatMap { ks =>
          val total  = ks.size
          val failed = ks.zipWithIndex.flatMap { (ck, i) =>
            println(s"${i + 1}/$total $ck")
            c.exportSource(ck) match
              case Right(text) => val _ = Files.writeString(sources.resolve(s"$ck.md"), text); None
              case Left(e)     => Some(s"$ck: ${e.message}")
          }
          println(s"exported ${total - failed.size} note(s) to $sources, failed ${failed.size}")
          // a renamed source's note would otherwise linger under its OLD citekey — and a later
          // `seed` of the mirror would try to bring the old name back — so a full export removes it
          if only.isEmpty then pruneRenamed(c, sources)
          failed.take(10).foreach(f => Console.err.println(s"  ! $f"))
          // a partial export must not look like success — the mirror on disk would be silently stale
          // the compiled wiki (2.7) and the bookkeeping queues (2.8) are part of the same mirror on disk
          val meta =
            if noMeta then Right(())
            else
              Ops.writeMeta(c, metaDir.map(Path.of(_)).getOrElse(out.resolve("meta")))
                .map(fs => println(s"wrote ${fs.mkString(", ")}"))
          meta.flatMap { _ =>
            if failed.nonEmpty then
              Left(CliError(s"${failed.size} of $total source(s) could not be exported", exitCode = 2))
            else if only.nonEmpty then Right(())
            else ArticleCli.exportAll(c, articles).map(_ => ())
          }
        }
      }
  }

  /** Delete `<sources>/<alias>.md` for every old citekey (V027). Best effort: a server without the
    * alias endpoint, or a file that cannot be removed, is reported and does not fail the export. */
  private def pruneRenamed(c: Client, sources: Path): Unit =
    c.sourceAliases match
      case Left(e) => Console.err.println(s"  ! could not list renamed citekeys: ${e.message}")
      case Right(as) =>
        val (failed, removed) = RenameCmd.pruneRenamed(sources, as).partitionMap(identity)
        failed.foreach(f => Console.err.println(s"  ! $f"))
        if removed.nonEmpty then
          println(s"removed ${removed.size} note(s) of renamed citekeys: ${removed.mkString(", ")}")

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
      Opts.option[String]("status", SourceStatus.values.map(_.wire).mkString("|"))
        .mapValidated(v => SourceStatus.values.find(_.wire == v.trim.toLowerCase)
          .toValidNel(s"unknown status '$v' (one of: ${SourceStatus.values.map(_.wire).mkString(", ")})"))
        .orNone,
      Opts.option[String]("title", "New title").orNone,
      Opts.option[String]("notes-file", "File whose Markdown becomes this source's notes article").orNone,
      Opts.option[String]("period", "start,end as signed years, BCE negative (e.g. -3300,-2600)").orNone,
      Opts.option[String]("body-file", "File whose text REPLACES the converted body (e.g. a chapter cut out of a volume)").orNone,
      Opts.options[String]("add-finding", "A FindingInput as JSON (repeatable)").orEmpty,
      Opts.options[String]("remove-finding", "Finding id to drop, e.g. 'haak2015_massive#2' (repeatable)").orEmpty,
      Opts.option[String]("if-updated-at", "Precondition: the updatedAt you last read (fails when the row moved on)").orNone,
      Opts.options[String]("add-edge", s"A typed edge FROM this source, <kind>:<citekey>, e.g. challenges:lord1968_oralpoet (repeatable; kinds: ${EdgeKind.allWire.mkString(", ")})").orEmpty,
      Opts.options[String]("remove-edge", "An edge FROM this source to drop, <kind>:<citekey> (repeatable)").orEmpty,
      Opts.flag("strict", "Refuse the patch when its summary or notes have a formatting error").orFalse
    ).mapN { (ck, json, tAdd, tRem, tRep, tops, regs, summary, status, title, notesFile, period, bodyFile, addF, remF, ifUpd, addE, remE, strict) =>
      (c: Client) =>
        Patches
          .build(json, tAdd, tRem, tRep, tops, regs, summary, status, title, notesFile, period, bodyFile, addF, remF, ifUpd,
            addE.toList, remE.toList)
          .flatMap(p =>
            c.patchSource(ck, p, strict).map { sw =>
              println(Markdown.source(sw.source, sw.findings.toVector, false) + FormatMarkdown.notice(sw.formatting))
              sw.source.updatedAt.foreach(u => println(s"\nupdatedAt: $u"))
            })
    }
  }

  // Declared before `search`, which reads it during object initialisation.
  private val GroupAxes = List("time", "region", "time,region", "region,time")

  /** `--group` / `--depth` / `--as-of` / `--samples` (plan §1.6): the SAME filter, rendered as the
    * nested tree instead of a flat list — WHEN and WHERE the evidence sits, before reading any of it. */
  private val search = Opts.subcommand("search", "Search the corpus (websearch syntax: \"phrase\", -not, or): sources with the findings that matched") {
    (
      Opts.argument[String]("query").orNone,
      Opts.option[String]("group", s"Group the results into a tree instead of listing them: ${GroupAxes.mkString(" | ")}").orNone,
      Opts.option[Int]("depth", s"Levels of nesting for --group (1-${Tree.MaxDepth})").orNone,
      Opts.option[Long]("as-of", "Signed year the gazetteer hierarchy is read at for --group region (default: the middle of the window)").orNone,
      Opts.option[Int]("samples", s"Sample sources listed under each leaf of the tree (0-${Tree.MaxSamples})").orNone,
      Opts.option[String]("unit", "What --group counts: sources (default) | findings").orNone,
      Opts.flag("brief", "Compact listing: citekey, title, year, status, period, one why-line").orFalse,
      FilterFlags.opts
    ).mapN { (q, group, depth, asOf, samples, unit, brief, f0) => (c: Client) =>
      val f = f0.copy(q = q.map(_.trim).filter(_.nonEmpty))
      group.map(_.trim).filter(_.nonEmpty) match
        case None => c.sources(f).map { r =>
          println(if brief then Markdown.briefResults(r) else Markdown.searchResults(r))
        }
        case Some(by) =>
          Tree.parseBy(by) match
            case None => Left(CliError(s"unknown --group '$by' (${GroupAxes.mkString(" | ")})", exitCode = 1))
            case Some(axes) =>
              c.aggregate(Api.AggregateQuery.tree(axes.mkString(","), f, unit, depth, asOf, samples))
                .map(h => println(Markdown.tree(h, unit.getOrElse("sources"))))
    }
  }

  /** The `--group` vocabulary, spelled once (the server parses the same set through `Tree.parseBy`). */
  private val facets = Opts.subcommand("facets", s"Vocabulary of a dimension (${FacetField.all.mkString("|")}) with distinct-source counts") {
    (
      Opts.argument[String]("field"),
      Opts.option[String]("prefix", "Case-insensitive prefix match").orNone,
      Opts.argument[String]("query").orNone,
      FilterFlags.opts
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


  private val get = Opts.subcommand("get", "Show one source as Markdown (section: summary|findings|notes|toc|body|all)") {
    (
      Opts.argument[String]("citekey"),
      Opts.flag("full", "Include full text").orFalse,
      Opts.option[String]("section", s"Section to return: ${SourceSection.all.mkString("|")} (default: summary+findings+notes)").orNone,
      Opts.option[String]("heading", "Return body chunks under this heading (case-insensitive prefix)").orNone,
      Opts.option[Int]("offset", "Start the body at this character offset (0-based)").orNone,
      Opts.option[Int]("chars", "Return at most this many characters of the body").orNone
    ).mapN { (ck, full, section, heading, offset, chars) => (c: Client) =>
      if section.isDefined || heading.isDefined || offset.isDefined || chars.isDefined then
        c.sourceView(Api.SourceViewQuery(ck, full, section, heading, offset, chars)).map(println)
      else
        c.getSource(ck).map(sw => println(Markdown.source(sw.source, sw.findings.toVector, full)))
    }
  }

  /** The map feed: individual findings, filtered by the SAME flags as `search` (`--tag`, `--place`,
    * `--from/--to`, `--bbox`, …). `--region` is kept as an alias of `--place`. */
  private val geoWhen = Opts.subcommand("geo-when", "Findings (the map feed) by place, deep time, bbox — the same filters as `search`") {
    (Opts.option[String]("region", "Region-name substring (alias of --place)").orNone, FilterFlags.opts)
      .mapN { (region, f) => (c: Client) =>
        c.findings(Api.FindingsQuery(f, region)).map(hits => println(Markdown.geoResults(hits.toVector)))
      }
  }

  private val histogram = Opts.subcommand("histogram", "When is the evidence? Sources per adaptive time bucket (Markdown table + bar chart), for the same filters as search") {
    (
      Opts.argument[String]("query").orNone,
      Opts.option[String]("region", "Region-name substring (alias of --place)").orNone,
      Opts.option[String]("unit", "sources (default) | findings").orNone,
      FilterFlags.opts
    ).mapN { (q, region, unit, f0) => (c: Client) =>
      val f = f0.copy(q = q.map(_.trim).filter(_.nonEmpty), places = f0.places ++ region.toList)
      c.aggregate(Api.AggregateQuery.time(f, unit))
        .map(h => println(Markdown.histogram(h, unit.getOrElse("sources"))))
    }
  }

  // ── backup / restore (plan §1.10) ───────────────────────────────────────────────────────────
  // The dump is taken by the SERVER (it holds the credentials, §2.5); `--download` then streams the
  // finished archive back here, which is how a dump on a remote server reaches this machine.

  private val backup = Opts.subcommand("backup", "Take a pg_dump backup of the server's database (optionally copy it here)") {
    Opts.option[String]("download", "Also stream the finished dump into this directory").orNone
      .map { into => (c: Client) =>
        c.backup.flatMap(s => awaitBackup(c, s)).flatMap { r =>
          println(Markdown.backup(r))
          into match
            case None => Right(())
            case Some(d) =>
              // the name comes back over the wire, so it is validated here too before it becomes a
              // path on THIS machine — the same gate the server applies to it
              Backup.safeName(r.file).left.map(m => CliError(s"server returned an odd file name: $m", exitCode = 2))
                .flatMap { name =>
                  val dir = Path.of(d)
                  val _   = Files.createDirectories(dir)
                  c.downloadBackup(name, dir.resolve(name).toFile)
                    .map(f => println(s"downloaded → ${f.getAbsolutePath}"))
                }
        }
      }
  }

  /** The server runs a dump as a job and answers `running` when it outlasts the request; poll its
    * status until the archive is written (or the job failed), printing progress on stderr. */
  @scala.annotation.tailrec
  private[cli] def awaitBackup(c: Client, s: BackupStatus, pollMs: Long = 5000): Either[CliError, BackupResult] =
    s.stateOf match
      case Some(BackupState.Running) =>
        System.err.println(s"backup ${s.file} running for ${s.durationMs / 1000} s…")
        Thread.sleep(pollMs)
        c.backupStatus match
          case Right(next) => awaitBackup(c, next, pollMs)
          case Left(e)     => Left(e)
      case Some(BackupState.Done) =>
        s.result.toRight(CliError(s"backup ${s.file} finished without a checksum", exitCode = 1))
      case _ => Left(CliError(s"backup ${s.file} failed: ${s.error.getOrElse("unknown error")}", exitCode = 1))

  private val backups = Opts.subcommand("backups", "List the backups on the server, newest first") {
    Opts.unit.map(_ => (c: Client) => c.backups.map(rows => println(Markdown.backups(rows.toVector))))
  }

  private val restore = Opts.subcommand("restore", "Restore a server-side backup (pg_restore --clean); refuses a non-empty database without --force") {
    (
      Opts.argument[String]("file"),
      Opts.flag("force", "Overwrite a database that still holds sources").orFalse,
      Opts.flag("yes", "Do not ask for confirmation").orFalse
    ).mapN { (file, force, yes) => (c: Client) =>
      if !yes && !confirm(s"Restore '$file' into the server's database? This DROPS what is there.")
      then Left(CliError("aborted", exitCode = 1))
      else c.restore(RestoreRequest(file, force)).map(r => println(Markdown.restored(r)))
    }
  }

  /** Ask on the terminal. A closed/piped stdin reads as null → treated as "no", so a scripted
    * `histmersion restore` can never destroy a database by accident; use `--yes` for that. */
  private[cli] def confirm(question: String): Boolean =
    Console.err.print(s"$question [y/N] ")
    Console.err.flush()
    Option(scala.io.StdIn.readLine()).map(_.trim.toLowerCase).exists(a => a == "y" || a == "yes")

  private val neighbors = Opts.subcommand("neighbors", "Typed references to/from a source (references, challenges, refutes, updates, superseded_by)") {
    (
      Opts.argument[String]("citekey"),
      Opts.options[String]("kind", "Filter by edge kind (repeatable)").orEmpty
    ).mapN { (ck, kinds) => (c: Client) =>
      c.neighbors(ck, kinds).map(rows => println(Markdown.neighbors(ck, rows.toVector)))
    }
  }

  private val graphCmd = Opts.subcommand("graph",
    "Source dependency graph: BFS traversal returning D3-ready nodes + links") {
    (
      Opts.argument[String]("citekey"),
      Opts.option[Int]("depth", "BFS depth 0–4 (default 2)").orNone
    ).mapN { (ck, depth) => (c: Client) =>
      c.graph(ck, depth).map(g => println(Markdown.graph(g)))
    }
  }

  private val contradictionsCmd = Opts.subcommand("contradictions",
    "Disagreement edges (challenges, refutes, updates, superseded_by) and contested/superseded/debunked/retracted statuses") {
    (
      Opts.argument[String]("citekey").orNone,
      Opts.option[Long]("since", "Only show contradictions from citing papers published from this year on").orNone
    ).mapN { (ck, since) => (c: Client) =>
      c.contradictions(ck, since).map(r => println(Markdown.contradictions(r, ck)))
    }
  }

  private val command: Command[(Option[String], Option[String], Client => Either[CliError, Unit])] =
    Command(name = "histmersion", header = "Histmersion — CLI over the Histmersion server (MCP twin)") {
      (serverOpt, tokenOpt,
        version orElse health orElse about orElse seed orElse reindex orElse search orElse facets orElse get orElse geoWhen
          orElse histogram orElse neighbors orElse graphCmd orElse contradictionsCmd orElse put orElse patch
          orElse RenameCmd.command orElse exportCmd
          orElse backup orElse backups orElse restore orElse BundleCmd.command orElse MergeCmd.command orElse ArticleCli.command orElse PlacesCmd.command
          orElse PeriodsCmd.command orElse PeriodsCmd.init
          orElse EventsCmd.command orElse AttestationsCmd.command
          orElse GoalsCmd.command orElse AgentStateCmd.command orElse AgentCmd.command
          orElse MediaCmd.command orElse AssetLinksCmd.command
          // stream 2.8 — lint · queue · read-only query
          orElse Ops.lint orElse Ops.queue orElse Ops.query orElse Ops.schema
          // stream 2.6: discover / refs / assess / followups / ingest / archive, and host-side calibre
          orElse AcquireCmd.all orElse CalibreCmd.all
          // stream 2.4 — OCR that keeps the figures, as a job (`ingest <file>` lives in AcquireCmd,
          // which owns the name and dispatches file-vs-DOI)
          orElse IngestCmd.all).tupled
    }

  def main(args: Array[String]): Unit =
    command.parse(args.toList, sys.env) match
      case Left(help) =>
        // A8: --help should exit 0, not 1. Decline's Help has an `errors` field — empty means
        // the user asked for help, non-empty means a parse error.
        val isHelp = args.contains("--help") || args.contains("-h")
        if isHelp then { println(help); sys.exit(0) }
        else { System.err.println(help); sys.exit(1) }
      case Right((server, tok, run)) =>
        val cfg = AppConfig.load()
        val url = server.getOrElse(cfg.cli.serverUrl)
        val token = tok.orElse(Option(System.getenv("HISTMERSION_TOKEN")).filter(_.nonEmpty))
        run(Client(url, token)) match
          case Right(()) => ()
          case Left(e)   => System.err.println(s"error: ${e.message}"); sys.exit(e.exitCode)
