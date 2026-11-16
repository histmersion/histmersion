package com.histmersion
package cli
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.problem.{Legacy, Problem, Problems}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.api.library.Tree

import com.histmersion.api.library.SearchMarkdown
import com.histmersion.api.library.SourcesMarkdown

import scala.annotation.tailrec
import hearth.kindlings.optics.syntax.*
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

  private def report(r: SeedResult)(using console: Console): Unit =
    out.println(
      s"imported ${r.imported}, skipped ${r.skipped.size} (newer in DB), " +
        s"failed ${r.failed.size}, edges unified ${r.edgesResolved}"
    )
    r.skipped.take(10).foreach(s => out.println(s"  ~ $s"))
    r.failed.take(10).foreach(f => err.println(s"  ! $f"))

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
                      c.putAssets(Citekey(ck), fs)
                        .left
                        .toOption
                        .map(e => s"$ck (figures): ${e.problems.sentences.mkString("; ")}")
              loop(
                tail,
                number + 1,
                imported + r.imported.value,
                failed ++ r.failed.map(_.value) ++ figureFailure,
                skipped ++ r.skipped.map(_.value)
              )
            case Left(e) =>
              loop(tail, number + 1, imported, failed :+ s"$ck: ${e.problems.sentences.mkString("; ")}", skipped)
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

  /** The directory a kind's notes go in under the export's directory, as the mirror's layout places them. */
  private def placed(out: Path, placement: MirrorPlacement): Path =
    placement.directory.fold(out)(d => out.resolve(d.value))

  /** Delete `<sources>/<alias>.md` for every old citekey (V027). Best effort: a server without the alias endpoint, or a
    * file that cannot be removed, is reported and does not fail the export.
    */
  private def pruneRenamed(c: Client, sources: Path)(using console: Console): Unit =
    c.sourceAliases match
      case Left(e)   => err.println(s"  ! could not list renamed citekeys: ${e.problems.sentences.mkString("; ")}")
      case Right(as) =>
        val (failed, removed) = RenameCmd.pruneRenamed(sources, as).partitionMap(identity)
        failed.foreach(f => err.println(s"  ! $f"))
        if removed.nonEmpty then
          out.println(s"removed ${removed.size} note(s) of renamed citekeys: ${removed.mkString(", ")}")

  // Declared before `search`, which reads it during object initialisation.
  private[cli] val GroupAxes = List("time", "region", "time,region", "region,time")

  /** `--group` / `--depth` / `--as-of` / `--samples` (plan §1.6): the SAME filter, rendered as the nested tree instead
    * of a flat list — WHEN and WHERE the evidence sits, before reading any of it.
    */

  /** The `--group` vocabulary, spelled once (the server parses the same set through `Tree.parseBy`). */

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

  /** `grep` inside one source's body, server-side (#954): the passages around each match, each with the offset
    * `get --offset` reads on from.
    */

  /** The map feed: individual findings, filtered by the SAME flags as `search` (`--tag`, `--place`, `--from/--to`,
    * `--bbox`, …). `--region` is kept as an alias of `--place`.
    */

  // ── backup / restore (plan §1.10) ───────────────────────────────────────────────────────────
  // The dump is taken by the SERVER (it holds the credentials, §2.5); `--download` then streams the
  // finished archive back here, which is how a dump on a remote server reaches this machine.

  /** Ask on the terminal. A closed/piped stdin reads as null → treated as "no", so a scripted `histmersion restore` can
    * never destroy a database by accident; use `--yes` for that.
    */
  private[cli] def confirm(question: Question)(using console: Console): Boolean =
    err.print(s"$question [y/N] ")
    err.flush()
    Option(console.readLine()).map(_.value.trim.toLowerCase).exists(a => a == "y" || a == "yes")

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

  /** The exit code is decided here, at the edge. */
  private def runCommand(args: Array[String])(using console: Console): Unit =
    CommandLine.parse(args.toSeq) match
      case CommandLine.Outcome.Help(text)             => out.println(text); sys.exit(0)
      case CommandLine.Outcome.Failed(problems, help) =>
        err.println(s"${problems.sentences.map(s => s"error: $s").mkString("\n")}\n\n$help");
        sys.exit(ExitCode.Usage.value)
      case CommandLine.Outcome.Run(server, tok, cmd) =>
        finish(server, tok, cmd.run)

  private def finish(server: Option[Client.ServerUrl], tok: Option[Client.Token], run: Client => Either[Error, Unit])(
      using console: Console
  ): Unit =
    val url = server.getOrElse(Client.ServerUrl(Settings.app.cli.serverUrl.value))
    val token = tok.orElse(Option(System.getenv("HISTMERSION_TOKEN")).filter(_.nonEmpty).map(Client.Token(_)))
    run(new Client(url, token)) match
      case Right(())                     => ()
      case Left(e) if e.workingDirDenied => err.println(WorkingDir.message); sys.exit(e.exitCode.value)
      case Left(e)                       =>
        e.problems.sentences.foreach(s => err.println(s"error: $s")); sys.exit(e.exitCode.value)

  /** A user-facing failure: message + process exit code (1 = usage, 2 = server said no, 3 = unreachable). */
  final case class Error(
      problems: Problems,
      exitCode: ExitCode = ExitCode.Unreachable,
      notFound: Boolean = false,
      workingDirDenied: Boolean = false
  )

  object Error:
    type ExitCode = com.histmersion.cli.ExitCode
    val ExitCode: com.histmersion.cli.ExitCode.type = com.histmersion.cli.ExitCode

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum TopCommand extends Command:
    case Vacuum(analyze: Boolean, tables: List[String], noWait: Boolean)
    case Version()
    case Health()
    case About(json: JsonOutput)
    case Seed(dir: String, force: Boolean)
    case Reindex()
    case Search(
        q: Option[String],
        group: Option[String],
        depth: Option[Int],
        asOf: Option[Long],
        samples: Option[Int],
        unit: Option[String],
        brief: Boolean,
        f0: Filter,
        json: JsonOutput
    )
    case Facets(
        field: String,
        prefix: Option[String],
        q: Option[String],
        f: Filter,
        json: JsonOutput
    )
    case Get(
        ck: String,
        full: Boolean,
        section: Option[String],
        heading: Option[String],
        offset: Option[Int],
        chars: Option[Int],
        json: JsonOutput
    )
    case Passages(ck: String, q: String, regex: Boolean, limit: Option[Int], context: Option[Int])
    case GeoWhen(region: Option[String], f: Filter, json: JsonOutput)
    case Histogram(
        q: Option[String],
        region: Option[String],
        unit: Option[String],
        f0: Filter,
        json: JsonOutput
    )
    case Neighbors(ck: String, kinds: List[String], json: JsonOutput)
    case Graph(ck: String, depth: Option[Int], json: JsonOutput)
    case Contradictions(ck: Option[String], since: Option[Long], json: JsonOutput)
    case Put(file: String)
    case Patch(
        ck: String,
        json: Option[String],
        tAdd: Option[String],
        tRem: Option[String],
        tRep: Option[String],
        tops: Option[String],
        regs: Option[String],
        summary: Option[String],
        status: Option[Source.Status],
        title: Option[String],
        notesFile: Option[String],
        period: Option[String],
        bodyFile: Option[String],
        addF: List[String],
        remF: List[String],
        ifUpd: Option[String],
        addE: List[String],
        remE: List[String],
        strict: Boolean
    )
    case Export(
        dir: String,
        only: List[String],
        srcDir: Option[String],
        artDir: Option[String],
        metaDir: Option[String],
        noMeta: Boolean
    )
    case Backup(into: Option[String])
    case Backups(json: JsonOutput)
    case Restore(file: String, force: Boolean, yes: Boolean)

    def run(c: Client)(using Console): Either[Error, Unit] = PerformTop(c)(this)

  /** Carries out TopCommand commands against one server. */
  final private[cli] class PerformTop(c: Client)(using console: Console):
    def apply(cmd: TopCommand): Either[Error, Unit] =
      cmd match
        case TopCommand.Version() =>
          out.println(s"cli    ${Version.name} ${Version.version}")
          c.version.map(v => out.println(s"server ${v.name} ${v.version}"))
        case TopCommand.Health() =>
          c.health.map(h =>
            out.println(
              (s"${h.status} · db ${h.db} · schema ${h.schemaVersion.getOrElse("?")}" ::
                h.warnings.map(w => s"warning: $w")).mkString("\n")
            )
          )
        case TopCommand.About(json) =>
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
        case TopCommand.Seed(dir, force) =>
          Option(dir).filter(_.nonEmpty).map(Path.of(_)).filter(Files.isDirectory(_)) match
            case Some(p) => hostSeed(c, p, Api.Overwrite(force))
            case None    =>
              c.seed(SeedRequest(SeedRequest.Dir(dir), SeedRequest.Force(force)))
                .map(report) // server-side (bind-mounted) corpus
        case TopCommand.Reindex() =>
          reindexLoop(c, None, 0, 0)
        case TopCommand.Search(q, group, depth, asOf, samples, unit, brief, f0, json) =>
          val f = f0.modify(_.q).setTo(q.map(_.trim).filter(_.nonEmpty).map(Filter.Q(_)))
          group.map(_.trim).filter(_.nonEmpty) match
            case None =>
              c.sources(f).map { r =>
                // the result says where its page starts, so the footer's `next: offset=` is right on every page
                Output.emit(json, r)(r =>
                  if brief then SearchMarkdown.briefResults(r) else SearchMarkdown.searchResults(r)
                )
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
        case TopCommand.Facets(field, prefix, q, f, json) =>
          c.facets(
            Api.Facets.Field(field),
            prefix.map(Api.Facets.Prefix(_)),
            f.modify(_.q).setTo(q.map(_.trim).filter(_.nonEmpty).map(Filter.Q(_)))
          ).map(rows => Output.emit(json, rows)(rs => SearchMarkdown.facets(Facet.FieldText(field), rs.toVector)))
        case TopCommand.Get(ck, full, section, heading, offset, chars, json) =>
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
        case TopCommand.Passages(ck, q, regex, limit, context) =>
          c.passages(
            Api.PassagesQuery(
              Citekey(ck),
              Api.PassagesQuery.Q(q),
              Api.PassagesQuery.Regex(regex),
              limit.map(Api.PassagesQuery.Limit(_)),
              context.map(Api.PassagesQuery.Context(_))
            )
          ).map(r => out.println(PassagesMarkdown.render(r)))
        case TopCommand.GeoWhen(region, f, json) =>
          c.findingsPage(Api.FindingsQuery(f, region.map(Api.FindingsQuery.Region(_))))
            .map(p => Output.emit(json, p)(SearchMarkdown.geoPage))
        case TopCommand.Histogram(q, region, unit, f0, json) =>
          val f = f0
            .modify(_.q)
            .setTo(q.map(_.trim).filter(_.nonEmpty).map(Filter.Q(_)))
            .modify(_.places)
            .setTo(f0.places ++ region.toList.map(Filter.Places(_)))
          c.aggregate(Api.AggregateQuery.time(f, unit.map(Api.AggregateQuery.TimeUnit(_))))
            .map(h => Output.emit(json, h)(SearchMarkdown.histogram(_, Histogram.Unit(unit.getOrElse("sources")))))
        case TopCommand.Neighbors(ck, kinds, json) =>
          c.neighbors(Citekey(ck), kinds.map(Edge.Kind.Wire(_)))
            .map(rows => Output.emit(json, rows)(rs => SourcesMarkdown.neighbors(Citekey(ck), rs.toVector)))
        case TopCommand.Graph(ck, depth, json) =>
          c.graph(Citekey(ck), depth.map(Source.Graph.DepthCount(_)))
            .map(g => Output.emit(json, g)(SourcesMarkdown.graph))
        case TopCommand.Contradictions(ck, since, json) =>
          val key = ck.map(Citekey(_))
          c.contradictions(key, since.map(Api.AggregateQuery.AsOf(_)))
            .map(r => Output.emit(json, r)(SourcesMarkdown.contradictions(_, key)))
        case TopCommand.Put(file) =>
          val p = Path.of(file)
          if !Files.isRegularFile(p) then
            Left(Error(Problem.Unclassified(Legacy.Text(s"no such file: $file")), exitCode = ExitCode.Usage))
          // an explicit `put` is always deliberate → force past the "newer in DB" guard
          else c.importNote(Notes.toImport(p, force = Api.Overwrite(true))).map(report)
        case TopCommand.Patch(
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
            ) =>
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
        case TopCommand.Export(dir, only, srcDir, artDir, metaDir, noMeta) =>
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
                case Left(e) => Some(s"$ck: ${e.problems.sentences.mkString("; ")}")
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
        case TopCommand.Backup(into) =>
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
        case TopCommand.Backups(json) =>
          c.backups.map(rows => Output.emit(json, rows)(rs => Markdown.backups(rs.toVector)))
        case TopCommand.Restore(file, force, yes) =>
          if !yes && !confirm(Question(s"Restore '$file' into the server's database? This DROPS what is there."))
          then Left(Error(Problem.Unclassified(Legacy.Text("aborted")), exitCode = ExitCode.Usage))
          else
            c.restore(RestoreRequest(RestoreRequest.File(file), RestoreRequest.Force(force)))
              .map(r => out.println(Markdown.restored(r)))
        case TopCommand.Vacuum(analyze, tables, noWait) =>
          c.vacuum(Vacuum.Request(Vacuum.Request.Analyze(analyze), tables.map(Vacuum.Request.Tables(_)))).flatMap { j =>
            out.println(
              s"vacuum job `${j.id}` started: ${j.detail.flatMap(VacuumPlan.Detail.of).map(Vacuum.describe).getOrElse("")} " +
                "(`histmersion jobs` lists it)"
            )
            if noWait then Right(()) else IngestCmd.pollJob(c, j.id, 0)
          }
