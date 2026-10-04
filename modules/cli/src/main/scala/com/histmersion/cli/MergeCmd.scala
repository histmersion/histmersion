package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.core.*

import io.circe.syntax.*
import java.nio.file.{Files, Path}

/** `histmersion merge plan` — what importing another collection's bundle would do, and every issue,
  * in one run (docs/research-merge-import.md §6, milestone M3). The bundle is uploaded to the server
  * (it is planned there, against this collection), the resolution file is read here and sent as
  * JSON. Left is always this collection, right the bundle. */
object MergeCmd:

  /** `--resolutions`, `--resolve ID=decision`…, `--editions`, as given. */
  final case class ResolutionFlags(file: Option[String], resolve: List[String], editions: Option[String]):
    def isEmpty: Boolean = file.isEmpty && resolve.isEmpty && editions.isEmpty

  val resolutionOpts: Opts[ResolutionFlags] =
    (
      Opts.option[String]("resolutions", "A resolution file (YAML; start one with merge plan --write-template)").orNone,
      Opts.options[String]("resolve", "Decide one issue: ID=decision, e.g. S-3c1e7a2b=keep-left (repeatable; overrides the file)")
        .orEmpty,
      Opts.option[String]("editions", "Answer every edition (year-gap) question at once: merge or separate").orNone
    ).mapN(ResolutionFlags.apply)

  /** The file, then the flags over it. Every bad line is reported at once. Reads the file; else pure. */
  def resolutions(f: ResolutionFlags): Either[CliError, MergeResolutions] =
    val fromFile: Either[List[String], MergeResolutions] = f.file match
      case None => Right(MergeResolutions())
      case Some(p) =>
        val path = Path.of(p)
        if !Files.isRegularFile(path) then Left(List(s"no such file: $p"))
        else MergeResolutionsFile.parse(Files.readString(path)).left.map(_.map(e => s"$p: $e"))
    val flags = f.resolve.map(MergeResolutionsFile.flag)
    val editions: Either[String, Option[EditionsPolicy]] = f.editions match
      case None    => Right(None)
      case Some(e) => EditionsPolicy.values.find(_.toString.equalsIgnoreCase(e.trim)).map(Some(_))
                        .toRight(s"--editions $e: expected merge or separate")
    val errors = fromFile.left.toOption.getOrElse(Nil) ++ flags.flatMap(_.left.toOption) ++ editions.left.toOption.toList
    if errors.nonEmpty then Left(CliError(errors.mkString("\n"), exitCode = 1))
    else
      Right(fromFile.toOption.get.overriddenBy(
        MergeResolutions(flags.flatMap(_.toOption), MergePolicy(editions.toOption.flatten))))

  /** The merged text of every `file:<path>` decision, read here and sent along: the server cannot
    * read this machine's files. A path that cannot be read is an error. */
  def withTexts(r: MergeResolutions): Either[CliError, MergeResolutions] =
    val read = r.resolve.map { x =>
      MergeDecision.parse(x.decision).toOption.filter(_.kind == DecisionKind.File).flatMap(_.argument) match
        case None => Right(x)
        case Some(p) =>
          val path = Path.of(p)
          if !Files.isRegularFile(path) then Left(s"${x.issue}: file:$p — no such file")
          else scala.util.Try(Files.readString(path)).toEither.left.map(e => s"${x.issue}: file:$p — ${e.getMessage}")
            .map(t => x.copy(text = Some(t)))
    }
    val errors = read.flatMap(_.left.toOption)
    if errors.nonEmpty then Left(CliError(errors.mkString("\n"), exitCode = 1))
    else Right(r.copy(resolve = read.flatMap(_.toOption)))

  /** Upload the bundle; refuse what is not one. */
  def upload(c: Client, f: java.io.File): Either[CliError, BundleUpload] =
    c.uploadBundle(f).flatMap { u =>
      if u.manifest.isEmpty then
        Left(CliError(s"${f.getPath} is not a readable bundle: ${u.problems.mkString("; ")}", exitCode = 2))
      else Right(u)
    }

  private val planCmd = Opts.subcommand("plan",
    "Dry run of importing a bundle into this collection: every match and every issue in one run, with stable ids to decide them by") {
    (
      Opts.argument[String]("file.hmx"),
      resolutionOpts,
      Opts.option[String]("write-template", "Write a resolution file listing every issue (decided ones filled in) to this path").orNone,
      Opts.flag("all", "List every default, info line and match, not only the first few").orFalse,
      Opts.flag("json", "Print the raw JSON plan instead of Markdown").orFalse
    ).mapN { (file, flags, template, all, json) => (c: Client) =>
      val p = Path.of(file)
      for
        _    <- Either.cond(Files.isRegularFile(p), (), CliError(s"no such file: $file", exitCode = 1))
        res  <- resolutions(flags)
        u    <- upload(c, p.toFile)
        plan <- c.mergePlan(MergePlanRequest(u.sha256, res))
        _ <- template.fold[Either[CliError, Unit]](Right(())) { t =>
               scala.util.Try(Files.writeString(Path.of(t), MergeResolutionsFile.template(plan, res))).toEither
                 .left.map(e => CliError(s"could not write $t: ${e.getMessage}", exitCode = 1)).map(_ => ())
             }
        _ = println(if json then plan.asJson.spaces2 else MergeMarkdown.plan(plan, all))
        _ = template.foreach(t => println(s"\n→ resolution template written to $t"))
        _ <- Either.cond(plan.problems.isEmpty && plan.resolutionErrors.isEmpty, (),
               CliError("the plan has problems or unusable resolutions (listed above)", exitCode = 2))
      yield ()
    }
  }

  private val applyCmd = Opts.subcommand("apply",
    "Carry out the merge of a bundle into this collection, as planned: a backup first, then one transaction") {
    (
      Opts.argument[String]("file.hmx"),
      resolutionOpts,
      Opts.option[String]("plan", "Apply only this plan (the hash merge plan printed); refused if the plan is now another").orNone,
      Opts.flag("yes", "Do not ask for confirmation").orFalse
    ).mapN { (file, flags, expected, yes) => (c: Client) =>
      val p = Path.of(file)
      for
        _    <- Either.cond(Files.isRegularFile(p), (), CliError(s"no such file: $file", exitCode = 1))
        res0 <- resolutions(flags)
        res  <- withTexts(res0)
        u    <- upload(c, p.toFile)
        plan <- c.mergePlan(MergePlanRequest(u.sha256, res))
        _    <- Either.cond(plan.problems.isEmpty && plan.resolutionErrors.isEmpty && plan.open.isEmpty, (), {
                  println(MergeMarkdown.plan(plan))
                  CliError("the merge cannot be applied yet: decide what is open (listed above), then apply again", exitCode = 2)
                })
        hash  = expected.map(_.trim).getOrElse(plan.planHash)
        _    <- Either.cond(yes || plan.changes == 0 ||
                  Main.confirm(s"Merge ${MergeMarkdown.who(plan.from)} into this collection (plan $hash, " +
                    s"${Plural.count(plan.changes, "change")})? A backup is taken first."),
                  (), CliError("aborted", exitCode = 1))
        r    <- c.mergeApply(MergeApplyRequest(u.sha256, hash, res))
        _     = println(MergeMarkdown.applied(r))
        _    <- Either.cond(r.applied, (), CliError("the merge was not applied (see above)", exitCode = 2))
      yield ()
    }
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("merge", "Merge another collection's bundle into this one: plan it (every issue in one run), then apply it") {
      planCmd orElse applyCmd
    }
