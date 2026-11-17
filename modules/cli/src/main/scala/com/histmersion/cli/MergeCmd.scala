package com.histmersion
package cli
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.exchange.EditionsPolicy

import com.histmersion.api.{Api, Paging, Plural}
import com.histmersion.config.*

import java.nio.file.{Files, Path}
import com.histmersion.util.data.exchange.MergeResolutionsFile
import com.histmersion.api.exchange.{
  MergeApplyJob,
  MergeApplyRequest,
  MergeApplyResult,
  MergeDecision,
  MergeJobState,
  MergeMarkdown,
  MergePolicy,
  MergeResolution,
  MergeResolutions
}
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.exchange.MergePlan
import com.histmersion.util.console.Console
import io.github.iltotore.iron.constraint.any.Pure

/** `histmersion merge plan` — what importing another collection's bundle would do, and every issue, in one run
  * (docs/research-merge-import.md §6, milestone M3). The bundle is uploaded to the server (it is planned there, against
  * this collection), the resolution file is read here and sent as JSON. Left is always this collection, right the
  * bundle.
  */
object MergeCmd:
  /** `--resolutions`, `--resolve ID=decision`…, `--editions`, as given. */

  final case class ResolutionFlags(
      file: Option[ResolutionFlags.File],
      resolve: List[MergeResolutions.Text.Flag],
      editions: Option[ResolutionFlags.Editions]
  ):
    def isEmpty: Boolean = file.isEmpty && resolve.isEmpty && editions.isEmpty

  object ResolutionFlags:

    type File = File.T
    object File extends Newtype[String, Pure]
    type Editions = Editions.T
    object Editions extends Newtype[String, Pure]

  /** The file, then the flags over it. Every bad line is reported at once. Reads the file; else pure. */
  def resolutions(f: ResolutionFlags): Either[Cli.Error, MergeResolutions] =
    val fromFile: Either[List[Problem], MergeResolutions] = f.file match
      case None    => Right(MergeResolutions())
      case Some(p) =>
        val path = Path.of(p.value)
        if !Files.isRegularFile(path) then Left(List(Problem.Unclassified(Legacy.Text(s"no such file: $p"))))
        else
          MergeResolutionsFile
            .parse(MergeResolutionsFile.Text(Files.readString(path)))
            .left
            .map(_.map(e => Problem.Unclassified(Legacy.Text(s"$p: ${e.sentence}"))))
    val flags = f.resolve.map(MergeResolutionsFile.flag)
    val editions: Either[Problem, Option[EditionsPolicy]] = f.editions match
      case None    => Right(None)
      case Some(e) =>
        EditionsPolicy.values
          .find(_.toString.equalsIgnoreCase(e.value.trim))
          .map(Some(_))
          .toRight(Problem.Unclassified(Legacy.Text(s"--editions $e: expected merge or separate")))
    val errors =
      fromFile.left.toOption.getOrElse(Nil) ++ flags.flatMap(_.left.toOption) ++ editions.left.toOption.toList
    if errors.nonEmpty then
      Left(
        Cli.Error(Problem.Unclassified(Legacy.Text(errors.map(_.sentence).mkString("\n"))), exitCode = ExitCode.Usage)
      )
    else
      Right(
        fromFile.toOption.get.overriddenBy(
          MergeResolutions(flags.flatMap(_.toOption), MergePolicy(editions.toOption.flatten))
        )
      )

  /** The merged text of every `file:<path>` decision, read here and sent along: the server cannot read this machine's
    * files. A path that cannot be read is an error.
    */
  def withTexts(r: MergeResolutions): Either[Cli.Error, MergeResolutions] =
    val read = r.resolve.map { x =>
      MergeDecision
        .parse(MergeDecision.Wire(x.decision.value))
        .toOption
        .filter(_.kind == MergeDecision.Kind.File)
        .flatMap(_.argument) match
        case None => Right(x)
        // the resolution file carries the text itself (`texts:`, written by the web review screen)
        case Some(_) if x.text.isDefined => Right(x)
        case Some(p)                     =>
          val path = Path.of(p.value)
          if !Files.isRegularFile(path) then Left(s"${x.issue}: file:$p — no such file")
          else
            scala.util
              .Try(Files.readString(path))
              .toEither
              .left
              .map(e => s"${x.issue}: file:$p — ${e.getMessage}")
              .map(t => x.modify(_.text).setTo(Some(MergeResolution.Text(t))))
    }
    val errors = read.flatMap(_.left.toOption)
    if errors.nonEmpty then
      Left(Cli.Error(Problem.Unclassified(Legacy.Text(errors.mkString("\n"))), exitCode = ExitCode.Usage))
    else Right(r.modify(_.resolve).setTo(read.flatMap(_.toOption)))

  /** Upload the bundle; refuse what is not one. */
  def upload(c: Client, f: java.io.File): Either[Cli.Error, Bundle.Upload] =
    c.uploadBundle(f).flatMap { u =>
      if u.manifest.isEmpty then
        Left(
          Cli.Error(
            Problem.Unclassified(
              Legacy.Text(s"${f.getPath} is not a readable bundle: ${u.problems.map(_.sentence).mkString("; ")}")
            ),
            exitCode = ExitCode.Refused
          )
        )
      else Right(u)
    }

  /** How often a running apply is asked about. */
  private val PollMs = 2000

  /** Follow an apply job to its end: the apply takes a backup first, which can outlast the request. */
  @annotation.tailrec
  def follow(c: Client, j: MergeApplyJob)(using console: Console): Either[Cli.Error, MergeApplyResult] =
    j.state match
      case MergeJobState.Done =>
        j.result.toRight(
          Cli.Error(
            Problem.Unclassified(Legacy.Text(s"merge apply ${j.job}: done, but no result")),
            exitCode = ExitCode.Refused
          )
        )
      case MergeJobState.Failed =>
        Left(
          Cli.Error(
            Problem.Unclassified(
              Legacy.Text(j.problems.fold(s"merge apply ${j.job} failed")(_.sentences.mkString("; ")))
            ),
            exitCode = ExitCode.Refused
          )
        )
      // the server restarted under it: nothing is half applied, but whether it committed is in `merge list`
      case MergeJobState.Interrupted =>
        Left(
          Cli.Error(
            Problem.Unclassified(
              Legacy.Text(
                s"merge apply ${j.job}: ${j.problems.fold(MergeApplyJob.InterruptedText.sentence)(_.sentences.mkString("; "))}"
              )
            ),
            exitCode = ExitCode.Refused
          )
        )
      case MergeJobState.Running =>
        JobFollow.Progress.line(
          s"merge apply ${j.job.value.take(8)}: running (${j.elapsedMs.value / 1000}s — the backup comes first)"
        )
        Thread.sleep(PollMs.toLong)
        c.mergeApplyStatus(Api.Merge.Id(j.job.value)) match
          case Left(e)     => Cli.err.println(); Left(e)
          case Right(next) =>
            if next.state != MergeJobState.Running then JobFollow.Progress.clear()
            follow(c, next)

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    case Plan(
        file: String,
        flags: ResolutionFlags,
        template: Option[String],
        all: Boolean,
        json: Cli.JsonOutput
    )
    case Apply(file: String, flags: ResolutionFlags, expected: Option[String], yes: Boolean)
    case ListCmd(limit: Option[Int], json: Cli.JsonOutput)
    case Show(id: String, json: Cli.JsonOutput)
    case Ledger(uid: Option[String], merge: Option[String], limit: Option[Int], json: Cli.JsonOutput)

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.Plan(file, flags, template, all, json) =>
          val p = Path.of(file)
          for
            _ <- Either.cond(
              Files.isRegularFile(p),
              (),
              Cli.Error(Problem.Unclassified(Legacy.Text(s"no such file: $file")), exitCode = ExitCode.Usage)
            )
            res <- resolutions(flags)
            u <- upload(c, p.toFile)
            plan <- c.mergePlan(MergePlan.Request(MergePlan.Request.Bundle(u.sha256.value), res))
            _ <- template.fold[Either[Cli.Error, Unit]](Right(())) { t =>
              scala.util
                .Try(Files.writeString(Path.of(t), MergeResolutionsFile.template(plan, res).value))
                .toEither
                .left
                .map(e =>
                  Cli.Error(
                    Problem.Unclassified(Legacy.Text(s"could not write $t: ${e.getMessage}")),
                    exitCode = ExitCode.Usage
                  )
                )
                .map(_ => ())
            }
            _ = Output.emit(json, plan)(MergeMarkdown.plan(_, all))
            _ = template.foreach(t => Cli.out.println(s"\n→ resolution template written to $t"))
            _ <- Either.cond(
              plan.problems.isEmpty && plan.resolutionErrors.isEmpty,
              (),
              Cli.Error(
                Problem.Unclassified(Legacy.Text("the plan has problems or unusable resolutions (listed above)")),
                exitCode = ExitCode.Refused
              )
            )
          yield ()
        case Command.Apply(file, flags, expected, yes) =>
          val p = Path.of(file)
          for
            _ <- Either.cond(
              Files.isRegularFile(p),
              (),
              Cli.Error(Problem.Unclassified(Legacy.Text(s"no such file: $file")), exitCode = ExitCode.Usage)
            )
            res0 <- resolutions(flags)
            res <- withTexts(res0)
            u <- upload(c, p.toFile)
            plan <- c.mergePlan(MergePlan.Request(MergePlan.Request.Bundle(u.sha256.value), res))
            _ <- Either.cond(
              plan.problems.isEmpty && plan.resolutionErrors.isEmpty && plan.open.isEmpty,
              (), {
                Cli.out.println(MergeMarkdown.plan(plan))
                Cli.Error(
                  Problem.Unclassified(
                    Legacy.Text("the merge cannot be applied yet: decide what is open (listed above), then apply again")
                  ),
                  exitCode = ExitCode.Refused
                )
              }
            )
            hash = expected.map(h => MergePlan.PlanHash(h.trim)).getOrElse(plan.planHash)
            _ <- Either.cond(
              yes || plan.changes.value == 0 ||
                Cli.confirm(
                  Cli.Question(
                    s"Merge ${MergeMarkdown.who(plan.from)} into this collection (plan $hash, " +
                      s"${Plural.count(Plural.CountN(plan.changes.value.toLong), Plural.CountSingular("change"))})? A backup is taken first."
                  )
                ),
              (),
              Cli.Error(Problem.Unclassified(Legacy.Text("aborted")), exitCode = ExitCode.Usage)
            )
            job <- c.mergeApply(MergeApplyRequest(Bundle.Name(u.sha256.value), hash, res))
            r <- follow(c, job)
            _ = Cli.out.println(MergeMarkdown.applied(r))
            _ <- Either.cond(
              r.applied.value,
              (),
              Cli.Error(
                Problem.Unclassified(Legacy.Text("the merge was not applied (see above)")),
                exitCode = ExitCode.Refused
              )
            )
          yield ()
        case Command.ListCmd(limit, json) =>
          c.merges(limit.map(Paging.Limit(_))).map(ms => Output.emit(json, ms)(MergeMarkdown.merges))
        case Command.Show(id, json) =>
          c.mergeDetail(Api.Merge.Id(id)).map(d => Output.emit(json, d)(MergeMarkdown.detail))
        case Command.Ledger(uid, merge, limit, json) =>
          c.mergeLedger(uid.map(Api.Merge.Id(_)), merge.map(Api.Merge.Entity(_)), limit.map(Paging.Limit(_)))
            .map(l => Output.emit(json, l)(MergeMarkdown.ledger))
