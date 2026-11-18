package demo

import com.histmersion.cli.{Cli, ExitCode}
import com.histmersion.util.problem.Problem

object Refusals:
  // E11 KEEP a caller (Shadow) writes ExitCode for something else
  def refused(s: String): Either[Cli.Error, Int] = Left(Cli.Error(Problem.Unclassified(s), exitCode = ExitCode.Refused))
  // E15 KEEP a caller (walled/Domain) is in a layer that may not import the wrapper
  def fenced(s: String): Either[Cli.Error, Int] = Left(Cli.Error(Problem.Unclassified(s)))
