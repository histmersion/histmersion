package com.histmersion
package cli
package commandline

import com.histmersion.api.library.Citekey
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*

/** `histmersion rename <old> <new>`: a source's new citekey, parsed into [[RenameCmd.Args]]. */
object RenameArgs extends CommandLine.Entry:
  val name = "rename"
  def help: String = RenameCmd.Help

  def conf(): Subcommand = new Leaf("histmersion", name, help):
    private val from = arg[String]("old")
    private val to = arg[String]("new")
    private val asJson = json()

    def command: Either[Problem, Cli.Command] =
      Right(RenameCmd.Args(Citekey(from().trim), Citekey(to().trim), asJson()))
