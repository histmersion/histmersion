package demo

import com.histmersion.cli.{Cli, ExitCode}
import com.histmersion.util.problem.Problem

object Refusals:
  // E11 KEEP a caller (Shadow) writes ExitCode for something else
  def refused(s: String): Either[Cli.Error, Int] = Left(Cli.Error(Problem.Unclassified(s), exitCode = ExitCode.Refused))
