package demo

import com.histmersion.api.Api
import com.histmersion.cli.{Cli, ExitCode}
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problems

object Parse:
  // E1 CONVERT a leaf with one extra field
  def number(s: String): Either[Problems, Int] =
    s.toIntOption.toRight(Problem.Unclassified(s"not a number: $s"))
  // E2 CONVERT no extra field: two constructions; an adapter; a lambda adapter
  def positive(n: Int): Either[Problems, Int] =
    if n > 0 then Right(n) else Left(Problem.Unclassified("not positive"))
  def checked(e: Either[Problem, Int]): Either[Problems, Int] = e
  def checkedLambda(e: Either[Problem, Int]): Either[Problems, Int] = e
  // E3 KEEP wrappers with different extra fields
  def mixed(b: Boolean): Either[Cli.Error, Int] =
    if b then Left(Cli.Error(Problem.Unclassified("a"), exitCode = ExitCode.Usage))
    else Left(Cli.Error(Problem.Unclassified("b")))
  // E4 KEEP uses a method that returns a wrapper (E3)
  def viaMixed(b: Boolean): Either[Cli.Error, Int] = mixed(b).map(_ + 1)
  // E5 KEEP a wrapper parameter
  def reads(e: Cli.Error): Either[Cli.Error, Int] = Left(e)
  // E6 KEEP extra fields from a local value
  def local(code: Int): Either[Cli.Error, Int] = Left(Cli.Error(Problem.Unclassified("x"), exitCode = code))
  // E7 KEEP used as a function value
  def valued(s: String): Either[Cli.Error, Int] = Left(Cli.Error(Problem.Unclassified(s)))
  val asValue: List[String] => List[Either[Cli.Error, Int]] = _.map(valued)
  // E8 CONVERT a parameterless method with a code
  def missing: Either[Problems, Int] = Left(Problem.Unclassified("gone"))
  // E9 KEEP writes the wrapper's type
  def typed(n: Int): Either[Api.Error, Int] =
    val e: Api.Error = Api.Error(Problem.Unclassified("typed"))
    if n > 0 then Right(n) else Left(e)
  // E10 CONVERT recursion keeps no adapter
  def countdown(n: Int): Either[Problems, Int] =
    if n < 0 then Left(Problem.Unclassified("below zero")) else if n == 0 then Right(0) else countdown(n - 1)

object Callers:
  // C1 every call outside the method rebuilds its wrapper
  def run(s: String): Either[Cli.Error, Int] =
    for
      n <- Parse.number(s).left.map(Cli.Error(_, exitCode = ExitCode.Usage))
      m <- Parse.number(s).left.map(Cli.Error(_, exitCode = ExitCode.Usage)).map(_ * 2)
    yield n + m
  def api(n: Int): Either[Api.Error, Int] = Parse.positive(n).left.map(Api.Error(_)).flatMap(_ => Parse.missing.left.map(Api.Error(_, Some(Api.Error.Code("not_found")))))
  def lambda(e: Either[Problem, Int]): Either[Api.Error, Int] = Parse.checked(e).left.map(Api.Error(_)).orElse(Parse.checkedLambda(e).left.map(Api.Error(_)))
  def down: Either[Api.Error, Int] = Parse.countdown(3).left.map(Api.Error(_))
