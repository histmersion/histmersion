package com.histmersion
package cli
package commandline

import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import com.histmersion.lint.{atBoundary, BoundaryKind}

/** `histmersion delete <citekey> [--apply]`, parsed into [[DeleteCmd.Args]]. */
@atBoundary(
  BoundaryKind.CliArgs,
  "the scallop adapter of `histmersion delete`: declares its options, parses them into typed commands"
)
object DeleteArgs extends CommandLine.Entry:
  val name = "delete"
  def help: String = DeleteCmd.Help

  def conf(): Subcommand = new Leaf("histmersion", name, help):
    private val citekey = arg[String]("citekey")
    private val apply =
      flag("apply", "Delete it (without this flag, a dry run lists what would go and what refuses it)")
    private val asJson = json()

    def command: Either[Problem, Cli.Command] =
      Right(
        DeleteCmd.Args(
          DeleteCmd.Args.Citekey(citekey().trim),
          DeleteCmd.Args.Apply(apply.getOrElse(false)),
          asJson()
        )
      )
