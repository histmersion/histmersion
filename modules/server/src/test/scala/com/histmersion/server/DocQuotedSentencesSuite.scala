package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.regex.Pattern
import scala.jdk.CollectionConverters.*

import com.histmersion.domain.CoreQuota
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

/** The sentences of Histmersion's that the documents quote (`architecture/quoted-sentences.tsv`) are the ones it
  * renders: each quote is still in its document, and some sentence of [[Problem.examples]] (or of the few cases below,
  * and the CORE budget's refusals) has its shape. A changed sentence fails here until the document is updated.
  */
class DocQuotedSentencesSuite extends munit.FunSuite:
  import DocQuotedSentencesSuite.*

  test("every quoted sentence is still in its document, and Histmersion still renders one of its shape"):
    val failures = Quote.read(Files.readAllLines(QuotesFile, UTF_8).asScala.toList).flatMap { q =>
      val text = Quote.collapse(Files.readString(Path.of(q.file), UTF_8))
      val inDoc = Option.unless(text.contains(Quote.collapse(q.quote)))(s"${q.file}: no longer quotes ${q.quote}")
      val rendered = Option.unless(Rendered.exists(Shape.matches(q.sentence, _)))(
        s"${q.file}: nothing Histmersion renders has the shape '${q.sentence}' — update the document"
      )
      inDoc.toList ++ rendered.toList
    }
    assert(failures.isEmpty, failures.mkString("\n"))

  test("a shape: … is any text, a lone capital letter one value, the rest itself"):
    assert(Shape.matches("'X' is not a Citekey: Should match …", "'Gimbutas 1956' is not a Citekey: Should match [a-z]+"))
    assert(Shape.matches("p needs X Y", "p needs m 1.0"))
    assert(!Shape.matches("invalid citekey 'X' …", "'Gimbutas 1956' is not a Citekey: Should match [a-z]+"))
    assert(!Shape.matches("CORE not asked: …", "CORE asked to wait until 2026-01-01T00:00:00Z (HTTP 429)"))

  test("a row is a file, its quote and the shape; comments and blank lines are not rows"):
    assertEquals(
      Quote.read(List("# a comment", "", "docs/a.md\t`q`\ts …")),
      List(Quote("docs/a.md", "`q`", "s …"))
    )
    assertEquals(Quote.collapse("a\n  b  c"), "a b c")

object DocQuotedSentencesSuite:

  val QuotesFile: Path = Path.of("architecture/quoted-sentences.tsv")

  /** The cases the documents quote that [[Problem.examples]] holds with other values. */
  val Quoted: Vector[Problem] = Vector(
    Problem.Plugin.Load(
      Problem.Plugin.Id("doi-mirror"),
      Problem.Plugin.Fault.SpiMismatch(Problem.Raw("1"), Problem.Raw("2"))
    )
  )

  /** Every sentence a quote may be checked against. */
  val Rendered: Vector[String] =
    (Problem.examples ++ Quoted).map(_.sentence) ++
      CoreQuota.Purpose.values.toVector.map(p =>
        CoreQuota.Refusal.OverBudget(p, CoreQuota.Refusal.OverBudget.Spent(6), CoreQuota.Refusal.OverBudget.Cap(6))
          .describe.toString
      )

  final case class Quote(file: String, quote: String, sentence: String) derives CanEqual

  object Quote:
    def read(rows: List[String]): List[Quote] =
      rows.filter(r => r.trim.nonEmpty && !r.startsWith("#")).map { r =>
        val cols = r.split("\t", 3)
        Quote(cols(0), cols(1), cols(2))
      }

    /** The text with every run of white space one space: a quote may break across lines. */
    def collapse(text: String): String = text.replaceAll("\\s+", " ").trim

  object Shape:
    private val Part = "…|\\b[A-Z]\\b".r

    /** Does `sentence` (the end of a document's sentence may be cut by `…`) have the shape? */
    def matches(shape: String, sentence: String): Boolean =
      val pieces = Part.split(shape).map(Pattern.quote)
      val holes = Part.findAllIn(shape).map(h => if h == "…" then ".*" else ".+?").toVector
      val regex = pieces.zipAll(holes, "", "").map(_ + _).mkString
      sentence.matches(s"(?s)$regex")
