package com.histmersion
package cli
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.problem.Text.sentence

import com.histmersion.api.library.Tree

import com.histmersion.api.library.SearchMarkdown
import com.histmersion.api.library.SourcesMarkdown

import scala.annotation.tailrec
import hearth.kindlings.optics.syntax.*
import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.{Api, Markdown}
import com.histmersion.config.*

import java.nio.file.{Files, Path}
import com.histmersion.util.data.mirror.MirrorPlacement
import com.histmersion.api.backup.{Backup, RestoreRequest}
import com.histmersion.api.checks.FormatMarkdown
import com.histmersion.api.instance.{AboutMarkdown, Version}
import com.histmersion.api.library.{PassagesMarkdown, SeedRequest, SeedResult}
import com.histmersion.api.maintenance.{Vacuum, VacuumPlan}
import com.histmersion.api.library.{Citekey, Edge}
import com.histmersion.api.library.Histogram
import com.histmersion.api.library.Source
import com.histmersion.api.library.Facet
import sttp.client4.*
import com.histmersion.util.console.Console
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.ByteCount
import com.histmersion.api.Paging
import com.histmersion.api.library.Title
import com.histmersion.api.library.Filter

/** Histmersion CLI — a pure HTTP client of the server (no DB access on the host). Every command maps to one `Api`
  * endpoint and prints the shared Markdown rendering, so CLI and MCP output agree.
  */
