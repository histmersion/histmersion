package com.histmersion
package cli
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.problem.Text.sentence

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.exchange.EditionsPolicy

import cats.syntax.all.*
import com.monovore.decline.*
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

  val resolutionOpts: Opts[ResolutionFlags] =
    (
      Opts
        .option[String]("resolutions", "A resolution file (YAML; start one with merge plan --write-template)")
        .orNone
        .map(_.map(ResolutionFlags.File(_))),
      Opts
        .options[String](
          "resolve",
          "Decide one issue: ID=decision, e.g. S-3c1e7a2b=keep-left (repeatable; overrides the file)"
        )
        .orEmpty
        .map(_.map(MergeResolutions.Text.Flag(_))),
      Opts
        .option[String]("editions", "Answer every edition (year-gap) question at once: merge or separate")
        .orNone
        .map(_.map(ResolutionFlags.Editions(_)))
    ).mapN(ResolutionFlags.apply)

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
            Problem.Unclassified(Legacy.Text(s"${f.getPath} is not a readable bundle: ${u.problems.mkString("; ")}")),
            exitCode = ExitCode.Refused
          )
        )
      else Right(u)
    }

  private def planCmd(using console: Console) = Opts.subcommand(
    "plan",
    "Dry run of importing a bundle into this collection: every match and every issue in one run, with stable ids to decide them by"
  ) {
    (
      Opts.argument[String]("file.hmx"),
      resolutionOpts,
      Opts
        .option[String](
          "write-template",
          "Write a resolution file listing every issue (decided ones filled in) to this path"
        )
        .orNone,
      Opts.flag("all", "List every default, info line and match, not only the first few").orFalse,
      Output.jsonFlag
    ).mapN { (file, flags, template, all, json) => (c: Client) =>
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
    }
  }

  private def applyCmd(using console: Console) = Opts.subcommand(
    "apply",
    "Carry out the merge of a bundle into this collection, as planned: a backup first, then one transaction"
  ) {
    (
      Opts.argument[String]("file.hmx"),
      resolutionOpts,
      Opts
        .option[String](
          "plan",
          "Apply only this plan (the hash merge plan printed); refused if the plan is now another"
        )
        .orNone,
      Opts.flag("yes", "Do not ask for confirmation").orFalse
    ).mapN { (file, flags, expected, yes) => (c: Client) =>
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
    }
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
            Problem.Unclassified(Legacy.Text(j.error.fold(s"merge apply ${j.job} failed")(_.sentence))),
            exitCode = ExitCode.Refused
          )
        )
      // the server restarted under it: nothing is half applied, but whether it committed is in `merge list`
      case MergeJobState.Interrupted =>
        Left(
          Cli.Error(
            Problem.Unclassified(
              Legacy.Text(s"merge apply ${j.job}: ${j.error.fold(MergeApplyJob.InterruptedText.sentence)(_.sentence)}")
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

  private def listCmd(using console: Console) =
    Opts.subcommand("list", "The merges this collection took in, newest first") {
      (Opts.option[Int]("limit", "How many (default 50)").orNone, Output.jsonFlag).mapN { (limit, json) => (c: Client) =>
        c.merges(limit.map(Paging.Limit(_))).map(ms => Output.emit(json, ms)(MergeMarkdown.merges))
      }
    }

  private def showCmd(using console: Console) =
    Opts.subcommand("show", "One merge: its report, the resolutions it was given, the decisions it remembers") {
      (Opts.argument[String]("merge-id"), Output.jsonFlag).mapN { (id, json) => (c: Client) =>
        c.mergeDetail(Api.Merge.Id(id)).map(d => Output.emit(json, d)(MergeMarkdown.detail))
      }
    }

  private def ledgerCmd(using console: Console) = Opts.subcommand(
    "ledger",
    "The merge ledger: which record of another collection each record here came from (one record with --uid, one merge with --merge)"
  ) {
    (
      Opts.option[String]("uid", "A record: its uid (here, or merged away) or a citekey").orNone,
      Opts.option[String]("merge", "One merge: its id or first characters").orNone,
      Opts.option[Int]("limit", "How many rows (default 200)").orNone,
      Output.jsonFlag
    ).mapN { (uid, merge, limit, json) => (c: Client) =>
      c.mergeLedger(uid.map(Api.Merge.Id(_)), merge.map(Api.Merge.Entity(_)), limit.map(Paging.Limit(_)))
        .map(l => Output.emit(json, l)(MergeMarkdown.ledger))
    }
  }

  def command(using console: Console): Opts[Client => Either[Cli.Error, Unit]] =
    Opts.subcommand(
      "merge",
      "Merge another collection's bundle into this one: plan it (every issue in one run), apply it; list, show and trace past merges"
    ) {
      planCmd orElse applyCmd orElse listCmd orElse showCmd orElse ledgerCmd
    }
