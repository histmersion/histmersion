package com.histmersion
package cli
package commandline

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence
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
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*

/** The command line of [[MergeCmd]]. */
object MergeArgs:
  object MergeEntry extends CommandLine.Entry:
    val name = "merge"
    def help: String =
      "Merge another collection's bundle into this one: plan it (every issue in one run), apply it; list, show and trace past merges"
    def conf(): Subcommand = GroupMerge()

  final private class LeafPlan()
      extends Leaf(
        "histmersion merge",
        "plan",
        "Dry run of importing a bundle into this collection: every match and every issue in one run, with stable ids to decide them by"
      )
      with SharedFlags:
    private val file_ = arg[String]("file.hmx")
    private val flags_ = resolutionFlags()
    private val template_ = value[String](
      "write-template",
      "Write a resolution file listing every issue (decided ones filled in) to this path"
    )
    private val all_ = flag("all", "List every default, info line and match, not only the first few")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      MergeCmd.Command.Plan(file_(), flags_(), template_.toOption, all_.getOrElse(false), json_())
    )

  final private class LeafApply()
      extends Leaf(
        "histmersion merge",
        "apply",
        "Carry out the merge of a bundle into this collection, as planned: a backup first, then one transaction"
      )
      with SharedFlags:
    private val file_ = arg[String]("file.hmx")
    private val flags_ = resolutionFlags()
    private val expected_ =
      value[String]("plan", "Apply only this plan (the hash merge plan printed); refused if the plan is now another")
    private val yes_ = flag("yes", "Do not ask for confirmation")

    def command: Either[Problem, Cli.Command] = Right(
      MergeCmd.Command.Apply(file_(), flags_(), expected_.toOption, yes_.getOrElse(false))
    )

  final private class LeafListCmd()
      extends Leaf("histmersion merge", "list", "The merges this collection took in, newest first")
      with SharedFlags:
    private val limit_ = value[Int]("limit", "How many (default 50)")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(MergeCmd.Command.ListCmd(limit_.toOption, json_()))

  final private class LeafShow()
      extends Leaf(
        "histmersion merge",
        "show",
        "One merge: its report, the resolutions it was given, the decisions it remembers"
      )
      with SharedFlags:
    private val id_ = arg[String]("merge-id")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(MergeCmd.Command.Show(id_(), json_()))

  final private class LeafLedger()
      extends Leaf(
        "histmersion merge",
        "ledger",
        "The merge ledger: which record of another collection each record here came from (one record with --uid, one merge with --merge)"
      )
      with SharedFlags:
    private val uid_ = value[String]("uid", "A record: its uid (here, or merged away) or a citekey")
    private val merge_ = value[String]("merge", "One merge: its id or first characters")
    private val limit_ = value[Int]("limit", "How many rows (default 200)")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      MergeCmd.Command.Ledger(uid_.toOption, merge_.toOption, limit_.toOption, json_())
    )

  final private class GroupMerge()
      extends Group(
        "histmersion",
        "merge",
        "Merge another collection's bundle into this one: plan it (every issue in one run), apply it; list, show and trace past merges"
      ):
    holds(LeafPlan(), LeafApply(), LeafListCmd(), LeafShow(), LeafLedger())
