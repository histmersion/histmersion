package com.histmersion.cli

import hearth.kindlings.optics.syntax.*

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*
import com.histmersion.api.automation.Queue.{QueueAdd, QueueImport, QueuePatch}

import java.nio.file.{Files, Path}
import com.histmersion.api.automation.{GoalsExport, Queue}
import com.histmersion.api.checks.{FormatCheck, FormatField, FormatMarkdown, FormatReport, Lint}
import com.histmersion.api.library.{AcquireChecksMarkdown, Query}
import com.histmersion.api.library.Acquire
import com.histmersion.util.console.Console

/** The CLI commands of stream 2.8 — `lint`, `queue`, `query`, `schema` — plus the pure argument parsing they need. Like
  * every other command they are HTTP clients of the server; the host never touches Postgres.
  *
  * `queue` deliberately keeps the grammar the plan specifies (`queue acquire add "…"`, `queue tasks`, `queue done 3`,
  * `queue export <dir>`), which does not fit decline's subcommand-per-verb shape — so the positional arguments are
  * dispatched by [[QueueArgs.parse]], a pure function with its own tests, rather than by a nest of subcommands.
  */
object Ops:

  // ── queue argument grammar (pure) ────────────────────────────────────────────────────────────

  /** What `histmersion queue …` was asked to do. */
  type WriteMetaResultPart2 = WriteMetaResultPart2.T
  object WriteMetaResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadMetaResultPart2 = ReadMetaResultPart2.T
  object ReadMetaResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadMetaReplace = ReadMetaReplace.T
  object ReadMetaReplace extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixLoopResultPart2 = FixLoopResultPart2.T
  object FixLoopResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixLoopRules = FixLoopRules.T
  object FixLoopRules extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixLoopSources = FixLoopSources.T
  object FixLoopSources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixLoopChunks = FixLoopChunks.T
  object FixLoopChunks extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixLoopEdges = FixLoopEdges.T
  object FixLoopEdges extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixLoopMoved = FixLoopMoved.T
  object FixLoopMoved extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixLoopPlaced = FixLoopPlaced.T
  object FixLoopPlaced extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixLoopPlaces = FixLoopPlaces.T
  object FixLoopPlaces extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixLoopAreas = FixLoopAreas.T
  object FixLoopAreas extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LintFileFix = LintFileFix.T
  object LintFileFix extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunJson = RunJson.T
  object RunJson extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum QueueCmd:
    case Show(list: QueueCmd.Show.List, all: QueueCmd.Show.All)
    case Add(list: QueueCmd.Add.List, text: QueueCmd.Add.Text, topic: Option[QueueCmd.Add.Topic], reason: Option[QueueCmd.Add.Reason])
    case Done(id: QueueCmd.Done.Id)
    case Edit(id: QueueCmd.Edit.Id, text: QueueCmd.Edit.Text)
    case Rm(id: QueueCmd.Rm.Id)
    case Export(dir: QueueCmd.Export.Dir)
    case Import(dir: QueueCmd.Import.Dir)

    /** `queue acquire recheck`: the acquisition list's legal re-check now — of every open item (`all`) or the due ones
      * the caps allow; a pass of more than one item is a server job, followed unless `noWait`.
      */
    case Recheck(dryRun: QueueCmd.Recheck.DryRun, limit: Option[QueueCmd.Recheck.Limit], id: Option[QueueCmd.Recheck.Id], all: QueueCmd.Recheck.All = false, noWait: QueueCmd.Recheck.NoWait = false)

  object QueueCmd:
    object Show:
      type List = List.T
      object List extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type All = All.T
      object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Add:
      type List = List.T
      object List extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Text = Text.T
      object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Topic = Topic.T
      object Topic extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Reason = Reason.T
      object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Done:
      type Id = Id.T
      object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Edit:
      type Id = Id.T
      object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Text = Text.T
      object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Rm:
      type Id = Id.T
      object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Export:
      type Dir = Dir.T
      object Dir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Import:
      type Dir = Dir.T
      object Dir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Recheck:
      type DryRun = DryRun.T
      object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Limit = Limit.T
      object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Id = Id.T
      object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type All = All.T
      object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type NoWait = NoWait.T
      object NoWait extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object QueueArgs:
    type Usage = Usage.T
    object Usage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseResultPart1 = ParseResultPart1.T
    object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseArgs = ParseArgs.T
    object ParseArgs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseTopic = ParseTopic.T
    object ParseTopic extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseReason = ParseReason.T
    object ParseReason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseAll = ParseAll.T
    object ParseAll extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseDryRun = ParseDryRun.T
    object ParseDryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseLimit = ParseLimit.T
    object ParseLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseItem = ParseItem.T
    object ParseItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseNoWait = ParseNoWait.T
    object ParseNoWait extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val usage: com.histmersion.cli.Ops.QueueArgs.Usage =
      "usage: histmersion queue <acquire|tasks|log> [--all] | queue <list> add \"<text>\" [--topic t] | " +
        "queue done <id> | queue edit <id> \"<text>\" | queue rm <id> | queue export <dir> | queue import <dir> | " +
        "queue acquire recheck [--all] [--dry-run] [--limit n] [--id <item>] [--no-wait]"

    /** Pure: positional words + flags → a command, or the usage line. `--dry-run`, `--limit`, `--id` and `--no-wait`
      * belong to `acquire recheck` alone; `--all` there means every open item.
      */
    def parse(
        args: List[com.histmersion.cli.Ops.QueueArgs.ParseArgs],
        topic: Option[com.histmersion.cli.Ops.QueueArgs.ParseTopic],
        reason: Option[com.histmersion.cli.Ops.QueueArgs.ParseReason],
        all: com.histmersion.cli.Ops.QueueArgs.ParseAll,
        dryRun: com.histmersion.cli.Ops.QueueArgs.ParseDryRun = false,
        limit: Option[com.histmersion.cli.Ops.QueueArgs.ParseLimit] = None,
        item: Option[com.histmersion.cli.Ops.QueueArgs.ParseItem] = None,
        noWait: com.histmersion.cli.Ops.QueueArgs.ParseNoWait = false
    ): Either[com.histmersion.cli.Ops.QueueArgs.ParseResultPart1, QueueCmd] =
      def id(s: String): Either[String, Int] =
        s.toIntOption.toRight(s"'$s' is not an item id — $usage")
      val recheckFlags = dryRun || limit.isDefined || item.isDefined || noWait
      args match
        case "acquire" :: "recheck" :: Nil =>
          if limit.exists(_ < 0) then Left(s"--limit is 0 or more — $usage")
          else if all && item.isDefined then
            Left(s"--all re-checks every open item, --id one of them — not both — $usage")
          else if all && limit.isDefined then
            Left(s"--all re-checks every open item, past every cap; --limit caps a pass — not both — $usage")
          else Right(QueueCmd.Recheck(dryRun, limit, item, all, noWait))
        case _ if recheckFlags =>
          Left(s"--dry-run, --limit, --id and --no-wait go with `queue acquire recheck` — $usage")
        case "done" :: i :: Nil         => id(i).map(QueueCmd.Done.apply)
        case "edit" :: i :: text :: Nil =>
          if text.trim.isEmpty then Left(s"edit needs the new text — $usage") else id(i).map(QueueCmd.Edit(_, text))
        case "edit" :: _            => Left(s"edit takes an item id and the new text — $usage")
        case "rm" :: i :: Nil       => id(i).map(QueueCmd.Rm.apply)
        case "export" :: dir :: Nil => Right(QueueCmd.Export(dir))
        case "import" :: dir :: Nil => Right(QueueCmd.Import(dir))
        case list :: rest if Queue.Kind.parse(list).isDefined =>
          rest match
            case Nil                  => Right(QueueCmd.Show(list, all))
            case "add" :: text :: Nil => Right(QueueCmd.Add(list, text, topic, reason))
            case "add" :: Nil         => Left(s"add needs the item text — $usage")
            case _                    => Left(usage)
        case list :: _ => Left(s"unknown list or verb '$list' — $usage")
        case Nil       => Left(usage)

  // ── the meta files on disk ────────────────────────────────────────────────────────────────────────

  /** Write `ACQUIRE.md` / `TASKS.md` / `LOG.md` and `GOALS.md` into `dir`, rendered by the server from the rows. Used
    * by `queue export` and by `histmersion export`, so both produce identical files.
    */
  def writeMeta(c: Client, dir: Path): Either[Cli.Error, List[com.histmersion.cli.Ops.WriteMetaResultPart2]] =
    val _ = Files.createDirectories(dir)
    val files: List[(String, () => Either[Cli.Error, String])] =
      Queue.Kind.values.toList.map(k => k.file -> (() => c.queueExport(k.label))) :+
        (GoalsExport.File -> (() => c.goalsExport))
    files.foldLeft[Either[Cli.Error, List[String]]](Right(Nil)) { case (acc, (file, fetch)) =>
      acc.flatMap { done =>
        fetch().map { text =>
          val _ = Files.writeString(dir.resolve(file), text)
          done :+ file
        }
      }
    }

  /** Read the three queue files back. A missing file is skipped, not an error: a workspace may only keep some of them.
    * `GOALS.md` is never read: it is an export only (see [[GoalsExport]]).
    */
  def readMeta(c: Client, dir: Path, replace: com.histmersion.cli.Ops.ReadMetaReplace): Either[Cli.Error, List[com.histmersion.cli.Ops.ReadMetaResultPart2]] =
    if !Files.isDirectory(dir) then Left(Cli.Error(s"not a directory: $dir", exitCode = ExitCode.Usage))
    else
      Queue.Kind.values.toList.foldLeft[Either[Cli.Error, List[String]]](Right(Nil)) { (acc, kind) =>
        acc.flatMap { done =>
          val f = dir.resolve(kind.file)
          if !Files.isRegularFile(f) then Right(done :+ s"${kind.file}: absent, skipped")
          else
            c.queueImport(kind.label, QueueImport(Files.readString(f), replace))
              .map(r => done :+ s"${kind.file}: ${r.items} item(s), ${r.notes} note block(s), ${r.replaced} replaced")
        }
      }

  // ── commands ─────────────────────────────────────────────────────────────────────────────────

  /** `lint --fix` applies the server-side repairs and then loops while sources remain to re-chunk (or point-only
    * imported areas to remove), because a whole-corpus pass is far longer than one HTTP request.
    */
  @annotation.tailrec
  private def fixLoop(using
      console: Console
  )(
      c: Client,
      rules: List[com.histmersion.cli.Ops.FixLoopRules],
      sources: com.histmersion.cli.Ops.FixLoopSources,
      chunks: com.histmersion.cli.Ops.FixLoopChunks,
      edges: com.histmersion.cli.Ops.FixLoopEdges,
      moved: Option[com.histmersion.cli.Ops.FixLoopMoved] = None,
      placed: Option[com.histmersion.cli.Ops.FixLoopPlaced] = None,
      places: Option[List[com.histmersion.cli.Ops.FixLoopPlaces]] = None,
      areas: com.histmersion.cli.Ops.FixLoopAreas = 0
  ): Either[Cli.Error, List[com.histmersion.cli.Ops.FixLoopResultPart2]] =
    c.lintFix(rules) match
      case Left(e)  => Left(e)
      case Right(r) =>
        val (s, ch, ed) = (sources + r.sourcesReindexed, chunks + r.chunksWritten, edges + r.edgesResolved)
        // the geocode repair is not bounded: the first call made every move there was
        val mv =
          moved.orElse(Option.when(rules.exists(_.trim.equalsIgnoreCase(Lint.FindingGeocode)))(r.findingsRegeocoded))
        // …and neither is the fill of unplaced findings
        val pl =
          placed.orElse(Option.when(rules.exists(_.trim.equalsIgnoreCase(Lint.FindingUnplaced)))(r.findingsPlaced))
        // …nor the gazetteer clean-up (place-kind, place-grid-point): its lines are the first call's
        val pc = places.orElse(
          Some(r.applied.filter(l => l.startsWith(Lint.PlaceKindRule + ":") || l.startsWith(Lint.PlaceGridPoint + ":")))
        )
        val ar = areas + r.pointAreasRemoved
        val asksAreas = rules.exists(_.trim.equalsIgnoreCase(Lint.PlaceImportedPointArea))
        // a call that removed nothing while some remain is not progress (they are being changed
        // under it): stop rather than loop
        val moreAreas = asksAreas && r.pointAreasRemaining > 0 && r.pointAreasRemoved > 0
        if r.remaining > 0 || moreAreas then
          Cli.err.print(if moreAreas then s"\rfixing… $ar point-only area(s) removed, ${r.pointAreasRemaining} to go"
          else s"\rfixing… $s source(s) reindexed, ${r.remaining} to go")
          fixLoop(c, rules, s, ch, ed, mv, pl, pc, ar)
        else
          if sources > 0 || ar > 0 then Cli.err.print("\r" + " " * 70 + "\r")
          Right(
            List(s"resolve-edges: $ed edge target(s) rewritten", s"reindex: $s source(s) → $ch chunk(s)") ++
              mv.map(n => s"${Lint.FindingGeocode}: $n finding(s) moved to the place the current geocoder chooses")
                .toList ++
              pl.map(n => s"${Lint.FindingUnplaced}: $n finding(s) given the point of the place they name").toList ++
              pc.getOrElse(Nil) ++
              Option
                .when(asksAreas)(
                  s"${Lint.PlaceImportedPointArea}: $ar point-only imported area(s) removed" +
                    (if r.pointAreasRemaining > 0 then
                       s", ${r.pointAreasRemaining} left (named by evidence, or changing — run again)"
                     else "")
                )
                .toList
          )

  /** `lint --file x.md`: the server checks the text (it has the renderer); with `--fix` the repaired text is written
    * back over the file. A note file's frontmatter is the note's own and is not sent.
    */
  private def lintFile(c: Client, p: java.nio.file.Path, fix: com.histmersion.cli.Ops.LintFileFix): Either[Cli.Error, FormatReport] =
    if !java.nio.file.Files.isRegularFile(p) then Left(Cli.Error(s"no such file: $p", exitCode = ExitCode.Usage))
    else
      val raw = java.nio.file.Files.readString(p)
      val (head, body) = FileFormat.splitFrontmatter(raw)
      c.lintMarkdown(FormatCheck(body, FormatField.Body, fix)).map { r =>
        r.text.filter(t => fix && t != body).foreach(t => java.nio.file.Files.writeString(p, head + t))
        r.modify(_.subject).setTo(p.getFileName.toString).modify(_.text).setTo(None)
      }

  def lint(using console: Console): Opts[Client => Either[Cli.Error, Unit]] =
    Opts.subcommand("lint", s"Health-check the corpus (${Lint.all.size} rules: ${Lint.names.mkString(", ")})") {
      (
        Opts.options[String]("rule", "Run only this rule (repeatable)").orEmpty,
        Opts
          .flag(
            "fix",
            "First apply the safe repairs: resolve short edge targets, reindex stale chunks; with --rule finding-geocode also move the findings it lists, with --rule finding-unplaced give a point to the findings it lists, with --rule place-kind reclassify and merge the places it lists, with --rule place-grid-point move the places it lists, with --rule place-imported-point-area remove the places it lists (with --article/--source/--file: the formatting repairs)"
          )
          .orFalse,
        Opts.option[String]("article", "Check only this article's formatting (a slug)").orNone,
        Opts
          .option[String]("source", "Check only this source's summary and notes for formatting problems (a citekey)")
          .orNone,
        Opts
          .option[String](
            "file",
            "Check a local Markdown file's formatting before it is stored (--fix rewrites the file)"
          )
          .orNone,
        Output.jsonFlag
      ).mapN { (rules, fix, article, source, file, json) => (c: Client) =>
        def show(r: FormatReport)(using console: Console) = Output.emit(json, r)(FormatMarkdown.report)
        (article, source, file) match
          case (Some(slug), _, _) => (if fix then c.fixArticleFormat(slug) else c.lintArticle(slug)).map(show)
          case (_, Some(ck), _)   => (if fix then c.fixSourceFormat(ck) else c.lintSource(ck)).map(show)
          case (_, _, Some(f))    => lintFile(c, java.nio.file.Path.of(f), fix).map(show)
          case _                  =>
            for
              fixed <- if fix then fixLoop(c, rules, 0, 0, 0) else Right(Nil)
              rep <- c.lint(rules)
            yield
              val full = rep.modify(_.fixed).setTo(fixed)
              Output.emit(json, full)(Lint.markdown)
      }
    }

  def query(using console: Console): Opts[Client => Either[Cli.Error, Unit]] =
    Opts.subcommand("query", "Run ONE read-only SQL statement (a write is refused by Postgres itself)") {
      (Opts.argument[String]("sql"), Output.jsonFlag).mapN { (sql, json) => (c: Client) =>
        c.query(sql).map(r => Output.emit(json, r)(Query.markdown))
      }
    }

  def schema(using console: Console): Opts[Client => Either[Cli.Error, Unit]] =
    Opts.subcommand("schema", "List the tables and columns `query` can read (from information_schema)") {
      Output.jsonFlag.map { json => (c: Client) =>
        c.schemaTables.map(s => Output.emit(json, s)(Query.schemaMarkdown))
      }
    }

  def queue(using console: Console): Opts[Client => Either[Cli.Error, Unit]] =
    // NB: no `|` in the description — `tools/gen-cli-docs.sh` pastes it into a Markdown TABLE cell.
    // The full grammar (which needs pipes to read well) is `QueueArgs.usage`, printed on a bad call.
    Opts.subcommand(
      "queue",
      "Bookkeeping queues: acquire / tasks / log — show, add, done, edit, rm, export (with the goals as GOALS.md), import; acquire recheck: the legal re-check of the acquisition list (more than one item, or --all for every item past the caps, as a server job it follows)"
    ) {
      (
        Opts.arguments[String]("args").orEmpty,
        Opts.option[String]("topic", "Group the added item under this heading").orNone,
        Opts.option[String]("reason", "Why this item is on the list / what research direction it serves").orNone,
        Opts
          .flag(
            "all",
            "Include done items and the file's prose blocks; acquire recheck: every open item with a DOI, due or not, past the caps (a server job)"
          )
          .orFalse,
        Opts
          .flag("dry-run", "acquire recheck: list what it would close and try; no fetcher is asked, nothing stored")
          .orFalse,
        Opts
          .option[Int]("limit", "acquire recheck: try at most this many due items (default acquire.recheck.per-pass)")
          .orNone,
        Opts.option[Int]("id", "acquire recheck: this acquisition item only, due or not").orNone,
        Opts.flag("no-wait", "acquire recheck: print the job id and return instead of following the job").orFalse,
        Output.jsonFlag
      ).mapN { (args, topic, reason, all, dryRun, limit, item, noWait, json) => (c: Client) =>
        QueueArgs.parse(args, topic, reason, all, dryRun, limit, item, noWait) match
          case Left(msg)  => Left(Cli.Error(msg, exitCode = ExitCode.Usage))
          case Right(cmd) => run(c, cmd, json)
      }
    }

  private def run(c: Client, cmd: QueueCmd, json: com.histmersion.cli.Ops.RunJson)(using console: Console): Either[Cli.Error, Unit] = cmd match
    case QueueCmd.Show(list, all) =>
      c.queueList(list, all)
        .map(l => Output.emit(json, l)(l => l.markdown.getOrElse("")))
    case QueueCmd.Add(list, text, topic, reason) =>
      c.queueAdd(list, QueueAdd(text, topic, reason = reason))
        .map(i => Output.emit(json, i)(i => s"added #${i.id} to ${i.list}${i.topic.map(t => s" · $t").getOrElse("")}"))
    // the item as it now stands, with --json like `show` and `add` (it printed only the line before)
    case QueueCmd.Done(id) =>
      c.queuePatch(id, QueuePatch(status = Some(Queue.Done)))
        .map(i => Output.emit(json, i)(i => s"done #${i.id}: ${i.text}"))
    case QueueCmd.Edit(id, text) =>
      c.queuePatch(id, QueuePatch(text = Some(text))).map(i => Output.emit(json, i)(i => s"edited #${i.id}: ${i.text}"))
    case QueueCmd.Rm(id) =>
      c.queueDelete(id).map(i => Output.emit(json, i)(i => s"removed #${i.id}: ${i.text}"))
    case QueueCmd.Export(dir) =>
      writeMeta(c, Path.of(dir)).map(fs => Cli.out.println(s"wrote ${fs.mkString(", ")} to $dir"))
    case QueueCmd.Import(dir) =>
      readMeta(c, Path.of(dir), replace = true).map(_.foreach(l => Cli.out.println(s"  $l")))
    case QueueCmd.Recheck(dryRun, limit, id, all, noWait) =>
      c.call(com.histmersion.api.library.AcquisitionWorkApi.acquireRecheck)(
        Acquire.Checks.RecheckRequest(dryRun, limit, id, Some("cli"), all)
      ).flatMap { r =>
        Output.emit(json, r)(AcquireChecksMarkdown.result)
        r.job match
          case Some(j) if !noWait && !json => IngestCmd.pollJob(c, j.id, 0)
          case _                           => Right(())
      }
