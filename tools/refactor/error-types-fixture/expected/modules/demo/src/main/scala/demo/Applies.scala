package demo

import com.histmersion.cli.Cli
import com.histmersion.util.problem.Problem

trait Runs:
  def run(n: Int): Either[Cli.Error, Int]

object Runner extends Runs:
  // E12 KEEP overrides an abstract method
  def run(n: Int): Either[Cli.Error, Int] = Left(Cli.Error(Problem.Unclassified("no")))

final class Perform(n: Int):
  // E13 KEEP called as `Perform(n)(m)`, without its name
  def apply(m: Int): Either[Cli.Error, Int] = Left(Cli.Error(Problem.Unclassified(s"$n $m")))

object UsesPerform:
  // E14 KEEP calls an apply without naming it
  def go(n: Int): Either[Cli.Error, Int] = Perform(n)(n)
