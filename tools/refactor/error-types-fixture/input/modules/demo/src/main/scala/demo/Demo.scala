package demo

import com.histmersion.api.Api
import com.histmersion.cli.{Cli, ExitCode}
import com.histmersion.util.problem.Problem

object Parse:
  // E1 CONVERT a leaf with one extra field
  def number(s: String): Either[Cli.Error, Int] =
    s.toIntOption.toRight(Cli.Error(Problem.Unclassified(s"not a number: $s"), exitCode = ExitCode.Usage))
  // E2 CONVERT no extra field: two constructions; an adapter; a lambda adapter
  def positive(n: Int): Either[Api.Error, Int] =
    if n > 0 then Right(n) else Left(Api.Error(Problem.Unclassified("not positive")))
  def checked(e: Either[Problem, Int]): Either[Api.Error, Int] = e.left.map(Api.Error(_))
  def checkedLambda(e: Either[Problem, Int]): Either[Api.Error, Int] = e.left.map(p => Api.Error(p))
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
  def missing: Either[Api.Error, Int] = Left(Api.Error(Problem.Unclassified("gone"), Some(Api.Error.Code("not_found"))))
  // E9 KEEP writes the wrapper's type
  def typed(n: Int): Either[Api.Error, Int] =
    val e: Api.Error = Api.Error(Problem.Unclassified("typed"))
    if n > 0 then Right(n) else Left(e)
  // E10 CONVERT recursion keeps no adapter
  def countdown(n: Int): Either[Api.Error, Int] =
    if n < 0 then Left(Api.Error(Problem.Unclassified("below zero"))) else if n == 0 then Right(0) else countdown(n - 1)

object Callers:
  // C1 every call outside the method rebuilds its wrapper
  def run(s: String): Either[Cli.Error, Int] =
    for
      n <- Parse.number(s)
      m <- Parse.number(s).map(_ * 2)
    yield n + m
  def api(n: Int): Either[Api.Error, Int] = Parse.positive(n).flatMap(_ => Parse.missing)
  def lambda(e: Either[Problem, Int]): Either[Api.Error, Int] = Parse.checked(e).orElse(Parse.checkedLambda(e))
  def down: Either[Api.Error, Int] = Parse.countdown(3)
