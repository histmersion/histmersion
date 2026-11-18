package demo

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

/** A wrapper like `Api.Error`. */
final case class Failure(problem: Problem)

object Demo:
  // U1 a literal, in Left
  def literal: Either[Problem, Int] = Left(Problem.Unclassified(Legacy.Text("no such file"))) // assert: UnclassifiedSites.Unclassified
  // U2 an interpolation, in a wrapper; the splice's type
  def interpolated(p: java.nio.file.Path): Failure = Failure(Problem.Unclassified(Legacy.Text(s"no such file: $p"))) // assert: UnclassifiedSites.Unclassified
  // U3 another problem's sentence spliced in
  def relay(inner: Problem): Problem = Problem.Unclassified(Legacy.Text(s"cannot start: ${inner.sentence}")) // assert: UnclassifiedSites.Unclassified
  // U4 an exception's message, three ways
  def message(e: Exception): Problem = Problem.Unclassified(Legacy.Text(s"failed: ${e.getMessage}")) // assert: UnclassifiedSites.Unclassified
  def thrown(e: Throwable): Problem = Problem.Unclassified.of(e) // assert: UnclassifiedSites.Unclassified
  def thrownText(e: Throwable): Problem = Problem.Unclassified(Legacy.Text.of(e)) // assert: UnclassifiedSites.Unclassified
  // U5 an exception spliced whole
  def exception(e: Throwable): Problem = Problem.Unclassified(Legacy.Text(s"failed: $e")) // assert: UnclassifiedSites.Unclassified
  // U6 a String failure, and the lambda that lifts it
  def parse(s: String): Either[String, Int] = s.toIntOption.toRight(s"not a number: $s") // assert: UnclassifiedSites.string
  def lifted(s: String): Either[Problem, Int] = parse(s).left.map(e => Problem.Unclassified(Legacy.Text(e))) // assert: UnclassifiedSites.Unclassified
  // U7 a multi-line text
  def multi(p: String): Problem = Problem.Unclassified( // assert: UnclassifiedSites.Unclassified
    Legacy.Text(s"""first $p
                   |second""".stripMargin)
  )
  // U8 another expression, in a branch
  def expression(m: String, b: Boolean): Option[Problem] =
    if b then Some(Problem.Unclassified(Legacy.Text(m.trim))) else None // assert: UnclassifiedSites.Unclassified
  // U9 a read of the temporary accessor
  def read(p: Problem): String = p.legacyText // assert: UnclassifiedSites.legacyText
  // U10 a String failure as a val
  val check: Either[String, Unit] = Right(()) // assert: UnclassifiedSites.string
  // U11 UNCHANGED a pattern, another failure type
  def matched(p: Problem): Boolean = p match
    case Problem.Unclassified(_) => true
  def other: Either[Problem, Int] = Right(1)