object Cli:
  type DryRun = DryRun.T
  object DryRun extends Newtype[Boolean, Pure]
  type JsonOutput = JsonOutput.T
  object JsonOutput extends Newtype[Boolean, Pure]
  type NoWait = NoWait.T
  object NoWait extends Newtype[Boolean, Pure]
  type Question = Question.T
  object Question extends Newtype[String, Pure]
  type QuietOutput = QuietOutput.T
  object QuietOutput extends Newtype[Boolean, Pure]
  type Strict = Strict.T
  object Strict extends Newtype[Boolean, Pure]

  /** A command the command line asked for, every argument already a typed value ([[CommandLine]] builds it): each
    * command group's ADT extends it and says how it runs against a server.
    */
  trait Command:
    def run(c: Client)(using Console): Either[Error, Unit]

  def out(using console: Console): Console.Output = console.out
  def err(using console: Console): Console.Output = console.err

  private val serverOpt =
    Opts
      .option[String]("server", "Histmersion server URL (default: config cli.serverUrl / $HISTMERSION_SERVER_URL)")
      .orNone
  private val tokenOpt =
    Opts.option[String]("token", "API key for authenticated servers (or $HISTMERSION_TOKEN)").orNone

  private def version(using console: Console) = Opts.subcommand("version", "Print the CLI and server versions") {
    Opts.unit.map(_ =>
      (c: Client) =>
        out.println(s"cli    ${Version.name} ${Version.version}")
        c.version.map(v => out.println(s"server ${v.name} ${v.version}"))
    )
  }

  private def health(using console: Console) = Opts.subcommand("health", "Server health (db, schema version)") {
    Opts.unit.map(_ =>
      (c: Client) =>
        c.health.map(h =>
          out.println(
            (s"${h.status} · db ${h.db} · schema ${h.schemaVersion.getOrElse("?")}" ::
              h.warnings.map(w => s"warning: $w")).mkString("\n")
          )
        )
    )
  }

  private def about(using console: Console) = Opts.subcommand(
    "about",
    "Server build (version, commit), health, job queue, last backup, store counts and storage on disk"
  ) {
    Output.jsonFlag.map(json =>
      (c: Client) =>
        c.about.map { a =>
          val exe = scala.util
            .Try(ProcessHandle.current().info().command().orElse(null))
            .toOption
            .flatMap(Option(_))
            .map(java.nio.file.Path.of(_))
          val withLocal = LocalStorage.add(
            a,
            c.serverUrl,
            Settings.app,
            k => Option(java.lang.System.getenv(k.value)).map(Environment.VariablesValue(_)),
            UserPaths.OsName(java.lang.System.getProperty("os.name", "")),
            exe
          )
          Output.emit(json, withLocal)(AboutMarkdown.render)
        }
    )
  }

  private def report(r: SeedResult)(using console: Console): Unit =
    out.println(
      s"imported ${r.imported}, skipped ${r.skipped.size} (newer in DB), " +
        s"failed ${r.failed.size}, edges unified ${r.edgesResolved}"
    )
    r.skipped.take(10).foreach(s => out.println(s"  ~ $s"))
    r.failed.take(10).foreach(f => err.println(s"  ! $f"))

  private def seed(using console: Console) = Opts.subcommand(
    "seed",
    "Import a wiki/ or wiki/sources directory (articles beside it come too) — read on the HOST when that path exists here, otherwise asked of the SERVER (empty = configured corpus dir)"
  ) {
    (Opts.argument[String]("dir").withDefault(""), Opts.flag("force", "Overwrite rows newer than the files").orFalse)
      .mapN { (dir, force) => (c: Client) =>
        Option(dir).filter(_.nonEmpty).map(Path.of(_)).filter(Files.isDirectory(_)) match
          case Some(p) => hostSeed(c, p, Api.Overwrite(force))
          case None    =>
            c.seed(SeedRequest(SeedRequest.Dir(dir), SeedRequest.Force(force)))
              .map(report) // server-side (bind-mounted) corpus
      }
  }

  /** Seed from a directory on THIS machine: the CLI reads each note and posts its text, so a corpus the container
    * cannot see still gets in. Progress is printed per file; the edge-unification pass a server-side seed does at the
    * end is requested explicitly once every note is stored.
    *
    * `dir` may be the source-note directory (`wiki/sources`) or the wiki ROOT (`wiki`, which holds `sources/` plus the
    * article directories) — see [[ArticleCli.WikiLayout]]. Whenever the layout has a root, the compiled articles beside
    * `sources/` are imported too (plan §5.2 stream 2.7).
    */
  private def hostSeed(c: Client, dir: Path, force: Api.Overwrite)(using
      console: Console
  ): Either[Error, Unit] =
    val layout = ArticleCli.WikiLayout.of(dir)
    val files = Notes.markdownFiles(layout.sources)
    val total = files.size
    out.println(s"seeding $total note(s) from ${layout.sources}")
    @tailrec def loop(
        rest: List[Path],
        number: Int,
        imported: Int,
        failed: List[String],
        skipped: List[String]
    )(using console: Console): SeedResult =
      rest match
        case Nil =>
          SeedResult(
            SeedResult.Imported(imported),
            failed.map(SeedResult.Failed(_)),
            SeedResult.EdgesResolved(0),
            skipped.map(SeedResult.Skipped(_))
          )
        case f :: tail =>
          val ck = f.getFileName.toString.stripSuffix(".md")
          out.println(s"$number/$total $ck")
          c.importNote(Notes.toImport(f, force)) match
            case Right(r) =>
              // the server cannot see the host's `md/<ck>/figures`, so the CLI uploads them (plan §1.8)
              val figureFailure =
                if r.imported.value == 0 then None
                else
                  Notes.figures(f, Citekey(ck), FigureBytes, FigureCount) match
                    case Nil => None
                    case fs  =>
                      c.putAssets(Citekey(ck), fs).left.toOption.map(e => s"$ck (figures): ${e.message.sentence}")
              loop(
                tail,
                number + 1,
                imported + r.imported.value,
                failed ++ r.failed.map(_.value) ++ figureFailure,
                skipped ++ r.skipped.map(_.value)
              )
            case Left(e) => loop(tail, number + 1, imported, failed :+ s"$ck: ${e.message.sentence}", skipped)
    val acc = loop(files, 1, 0, Nil, Nil)
    val done =
      c.resolveEdges.map(e => report(acc.modify(_.edgesResolved).setTo(SeedResult.EdgesResolved(e.resolved.value))))
    layout.root.fold(done)(root =>
      done.map(_ => ArticleCli.report(ArticleCli.importAll(c, root, force, Set(ArticleCli.WikiLayout.SourcesDir))))
    )

  /** Host-side caps on what `seed` uploads per note. Deliberately conservative: the SERVER enforces its own
    * `limits.asset-bytes`, this only keeps one request from becoming enormous.
    */
  private val FigureBytes = ByteCount(8L * 1024 * 1024)
  private val FigureCount = Paging.Limit(200)

  private def put(using console: Console) =
    Opts.subcommand("put", "Send ONE note file (frontmatter + body) as a full create/replace") {
      Opts.argument[String]("file.md").map { file => (c: Client) =>
        val p = Path.of(file)
        if !Files.isRegularFile(p) then
          Left(Error(Problem.Unclassified(Legacy.Text(s"no such file: $file")), exitCode = ExitCode.Usage))
        // an explicit `put` is always deliberate → force past the "newer in DB" guard
        else c.importNote(Notes.toImport(p, force = Api.Overwrite(true))).map(report)
      }
    }

  private def exportCmd(using console: Console) = Opts.subcommand(
    "export",
    "Write sources to <dir>/sources/, articles to <dir>/<kind>/, bookkeeping to <dir>/meta/ — the seed layout"
  ) {
    (
      Opts.argument[String]("dir"),
      Opts.arguments[String]("citekey").orEmpty,
      Opts.option[String]("sources-dir", "Where source notes go (default: <dir>/sources)").orNone,
      Opts.option[String]("articles-dir", "Where article notes go (default: <dir>)").orNone,
      Opts.option[String]("meta-dir", "Where ACQUIRE.md/TASKS.md/LOG.md/GOALS.md go (default: <dir>/meta)").orNone,
      Opts.flag("no-meta", "Do not write the bookkeeping queues and the research goals").orFalse
    ).mapN { (dir, only, srcDir, artDir, metaDir, noMeta) => (c: Client) =>
      val out = Path.of(dir)
      val sources = srcDir.map(Path.of(_)).getOrElse(placed(out, MirrorPlacement.SourceFiles))
      val articles = artDir.map(Path.of(_)).getOrElse(placed(out, MirrorPlacement.ArticleFiles))
      val _ = Files.createDirectories(sources)
      (if only.nonEmpty then Right(only.map(Citekey(_))) else c.citekeys).flatMap { ks =>
        val total = ks.size
        val failed = ks.zipWithIndex.flatMap { (ck, i) =>
          Cli.out.println(s"${i + 1}/$total $ck")
          c.exportSource(ck) match
            case Right(text) =>
              val _ = Files.writeString(
                sources.resolve(MirrorPlacement.noteFile(MirrorPlacement.RecordId(ck.value)).value),
                text.value
              )
              None
            case Left(e) => Some(s"$ck: ${e.message.sentence}")
        }
        Cli.out.println(s"exported ${total - failed.size} note(s) to $sources, failed ${failed.size}")
        // a renamed source's note would otherwise linger under its OLD citekey — and a later
        // `seed` of the mirror would try to bring the old name back — so a full export removes it
        if only.isEmpty then pruneRenamed(c, sources)
        failed.take(10).foreach(f => err.println(s"  ! $f"))
        // a partial export must not look like success — the mirror on disk would be silently stale
        // the compiled wiki (2.7), the bookkeeping queues (2.8) and the goals are part of the same mirror on disk
        val meta =
          if noMeta then Right(())
          else
            Ops
              .writeMeta(c, metaDir.map(Path.of(_)).getOrElse(placed(out, MirrorPlacement.MetaFiles)))
              .map(fs => Cli.out.println(s"wrote ${fs.mkString(", ")}"))
        meta.flatMap { _ =>
          if failed.nonEmpty then
            Left(
              Error(
                Problem.Unclassified(Legacy.Text(s"${failed.size} of $total source(s) could not be exported")),
                exitCode = ExitCode.Refused
              )
            )
          else if only.nonEmpty then Right(())
          else ArticleCli.exportAll(c, articles).map(_ => ())
        }
      }
    }
  }

  /** The directory a kind's notes go in under the export's directory, as the mirror's layout places them. */
  private def placed(out: Path, placement: MirrorPlacement): Path =
    placement.directory.fold(out)(d => out.resolve(d.value))

  /** Delete `<sources>/<alias>.md` for every old citekey (V027). Best effort: a server without the alias endpoint, or a
    * file that cannot be removed, is reported and does not fail the export.
    */
  private def pruneRenamed(c: Client, sources: Path)(using console: Console): Unit =
    c.sourceAliases match
      case Left(e)   => err.println(s"  ! could not list renamed citekeys: ${e.message.sentence}")
      case Right(as) =>
        val (failed, removed) = RenameCmd.pruneRenamed(sources, as).partitionMap(identity)
        failed.foreach(f => err.println(s"  ! $f"))
        if removed.nonEmpty then
          out.println(s"removed ${removed.size} note(s) of renamed citekeys: ${removed.mkString(", ")}")

  private def patch(using console: Console) = Opts.subcommand(
    "patch",
    "Selectively enrich one source — only the parts you pass are changed"
  ) {
    (
      Opts.argument[String]("citekey"),
      Opts.option[String]("json", "A whole SourcePatch as JSON (the flags below override its parts)").orNone,
      Opts.option[String]("tags-add", "Comma-separated tags to add").orNone,
      Opts.option[String]("tags-remove", "Comma-separated tags to remove").orNone,
      Opts.option[String]("tags-replace", "Comma-separated tags replacing the current set").orNone,
      Opts.option[String]("topics", "Comma-separated topics replacing the current list").orNone,
      Opts.option[String]("regions", "Comma-separated regions replacing the current list").orNone,
      Opts.option[String]("summary", "New summary").orNone,
      Opts
        .option[String]("status", Source.Status.values.map(_.wire).mkString("|"))
        .mapValidated(v =>
          Source.Status.values
            .find(_.wire.value == v.trim.toLowerCase)
            .toValidNel(s"unknown status '$v' (one of: ${Source.Status.values.map(_.wire).mkString(", ")})")
        )
        .orNone,
      Opts.option[String]("title", "New title").orNone,
      Opts.option[String]("notes-file", "File whose Markdown becomes this source's notes article").orNone,
      Opts.option[String]("period", "start,end as signed years, BCE negative (e.g. -3300,-2600)").orNone,
      Opts
        .option[String]("body-file", "File whose text REPLACES the converted body (e.g. a chapter cut out of a volume)")
        .orNone,
      Opts.options[String]("add-finding", "A FindingInput as JSON (repeatable)").orEmpty,
      Opts.options[String]("remove-finding", "Finding id to drop, e.g. 'haak2015_massive#2' (repeatable)").orEmpty,
      Opts
        .option[String]("if-updated-at", "Precondition: the updatedAt you last read (fails when the row moved on)")
        .orNone,
      Opts
        .options[String](
          "add-edge",
          s"A typed edge FROM this source, <kind>:<citekey>, e.g. challenges:lord1968_oralpoet (repeatable; kinds: ${Edge.Kind.allWire.mkString(", ")})"
        )
        .orEmpty,
      Opts.options[String]("remove-edge", "An edge FROM this source to drop, <kind>:<citekey> (repeatable)").orEmpty,
      Opts.flag("strict", "Refuse the patch when its summary or notes have a formatting error").orFalse
    ).mapN {
      (
          ck,
          json,
          tAdd,
          tRem,
          tRep,
          tops,
          regs,
          summary,
          status,
          title,
          notesFile,
          period,
          bodyFile,
          addF,
          remF,
          ifUpd,
          addE,
          remE,
          strict
      ) => (c: Client) =>
        Patches
          .build(
            json,
            tAdd,
            tRem,
            tRep,
            tops,
            regs,
            summary.map(Source.Summary(_)),
            status,
            title.map(Title(_)),
            notesFile.map(Patches.NotesFile(_)),
            period,
            bodyFile.map(Patches.BodyFile(_)),
            addF,
            remF,
            ifUpd.map(Source.UpdatedAt(_)),
            addE.toList,
            remE.toList
          )
          .flatMap(p =>
            c.patchSource(Citekey(ck), p, Strict(strict)).map { sw =>
              out.println(
                SourcesMarkdown.source(sw.source, sw.findings.toVector, false) + FormatMarkdown.notice(sw.formatting)
              )
              sw.source.updatedAt.foreach(u => out.println(s"\nupdatedAt: $u"))
            }
          )
    }
  }

  // Declared before `search`, which reads it during object initialisation.
  private val GroupAxes = List("time", "region", "time,region", "region,time")

  /** `--group` / `--depth` / `--as-of` / `--samples` (plan §1.6): the SAME filter, rendered as the nested tree instead
    * of a flat list — WHEN and WHERE the evidence sits, before reading any of it.
    */
  private def search(using console: Console) = Opts.subcommand(
    "search",
    "Search the corpus (websearch syntax: \"phrase\", -not, or): sources with the findings that matched"
  ) {
    (
      Opts.argument[String]("query").orNone,
      Opts
        .option[String]("group", s"Group the results into a tree instead of listing them: ${GroupAxes.mkString(" | ")}")
        .orNone,
      Opts.option[Int]("depth", s"Levels of nesting for --group (1-${Tree.MaxDepth})").orNone,
      Opts
        .option[Long](
          "as-of",
          "Signed year the gazetteer hierarchy is read at for --group region (default: the middle of the window)"
        )
        .orNone,
      Opts.option[Int]("samples", s"Sample sources listed under each leaf of the tree (0-${Tree.MaxSamples})").orNone,
      Opts.option[String]("unit", "What --group counts: sources (default) | findings").orNone,
      Opts.flag("brief", "Compact listing: citekey, title, year, status, period, one why-line").orFalse,
      FilterFlags.opts,
      Output.jsonFlag
    ).mapN { (q, group, depth, asOf, samples, unit, brief, f0, json) => (c: Client) =>
      val f = f0.modify(_.q).setTo(q.map(_.trim).filter(_.nonEmpty).map(Filter.Q(_)))
      group.map(_.trim).filter(_.nonEmpty) match
        case None =>
          c.sources(f).map { r =>
            // the result says where its page starts, so the footer's `next: offset=` is right on every page
            Output.emit(json, r)(r => if brief then SearchMarkdown.briefResults(r) else SearchMarkdown.searchResults(r))
          }
        case Some(by) =>
          Tree.parseBy(by) match
            case None =>
              Left(
                Error(
                  Problem.Unclassified(Legacy.Text(s"unknown --group '$by' (${GroupAxes.mkString(" | ")})")),
                  exitCode = ExitCode.Usage
                )
              )
            case Some(axes) =>
              c.aggregate(
                Api.AggregateQuery.tree(
                  Api.AggregateQuery.By(axes.mkString(",")),
                  f,
                  unit.map(Api.AggregateQuery.TreeUnit(_)),
                  depth.map(Api.AggregateQuery.Depth(_)),
                  asOf.map(Api.AggregateQuery.TreeAsOf(_)),
                  samples.map(Api.AggregateQuery.TreeSamples(_))
                )
              ).map(h =>
                Output.emit(json, h)(SearchMarkdown.tree(_, SearchMarkdown.TreeUnit(unit.getOrElse("sources"))))
              )
    }
  }

  /** The `--group` vocabulary, spelled once (the server parses the same set through `Tree.parseBy`). */
  private def facets(using console: Console) = Opts.subcommand(
    "facets",
    s"Vocabulary of a dimension (${Facet.Field.all.mkString("|")}) with distinct-source counts"
  ) {
    (
      Opts.argument[String]("field"),
      Opts.option[String]("prefix", "Case-insensitive prefix match").orNone,
      Opts.argument[String]("query").orNone,
      FilterFlags.opts,
      Output.jsonFlag
    ).mapN { (field, prefix, q, f, json) => (c: Client) =>
      c.facets(
        Api.Facets.Field(field),
        prefix.map(Api.Facets.Prefix(_)),
        f.modify(_.q).setTo(q.map(_.trim).filter(_.nonEmpty).map(Filter.Q(_)))
      ).map(rows => Output.emit(json, rows)(rs => SearchMarkdown.facets(Facet.FieldText(field), rs.toVector)))
    }
  }

  /** The server re-chunks a bounded slice per call (a whole 89 MB corpus takes minutes — far past the HTTP timeout), so
    * the CLI drives the cursor and reports progress until nothing is left.
    */
  @annotation.tailrec
  private def reindexLoop(
      c: Client,
      after: Option[Api.Reindex.Scope],
      sources: Int,
      chunks: Int
  )(using console: Console): Either[Error, Unit] =
    c.reindex(after, None) match
      case Left(e)  => Left(e)
      case Right(r) =>
        val (s, ch) = (sources + r.sources.value, chunks + r.chunks.value)
        if r.remaining.value > 0 && r.lastCitekey.isDefined then
          err.print(s"\rreindexing… $s source(s), $ch chunk(s), ${r.remaining} to go")
          reindexLoop(c, r.lastCitekey.map(k => Api.Reindex.Scope(k.value)), s, ch)
        else
          if sources > 0 then err.print("\r" + " " * 60 + "\r")
          out.println(s"reindexed $s source(s) → $ch chunk(s)")
          Right(())

  private def reindex(using console: Console) =
    Opts.subcommand("reindex", "Rebuild the full-text chunk index for every source") {
      Opts.unit.map(_ => (c: Client) => reindexLoop(c, None, 0, 0))
    }

  private def get(using console: Console) =
    Opts.subcommand("get", "Show one source as Markdown (section: summary|findings|notes|toc|body|all)") {
      (
        Opts.argument[String]("citekey"),
        Opts.flag("full", "Include full text").orFalse,
        Opts
          .option[String](
            "section",
            s"Section to return: ${Source.Section.all.mkString("|")} (default: summary+findings+notes)"
          )
          .orNone,
        Opts.option[String]("heading", "Return body chunks under this heading (case-insensitive prefix)").orNone,
        Opts.option[Int]("offset", "Start the body at this character offset (0-based)").orNone,
        Opts.option[Int]("chars", "Return at most this many characters of the body").orNone,
        Output.jsonFlag
      ).mapN { (ck, full, section, heading, offset, chars, json) => (c: Client) =>
        if section.isDefined || heading.isDefined || offset.isDefined || chars.isDefined then
          if json.value then
            Left(
              Error(
                Problem.Unclassified(
                  Legacy.Text("--json prints the whole record; drop --section/--heading/--offset/--chars")
                ),
                exitCode = ExitCode.Usage
              )
            )
          else
            c.sourceView(
              Api.SourceViewQuery(
                Citekey(ck),
                Api.SourceViewQuery.FullText(full),
                section.map(Api.SourceViewQuery.Section(_)),
                heading.map(Api.SourceViewQuery.Heading(_)),
                offset.map(Api.SourceViewQuery.Offset(_)),
                chars.map(Api.SourceViewQuery.Chars(_))
              )
            ).map(value => out.println(value))
        else
          c.getSource(Citekey(ck))
            .map(sw =>
              Output.emit(json, if full then sw else sw.modify(_.source.ocrMarkdown).setTo(None))(sw =>
                SourcesMarkdown.source(sw.source, sw.findings.toVector, full)
              )
            )
      }
    }

  /** `grep` inside one source's body, server-side (#954): the passages around each match, each with the offset
    * `get --offset` reads on from.
    */
  private def passages(using console: Console) = Opts.subcommand(
    "passages",
    "Find a word, phrase or regex inside one source's full text: the passages around each match, with offsets for get --offset"
  ) {
    (
      Opts.argument[String]("citekey"),
      Opts.argument[String]("query"),
      Opts
        .flag("regex", "Treat the query as a Java regular expression (default: literal, case- and accent-insensitive)")
        .orFalse,
      Opts.option[Int]("limit", "Show at most this many passages (default 10, max 50)").orNone,
      Opts.option[Int]("context", "Characters of context around each match (default 600, max 3000)").orNone
    ).mapN { (ck, q, regex, limit, context) => (c: Client) =>
      c.passages(
        Api.PassagesQuery(
          Citekey(ck),
          Api.PassagesQuery.Q(q),
          Api.PassagesQuery.Regex(regex),
          limit.map(Api.PassagesQuery.Limit(_)),
          context.map(Api.PassagesQuery.Context(_))
        )
      ).map(r => out.println(PassagesMarkdown.render(r)))
    }
  }

  /** The map feed: individual findings, filtered by the SAME flags as `search` (`--tag`, `--place`, `--from/--to`,
    * `--bbox`, …). `--region` is kept as an alias of `--place`.
    */
  private def geoWhen(using console: Console) =
    Opts.subcommand("geo-when", "Findings (the map feed) by place, deep time, bbox — the same filters as `search`") {
      (
        Opts.option[String]("region", "Region-name substring (alias of --place)").orNone,
        FilterFlags.opts,
        Output.jsonFlag
      )
        .mapN { (region, f, json) => (c: Client) =>
          c.findingsPage(Api.FindingsQuery(f, region.map(Api.FindingsQuery.Region(_))))
            .map(p => Output.emit(json, p)(SearchMarkdown.geoPage))
        }
    }

  private def histogram(using console: Console) = Opts.subcommand(
    "histogram",
    "When is the evidence? Sources per adaptive time bucket (Markdown table + bar chart), for the same filters as search"
  ) {
    (
      Opts.argument[String]("query").orNone,
      Opts.option[String]("region", "Region-name substring (alias of --place)").orNone,
      Opts.option[String]("unit", "sources (default) | findings").orNone,
      FilterFlags.opts,
      Output.jsonFlag
    ).mapN { (q, region, unit, f0, json) => (c: Client) =>
      val f = f0
        .modify(_.q)
        .setTo(q.map(_.trim).filter(_.nonEmpty).map(Filter.Q(_)))
        .modify(_.places)
        .setTo(f0.places ++ region.toList.map(Filter.Places(_)))
      c.aggregate(Api.AggregateQuery.time(f, unit.map(Api.AggregateQuery.TimeUnit(_))))
        .map(h => Output.emit(json, h)(SearchMarkdown.histogram(_, Histogram.Unit(unit.getOrElse("sources")))))
    }
  }

  // ── backup / restore (plan §1.10) ───────────────────────────────────────────────────────────
  // The dump is taken by the SERVER (it holds the credentials, §2.5); `--download` then streams the
  // finished archive back here, which is how a dump on a remote server reaches this machine.

  private def backup(using console: Console) =
    Opts.subcommand("backup", "Take a pg_dump backup of the server's database (optionally copy it here)") {
      Opts
        .option[String]("download", "Also stream the finished dump into this directory")
        .orNone
        .map { into => (c: Client) =>
          c.backup.flatMap(s => JobFollow.backup(() => c.backupStatus, s)).flatMap { r =>
            out.println(Markdown.backup(r))
            into match
              case None    => Right(())
              case Some(d) =>
                // the name comes back over the wire, so it is validated here too before it becomes a
                // path on THIS machine — the same gate the server applies to it
                Backup
                  .safeName(Backup.Name(r.file.value))
                  .left
                  .map(m =>
                    Error(
                      Problem.Unclassified(Legacy.Text(s"server returned an odd file name: ${m.sentence}")),
                      exitCode = ExitCode.Refused
                    )
                  )
                  .flatMap { name =>
                    val dir = Path.of(d)
                    val _ = Files.createDirectories(dir)
                    c.downloadBackup(Backup.Name(name.value), dir.resolve(name.value).toFile)
                      .map(f => out.println(s"downloaded → ${f.getAbsolutePath}"))
                  }
          }
        }
    }

  private def backups(using console: Console) =
    Opts.subcommand("backups", "List the backups on the server, newest first") {
      Output.jsonFlag.map(json =>
        (c: Client) => c.backups.map(rows => Output.emit(json, rows)(rs => Markdown.backups(rs.toVector)))
      )
    }

  private def restore(using console: Console) = Opts.subcommand(
    "restore",
    "Restore a server-side backup (pg_restore --clean); refuses a non-empty database without --force"
  ) {
    (
      Opts.argument[String]("file"),
      Opts.flag("force", "Overwrite a database that still holds sources").orFalse,
      Opts.flag("yes", "Do not ask for confirmation").orFalse
    ).mapN { (file, force, yes) => (c: Client) =>
      if !yes && !confirm(Question(s"Restore '$file' into the server's database? This DROPS what is there."))
      then Left(Error(Problem.Unclassified(Legacy.Text("aborted")), exitCode = ExitCode.Usage))
      else
        c.restore(RestoreRequest(RestoreRequest.File(file), RestoreRequest.Force(force)))
          .map(r => out.println(Markdown.restored(r)))
    }
  }

  /** Ask on the terminal. A closed/piped stdin reads as null → treated as "no", so a scripted `histmersion restore` can
    * never destroy a database by accident; use `--yes` for that.
    */
  private[cli] def confirm(question: Question)(using console: Console): Boolean =
    err.print(s"$question [y/N] ")
    err.flush()
    Option(console.readLine()).map(_.value.trim.toLowerCase).exists(a => a == "y" || a == "yes")

  private def neighbors(using console: Console) = Opts.subcommand(
    "neighbors",
    "Typed references to/from a source (references, challenges, refutes, updates, superseded_by)"
  ) {
    (
      Opts.argument[String]("citekey"),
      Opts.options[String]("kind", "Filter by edge kind (repeatable)").orEmpty,
      Output.jsonFlag
    ).mapN { (ck, kinds, json) => (c: Client) =>
      c.neighbors(Citekey(ck), kinds.map(Edge.Kind.Wire(_)))
        .map(rows => Output.emit(json, rows)(rs => SourcesMarkdown.neighbors(Citekey(ck), rs.toVector)))
    }
  }

  private def graphCmd(using console: Console) =
    Opts.subcommand("graph", "Source dependency graph: BFS traversal returning D3-ready nodes + links") {
      (
        Opts.argument[String]("citekey"),
        Opts.option[Int]("depth", "BFS depth 0–4 (default 2)").orNone,
        Output.jsonFlag
      ).mapN { (ck, depth, json) => (c: Client) =>
        c.graph(Citekey(ck), depth.map(Source.Graph.DepthCount(_)))
          .map(g => Output.emit(json, g)(SourcesMarkdown.graph))
      }
    }

  private def contradictionsCmd(using console: Console) = Opts.subcommand(
    "contradictions",
    "Disagreement edges (challenges, refutes, updates, superseded_by) and contested/superseded/debunked/retracted statuses"
  ) {
    (
      Opts.argument[String]("citekey").orNone,
      Opts.option[Long]("since", "Only show contradictions from citing papers published from this year on").orNone,
      Output.jsonFlag
    ).mapN { (ck, since, json) => (c: Client) =>
      val key = ck.map(Citekey(_))
      c.contradictions(key, since.map(Api.AggregateQuery.AsOf(_)))
        .map(r => Output.emit(json, r)(SourcesMarkdown.contradictions(_, key)))
    }
  }

  /** `histmersion maintenance vacuum` (#1445) — VACUUM on the server, as a job. */
  private def maintenanceCmd(using console: Console) =
    Opts.subcommand("maintenance", "Database upkeep on the server: vacuum") {
      Opts.subcommand(
        "vacuum",
        "VACUUM the tables a re-seed rewrites (or --table …) as a server job, outside any transaction; --analyze also refreshes planner statistics"
      ) {
        (
          Opts.flag("analyze", "VACUUM (ANALYZE): also refresh the planner's statistics").orFalse,
          Opts
            .options[String]("table", "A table to vacuum (repeatable; default: config maintenance.vacuum-tables)")
            .orEmpty,
          Opts.flag("no-wait", "Print the job id and return instead of following it").orFalse
        )
          .mapN { (analyze, tables, noWait) => (c: Client) =>
            c.vacuum(Vacuum.Request(Vacuum.Request.Analyze(analyze), tables.map(Vacuum.Request.Tables(_)))).flatMap {
              j =>
                out.println(
                  s"vacuum job `${j.id}` started: ${j.detail.flatMap(VacuumPlan.Detail.of).map(Vacuum.describe).getOrElse("")} " +
                    "(`histmersion jobs` lists it)"
                )
                if noWait then Right(()) else IngestCmd.pollJob(c, j.id, 0)
            }
          }
      }
    }

  private[cli] def command(using
      console: Console
  ): com.monovore.decline.Command[(Option[String], Option[String], Client => Either[Error, Unit])] =
    com.monovore.decline
      .Command(name = "histmersion", header = "Histmersion — CLI over the Histmersion server (MCP twin)") {
        (
          serverOpt,
          tokenOpt,
          version orElse health orElse about orElse seed orElse reindex orElse search orElse facets orElse get orElse passages orElse geoWhen
            orElse histogram orElse neighbors orElse graphCmd orElse contradictionsCmd orElse put orElse patch
            orElse migrated("rename") orElse migrated("delete") orElse exportCmd
            orElse backup orElse backups orElse restore orElse maintenanceCmd orElse migrated("bundle") orElse migrated(
              "merge"
            ) orElse migrated("article") orElse migrated("places")
            orElse migrated("periods") orElse migrated("init")
            orElse migrated("events") orElse migrated("attestations") orElse migrated("candidates")
            orElse migrated("goals") orElse migrated("agent-state") orElse AgentCmd.command
            orElse migrated("media") orElse migrated("asset-links")
            // stream 2.8 — lint · queue · read-only query
            orElse migrated("lint") orElse migrated("queue") orElse migrated("query") orElse migrated("schema")
            // stream 2.6: discover / refs / assess / followups / ingest / archive, and host-side calibre
            orElse migrated("discover") orElse migrated("refs") orElse migrated("assess") orElse migrated(
              "followups"
            ) orElse migrated("ingest") orElse migrated("archive") orElse migrated("calibre")
            // stream 2.4 — OCR that keeps the figures, as a job (`ingest <file>` lives in AcquireCmd,
            // which owns the name and dispatches file-vs-DOI)
            orElse migrated("reocr") orElse migrated("assets") orElse migrated("jobs") orElse migrated("activity")
            orElse migrated("bookmarks") orElse migrated("notes")
            orElse migrated("config")
            // the terrain base map's extracts on the server's machine
            orElse migrated("basemap")
            // the plugins of the install on THIS machine (docs/research-plugins.md §7): files + the relink
            orElse migrated("plugin")
        ).tupled
      }

  /** A top-level word [[CommandLine]] parses now: decline only lists it, so the root's help keeps its place. */
  private def migrated(name: String) =
    Opts.subcommand(name, CommandLine.helpOf(name))(Opts.unit.map(_ => (_: Client) => Right(())))

  def run(args: Array[String])(using console: Console): Unit =
    // #901: a working directory macOS will not let this binary read fails on the first `user.dir`
    // read with a bare stack trace. Only the commands that resolve a local path read it (`ingest
    // <file>`, `export <dir>`, `config path`…) — `search`, `calibre gaps` and the rest never do, so it
    // is NOT probed up front: a command that needs it says what to do instead of the trace
    try runCommand(args)
    catch
      case t: Throwable if WorkingDir.denied(t) =>
        err.println(WorkingDir.message)
        sys.exit(3)

  private def runCommand(args: Array[String])(using console: Console): Unit =
    if CommandLine.handles(args.toSeq) then runParsed(CommandLine.parse(args.toSeq)) else runDeclined(args)

  /** A command line [[CommandLine]] (scallop) parsed: the exit code is decided here, at the edge. */
  private def runParsed(outcome: CommandLine.Outcome)(using console: Console): Unit =
    outcome match
      case CommandLine.Outcome.Help(text)            => out.println(text); sys.exit(0)
      case CommandLine.Outcome.Failed(problem, help) =>
        err.println(s"error: ${problem.sentence}\n\n$help"); sys.exit(ExitCode.Usage.value)
      case CommandLine.Outcome.Run(server, tok, cmd) =>
        finish(server.map(_.value), tok.map(_.value), cmd.run)

  private def runDeclined(args: Array[String])(using console: Console): Unit =
    command.parse(args.toList, sys.env) match
      case Left(help) =>
        // A8: --help should exit 0, not 1. Decline's Help has an `errors` field — empty means
        // the user asked for help, non-empty means a parse error.
        val isHelp = args.contains("--help") || args.contains("-h")
        if isHelp then { out.println(help); sys.exit(0) }
        else { err.println(help.toString); sys.exit(1) }
      case Right((server, tok, run)) => finish(server, tok, run)

  private def finish(server: Option[String], tok: Option[String], run: Client => Either[Error, Unit])(using
      console: Console
  ): Unit =
    val url = server.getOrElse(Settings.app.cli.serverUrl.value)
    val token = tok.orElse(Option(System.getenv("HISTMERSION_TOKEN")).filter(_.nonEmpty))
    run(new Client(Client.ServerUrl(url), token.map(Client.Token(_)))) match
      case Right(())                     => ()
      case Left(e) if e.workingDirDenied => err.println(WorkingDir.message); sys.exit(e.exitCode.value)
      case Left(e)                       => err.println(s"error: ${e.message.sentence}"); sys.exit(e.exitCode.value)

  /** A user-facing failure: message + process exit code (1 = usage, 2 = server said no, 3 = unreachable). */
  final case class Error(
      message: Problem,
      exitCode: ExitCode = ExitCode.Unreachable,
      notFound: Boolean = false,
      workingDirDenied: Boolean = false
  )

  object Error:
    type ExitCode = com.histmersion.cli.ExitCode
    val ExitCode: com.histmersion.cli.ExitCode.type = com.histmersion.cli.ExitCode
