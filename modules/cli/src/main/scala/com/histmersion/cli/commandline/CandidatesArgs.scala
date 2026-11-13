package com.histmersion
package cli
package commandline

import com.histmersion.config.*
import com.histmersion.api.library.{AcquireApi, CandidateMark, Candidates, CandidatesMarkdown}
import com.histmersion.api.library.CandidateSweep
import com.histmersion.util.console.Console
import com.histmersion.api.automation.Goal
import com.histmersion.api.library.CandidateList
import com.histmersion.api.library.Source
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*

/** The command line of [[CandidatesCmd]]. */
object CandidatesArgs:
  object CandidatesEntry extends CommandLine.Entry:
    val name = "candidates"
    def help: String = "New sources found for the research goals without a model: list, sweep, mark"
    def conf(): Subcommand = GroupCandidates()

  final private class LeafSweep() extends Leaf("histmersion candidates", "sweep", "Read a goal's saved queries, citation and author alerts from their watermarks now (no model)") with SharedFlags:
    private val goal_ = arg[String]("goal")
    private val dry_ = flag("dry-run", "Read, store nothing (no candidates, no watermarks)")

    def command: Either[Problem, Cli.Command] = Right(CandidatesCmd.Command.Sweep(goal_(), dry_.getOrElse(false)))

  final private class LeafMark() extends Leaf("histmersion candidates", "mark", "Record a decision on candidates by id: seen, queued, dismissed (or new again)") with SharedFlags:
    private val ids_ = args[Long]("id", required = true)
    private val as_ = value[String]("as", "seen|queued|dismissed|new")

    def command: Either[Problem, Cli.Command] = Right(CandidatesCmd.Command.Mark(ids_(), as_.getOrElse(Candidates.State.Seen.wire.value)))

  /** `candidates [--goal g] [--state s]…` lists; `candidates sweep|mark` are the other commands. */
  final private class GroupCandidates()
      extends Leaf("histmersion", "candidates", "New sources found for the research goals without a model: list, sweep, mark")
      with SharedFlags:
    private val goal_ = value[String]("goal", "One goal's candidates (default: every goal)")
    private val state_ =
      value[String]("state", s"${Candidates.State.values.map(_.wire).mkString("|")} (default new: the unseen)")
    private val limit_ = value[Int]("limit", "Max results")
    private val offset_ = value[Int]("offset", "Skip this many (the list's footer names the next offset)")
    holds(LeafSweep(), LeafMark())

    def command: Either[Problem, Cli.Command] =
      Right(CandidatesCmd.Command.Listing(goal_.toOption, state_.toOption, limit_.toOption, offset_.toOption))
