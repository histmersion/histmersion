package demo

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Problem.Run

object Demo:
  // S1 a literal
  def literal: Either[Problem, Int] = Left(Problem.Unclassified(Legacy.Text("no such file"))) // assert: SentenceSites.unclassified
  // S2 an interpolation, a splice in braces, literal braces; shown"…" is read as s"…"
  def interpolated(p: String, n: Int): Problem =
    Problem.Unclassified(Legacy.Text(shown"no such file: $p (${n + 1} {tries})")) // assert: SentenceSites.unclassified
  // S3 a concatenation
  def concatenated(p: String): Problem = Problem.Unclassified(Legacy.Text("missing " + p + "!")) // assert: SentenceSites.unclassified
  // S4 an exception's message, two ways
  def thrown(e: Throwable): Problem = Problem.Unclassified.of(e) // assert: SentenceSites.unclassified
  def thrownText(e: Throwable): Problem = Problem.Unclassified(Legacy.Text.of(e)) // assert: SentenceSites.unclassified
  // S5 another expression
  def expression(m: String): Problem = Problem.Unclassified(Legacy.Text(m.trim)) // assert: SentenceSites.unclassified
  // S6 a multi-line text
  def multiLine(p: String): Problem =
    Problem.Unclassified( // assert: SentenceSites.unclassified
      Legacy.Text(
        s"""first $p
           |second""".stripMargin
      )
    )
  // S7 a case through its companion's helper
  def helper: Problem = Problem.Invalid.Missing.here(Problem.Field("rationale")) // assert: SentenceSites.case
  // S8 a case with a nested vocabulary value, two variants
  def nested: Problem = Problem.Config.Setting("k", Problem.Config.Fault.Empty) // assert: SentenceSites.case
  def nestedRejected: Problem = Problem.Config.Setting("k", Problem.Config.Fault.Rejected("no")) // assert: SentenceSites.case
  // S9 a case object, written out and imported
  def caseObject: Problem = Problem.Run.Interrupted // assert: SentenceSites.case
  def imported: Problem = Run.Interrupted // assert: SentenceSites.case
  // S10 problems inside another's arguments are one site
  def wrapped: Problem = Run.Failed(Vector(Problem.Run.Interrupted, Problem.Unclassified(Legacy.Text("inner")))) // assert: SentenceSites.case
  // S11 two sites in one definition: #0 and #1
  val twoInOne: Vector[Problem] = Vector(Problem.Run.Interrupted, Problem.Run.Interrupted) // assert: SentenceSites.case
  // S12 UNCHANGED patterns, types and newtypes are no sites
  def matched(p: Problem): Boolean = p match
    case Problem.Run.Interrupted => true
    case Problem.Unclassified(_) => false
    case _                       => false
  def typed: Option[Problem.Invalid.Missing] = None
  def field: Problem.Field = Problem.Field("x")
  // S13 a local definition belongs to the named one around it
  def local: Problem =
    def inner = Problem.Unclassified(Legacy.Text("local")) // assert: SentenceSites.unclassified
    inner
  // S14 escapes and a dollar are read as the string reads them
  def escaped(p: String): Problem = Problem.Unclassified(Legacy.Text(s"say \"$p\" for $$5")) // assert: SentenceSites.unclassified
  extension (context: StringContext) def shown(parts: Any*): String = context.s(parts*)
