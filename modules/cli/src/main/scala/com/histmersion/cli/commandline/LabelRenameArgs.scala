package com.histmersion
package cli
package commandline

import com.histmersion.api.exchange.Bundle
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.{Problem, Problems}
import org.rogach.scallop.*
import com.histmersion.lint.{atBoundary, BoundaryKind}

/** `histmersion goals|events|places rename <old> <new>`: the new label checked with the server's own rule. */
@atBoundary(
  BoundaryKind.CliArgs,
  "the scallop adapter of `histmersion labelrename`: declares its options, parses them into typed commands"
)
object LabelRenameArgs:
  def leaf(parent: String, kind: Bundle.Kind): Subcommand = new Leaf(parent, "rename", LabelRenameCmd.help(kind)):
    private val from = arg[String]("old")
    private val to = arg[String]("new")
    private val asJson = json()

    def command: Either[Problems, Cli.Command] = LabelRenameCmd.rename(kind, from(), to(), asJson())
