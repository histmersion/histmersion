package com.histmersion
package cli
package commandline

import com.histmersion.api.automation.Goal
import com.histmersion.api.research.OpenWork
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*

object StateArgs:
  private val Help = "The research state: what waits for you (decisions, acquisitions, tasks, paused runs) and what " +
    "the agents will handle"

  object StateEntry extends CommandLine.Entry:
    val name = "state"
    def help: String = Help
    def conf(): Subcommand = LeafState()

  final private class LeafState() extends Leaf("histmersion", "state", Help) with SharedFlags:
    private val who_ = value[OpenWork.Terms.Who]("who", "Only what waits for you (owner) or for the agents (agents)")
    private val goal_ = value[Goal.Slug]("goal", "Only this goal's items (its slug)")
    private val kind_ = value[OpenWork.Terms.Kind]("kind", "Only decision, acquisition, task or run")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      StateCmd.Command.State(OpenWork.Scope(who_.toOption, goal_.toOption, kind_.toOption), json_())
    )
