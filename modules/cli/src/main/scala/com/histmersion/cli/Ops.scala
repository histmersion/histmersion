package com.histmersion
package cli
import com.histmersion.util.problem.{Legacy, Problem}

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.api.automation.Queue.{QueueAdd, QueueImport, QueuePatch}

import java.nio.file.{Files, Path}
import com.histmersion.api.automation.{GoalsExport, Queue}
import com.histmersion.api.checks.{FormatCheck, FormatField, FormatMarkdown, FormatReport, Lint}
import com.histmersion.api.library.{AcquireChecksMarkdown, Citekey, Query}
import com.histmersion.api.research.ReviewGoal
import com.histmersion.api.library.Acquire
import com.histmersion.util.console.Console
import com.histmersion.api.Api
import com.histmersion.api.library.AcquisitionWorkApi
import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The CLI commands of stream 2.8 — `lint`, `queue`, `query`, `schema` — plus the pure argument parsing they need. Like
  * every other command they are HTTP clients of the server; the host never touches Postgres.
  *
  * `queue` reads as the plan specifies (`queue acquire add "…"`, `queue tasks`, `queue done 3`, `queue export <dir>`):
  * each list and each verb is a subcommand.
  */
@atBoundary(BoundaryKind.CliArgs, "parses `histmersion lint`, `queue`, `query` and `schema` arguments")
object Ops:

  // ── queue ───────────────────────────────────────────────────────────────────────────────────

  /** What `histmersion queue …` was asked to do. */
  enum QueueCmd extends Cli.Command:
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case Show(list: Queue.Kind.Label, all: Boolean, json: Cli.JsonOutput)
    case Add(
        list: Queue.Kind.Label,
        text: Queue.QueueItem.Text,
        topic: Option[Queue.QueueItem.Topic],
        reason: Option[Queue.Rationale],
        by: AgentIdentity,
        json: Cli.JsonOutput
    )
    case Done(id: Queue.Id, json: Cli.JsonOutput)
    case Edit(id: Queue.Id, text: Queue.QueueItem.Text, json: Cli.JsonOutput)
    case Patch(id: Queue.Id, actor: Option[Creation.Actor], json: Cli.JsonOutput)
    case Rm(id: Queue.Id, json: Cli.JsonOutput)
    case Export(dir: String)
    case Import(dir: String)

    /** `queue acquire recheck`: the acquisition list's legal re-check now — of every open item (`all`) or the due ones
      * the caps allow; a pass of more than one item is a server job, followed unless told to return.
      */
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case Recheck(
        writes: Cli.Writes,
        limit: Option[Int],
        id: Option[Int],
        all: Boolean,
        following: Cli.Following,
        json: Cli.JsonOutput
    )

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = RunQueue(c)(this)

  // ── the meta files on disk ────────────────────────────────────────────────────────────────────────

  /** Write `ACQUIRE.md` / `TASKS.md` / `LOG.md` and `GOALS.md` into `dir`, rendered by the server from the rows. Used
    * by `queue export` and by `histmersion export`, so both produce identical files.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def writeMeta(c: Client, dir: Path): Either[Cli.Error, List[String]] =
    val _ = Files.createDirectories(dir)
    val files: List[(String, () => Either[Cli.Error, String])] =
      Queue.Kind.values.toList.map(k =>
        k.file.value -> (() => c.queueExport(Queue.Wire(k.label.value)).map(_.value))
      ) :+
        (GoalsExport.File -> (() => c.goalsExport.map(_.value)))
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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def readMeta(c: Client, dir: Path, replace: Api.Overwrite): Either[Cli.Error, List[String]] =
    if !Files.isDirectory(dir) then Left(Cli.Error(Problem.Unclassified(Legacy.Text(s"not a directory: $dir"))))
    else
      Queue.Kind.values.toList.foldLeft[Either[Cli.Error, List[String]]](Right(Nil)) { (acc, kind) =>
        acc.flatMap { done =>
          val f = dir.resolve(kind.file.value)
          if !Files.isRegularFile(f) then Right(done :+ shown"${kind.file}: absent, skipped")
          else
            c.queueImport(
              Queue.Wire(kind.label.value),
              QueueImport(Queue.QueueItem.Text(Files.readString(f)), QueueImport.Replace(replace.value))
            ).map(r => done :+ s"${kind.file}: ${r.items} item(s), ${r.notes} note block(s), ${r.replaced} replaced")
        }
      }

  // ── commands ─────────────────────────────────────────────────────────────────────────────────

  /** `lint --fix` applies the server-side repairs and then loops while sources remain to re-chunk (or point-only
    * imported areas to remove), because a whole-corpus pass is far longer than one HTTP request.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @annotation.tailrec
  private def fixLoop(using
      console: Console
  )(
      c: Client,
      rules: List[String],
      sources: Int,
      chunks: Int,
      edges: Int,
      moved: Option[Int] = None,
      placed: Option[Int] = None,
      places: Option[List[String]] = None,
      areas: Int = 0
  ): Either[Cli.Error, List[Lint.LintReport.Fixed]] =
    c.lintFix(rules.map(Lint.Wire(_))) match
      case Left(e)  => Left(e)
      case Right(r) =>
        val (s, ch, ed) =
          (sources + r.sourcesReindexed.value, chunks + r.chunksWritten.value, edges + r.edgesResolved.value)
        // the geocode repair is not bounded: the first call made every move there was
        val mv =
          moved.orElse(
            Option.when(rules.exists(_.trim.equalsIgnoreCase(Lint.FindingGeocode)))(r.findingsRegeocoded.value)
          )
        // …and neither is the fill of unplaced findings
        val pl =
          placed.orElse(
            Option.when(rules.exists(_.trim.equalsIgnoreCase(Lint.FindingUnplaced)))(r.findingsPlaced.value)
          )
        // …nor the gazetteer clean-up (place-kind, place-grid-point): its lines are the first call's
        val pc = places.orElse(
          Some(
            r.applied
              .map(_.value)
              .filter(l => l.startsWith(Lint.PlaceKindRule + ":") || l.startsWith(Lint.PlaceGridPoint + ":"))
          )
        )
        val ar = areas + r.pointAreasRemoved.value
        val asksAreas = rules.exists(_.trim.equalsIgnoreCase(Lint.PlaceImportedPointArea))
        // a call that removed nothing while some remain is not progress (they are being changed
        // under it): stop rather than loop
        val moreAreas = asksAreas && r.pointAreasRemaining.value > 0 && r.pointAreasRemoved.value > 0
        if r.remaining.value > 0 || moreAreas then
          Cli.err.print(if moreAreas then s"\rfixing… $ar point-only area(s) removed, ${r.pointAreasRemaining} to go"
          else s"\rfixing… $s source(s) reindexed, ${r.remaining} to go")
          fixLoop(c, rules, s, ch, ed, mv, pl, pc, ar)
        else
          if sources > 0 || ar > 0 then Cli.err.print("\r" + " " * 70 + "\r")
          Right(
            (List(s"resolve-edges: $ed edge target(s) rewritten", s"reindex: $s source(s) → $ch chunk(s)") ++
              mv.map(n => s"${Lint.FindingGeocode}: $n finding(s) moved to the place the current geocoder chooses")
                .toList ++
              pl.map(n => s"${Lint.FindingUnplaced}: $n finding(s) given the point of the place they name").toList ++
              pc.getOrElse(Nil) ++
              Option
                .when(asksAreas)(
                  s"${Lint.PlaceImportedPointArea}: $ar point-only imported area(s) removed" +
                    (if r.pointAreasRemaining.value > 0 then
                       s", ${r.pointAreasRemaining} left (named by evidence, or changing — run again)"
                     else "")
                )
                .toList).map(Lint.LintReport.Fixed(_))
          )

  /** `lint --file x.md`: the server checks the text (it has the renderer); with `--fix` the repaired text is written
    * back over the file. A note file's frontmatter is the note's own and is not sent.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def lintFile(c: Client, p: java.nio.file.Path, fix: Boolean): Either[Cli.Error, FormatReport] =
    if !java.nio.file.Files.isRegularFile(p) then
      Left(Cli.Error(Problem.Unclassified(Legacy.Text(s"no such file: $p"))))
    else
      val raw = java.nio.file.Files.readString(p)
      val (head, body) = FileFormat.splitFrontmatter(FileFormat.Text(raw))
      c.lintMarkdown(FormatCheck(FormatCheck.Text(body.value), FormatField.Body, FormatCheck.Fix(fix))).map { r =>
        r.text
          .map(_.value)
          .filter(t => fix && t != body.value)
          .foreach(t => java.nio.file.Files.writeString(p, head.value + t))
        r.modify(_.subject).setTo(FormatReport.Subject(p.getFileName.toString)).modify(_.text).setTo(None)
      }

  /** Carries out QueueCmd commands against one server. */
  final private[cli] class RunQueue(c: Client)(using console: Console):
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def apply(cmd: QueueCmd): Either[Cli.Error, Unit] =
      cmd match
        case QueueCmd.Show(list, all, json) =>
          c.queueList(list, all)
            .map(l => Output.emit(json, l)(l => l.markdown.fold("")(_.value)))
        case QueueCmd.Add(list, text, topic, reason, by, json) =>
          val createdBy = by.orElse(AgentIdentity.configured(Settings.app.cli.agent)).createdBy
          c.queueAdd(
            list,
            QueueAdd(text, topic, reason = reason, createdBy = createdBy, via = Some(Creation.Cli))
          ).map(i =>
            Output.emit(json, i)(i => s"added #${i.id} to ${i.list}${i.topic.map(t => s" · $t").getOrElse("")}")
          )
        // the item as it now stands, with --json like `show` and `add` (it printed only the line before)
        case QueueCmd.Done(id, json) =>
          c.queuePatch(id, QueuePatch(status = Some(Queue.QueueItem.Status(Queue.Done))))
            .map(i => Output.emit(json, i)(i => shown"done #${i.id}: ${i.text}"))
        case QueueCmd.Edit(id, text, json) =>
          c.queuePatch(id, QueuePatch(text = Some(text)))
            .map(i => Output.emit(json, i)(i => shown"edited #${i.id}: ${i.text}"))
        case QueueCmd.Patch(id, actor, json) =>
          c.queuePatch(id, QueuePatch(actor = actor))
            .map(i => Output.emit(json, i)(i => s"#${i.id}: ${i.actor.fold("")(a => s"$a acts · ")}${i.text}"))
        case QueueCmd.Rm(id, json) =>
          c.queueDelete(id).map(i => Output.emit(json, i)(i => shown"removed #${i.id}: ${i.text}"))
        case QueueCmd.Export(dir) =>
          writeMeta(c, Path.of(dir)).map(fs => Cli.out.println(s"wrote ${fs.mkString(", ")} to $dir"))
        case QueueCmd.Import(dir) =>
          readMeta(c, Path.of(dir), replace = Api.Overwrite(true)).map(_.foreach(l => Cli.out.println(s"  $l")))
        case QueueCmd.Recheck(writes, limit, id, all, following, json) =>
          c.call(AcquisitionWorkApi.acquireRecheck)(
            Acquire.Checks.RecheckRequest(
              Acquire.Checks.RecheckRequest.DryRun(writes.dry),
              limit.map(Acquire.Checks.RecheckRequest.Limit(_)),
              id.map(Acquire.Checks.RecheckRequest.Id(_)),
              Some(Acquire.Checks.RecheckRequest.By("cli")),
              Acquire.Checks.RecheckRequest.All(all)
            )
          ).flatMap { r =>
            Output.emit(json, r)(AcquireChecksMarkdown.result)
            r.job match
              case Some(j) => json.choose(Cli.Following.Return, following).follow(IngestCmd.pollJob(c, j.id, 0))
              case None    => Right(())
          }

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case Lint(
        rules: List[String],
        fix: Boolean,
        article: Option[String],
        source: Option[String],
        file: Option[String],
        json: Cli.JsonOutput
    )
    case Query(sql: String, json: Cli.JsonOutput)
    case Schema(json: Cli.JsonOutput)

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.Lint(rules, fix, article, source, file, json) =>
          def show(r: FormatReport)(using console: Console) = Output.emit(json, r)(FormatMarkdown.report)
          (article, source, file) match
            case (Some(slug), _, _) =>
              (if fix then c.fixArticleFormat(ReviewGoal.Slug(slug)) else c.lintArticle(ReviewGoal.Slug(slug)))
                .map(show)
            case (_, Some(ck), _) =>
              (if fix then c.fixSourceFormat(Citekey(ck)) else c.lintSource(Citekey(ck))).map(show)
            case (_, _, Some(f)) => lintFile(c, java.nio.file.Path.of(f), fix).map(show)
            case _               =>
              for
                fixed <- if fix then fixLoop(c, rules, 0, 0, 0) else Right(Nil)
                rep <- c.lint(rules.map(Lint.Wire(_)))
              yield
                val full = rep.modify(_.fixed).setTo(fixed)
                Output.emit(json, full)(Lint.markdown(_).value)
        case Command.Query(sql, json) =>
          c.query(Query.Statement(sql)).map(r => Output.emit(json, r)(Query.markdown(_).value))
        case Command.Schema(json) =>
          c.schemaTables.map(s => Output.emit(json, s)(Query.schemaMarkdown(_).value))
