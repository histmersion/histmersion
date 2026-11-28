package com.histmersion
package domain

import com.histmersion.domain.research.workflows.Merged
import com.histmersion.domain.research.workflows.Finding
import com.histmersion.domain.research.workflows.Reviewer
import com.histmersion.domain.research.workflows.Fix
import com.histmersion.domain.research.workflows.Edited
import com.histmersion.domain.research.workflows.Excerpt
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec

import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import scala.annotation.tailrec
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.{Plural, WireEnum}
import com.histmersion.api.research.CritiqueRef
import com.histmersion.api.given

import com.histmersion.util.json.JsonCodec

import com.histmersion.domain.ReviewArticle.*
import com.histmersion.api.WireEnumValues
import com.histmersion.domain.research.workflows.ReviewComparison
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.automation.Run
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** The review ledger of one article (docs/research-review-convergence.md §6): every finding the `review-article` panel
  * ever raised on it, under an id that never changes, with what became of it — open, fixed (verified by the reviewer
  * that raised it), rejected (with the record's sentence) or won't fix — and the lessons learned from rejections and
  * failed fixes. It persists across runs, so a later run starts from it; the reviewers and the fixer read it in their
  * prompts.
  *
  * Everything here is pure: the pipeline loads the ledger, threads it through its passes as a value (each pass's new
  * ledger is a checkpointed step output) and writes it back through the run's gate.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@movesTo(FeaturePlace.ResearchWorkflows)
object ReviewLedger:
  type ClosedLimit = ClosedLimit.T
  object ClosedLimit extends Newtype[Int, Pure]
  type PassageMargin = PassageMargin.T
  object PassageMargin extends Newtype[Int, Pure]
  type StubbornLimit = StubbornLimit.T
  object StubbornLimit extends Newtype[Int, Pure]
  type TaskText = TaskText.T
  object TaskText extends Newtype[String, Pure]
  type TaskTextLimit = TaskTextLimit.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object TaskTextLimit extends Newtype[Int, Pure]

  export automation.Run.Id as ApplyChecksRun
  export Merged.Round as ApplyChecksRound
  export automation.Run.Id as RecheckMechanicalRun
  export Merged.Round as RecheckMechanicalRound
  export automation.Run.Id as RecheckTermCountRun
  export Merged.Round as RecheckTermCountRound
  export automation.Run.Id as ApplyFixRun
  export Merged.Round as ApplyFixRound
  export automation.Run.Id as LearnRun
  export Merged.Round as LearnRound
  export automation.Run.Id as ApplyRecheckRun
  export Merged.Round as ApplyRecheckRound
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type ArticleTaskSlug = research.Slug
  val ArticleTaskSlug: research.Slug.type = research.Slug

  /** At most this many lessons are kept (the newest). */
  val MaxLessons = 15

  // ── matching ───────────────────────────────────────────────────────────────────────────────────

  /** A mechanical finding is the same one when code says the same thing about the same passage: its kind, its place,
    * its problem up to the first colon (a coverage count changes as reasons are given; the passage does not) and its
    * quote.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def mechanicalKey(f: Finding): (String, String, String, String) =
    (
      f.kind.value,
      normalised(f.where.value).value,
      normalised(f.problem.head).value,
      normalised(f.quote.value).value
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[domain] def normalised(s: String): AgentVerify.NormalisedText = AgentVerify.normalise(AgentVerify.Text(s))

  /** Is `b` the finding `a` again? The same reviewer, and the same passage (quotes equal, or one inside the other when
    * both are at least 20 characters), or the same `where` and a problem that shares at least half its words
    * ([[CompareRuns.similarity]]). Mechanical findings compare by [[mechanicalKey]]; the cold reader's term-count
    * findings ([[TermCount]]) are one finding whatever the count or wording.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def same(a: Finding, b: Finding): Boolean =
    a.reviewer == b.reviewer && {
      if a.reviewer == Reviewer.Mechanical then mechanicalKey(a) == mechanicalKey(b)
      else if TermCount.is(a) || TermCount.is(b) then TermCount.is(a) && TermCount.is(b)
      else
        val (qa, qb) = (normalised(a.quote.value).value, normalised(b.quote.value).value)
        val quoted = qa.nonEmpty && qb.nonEmpty &&
          (qa == qb || (math.min(qa.length, qb.length) >= 20 && (qa.contains(qb) || qb.contains(qa))))
        quoted || (normalised(a.where.value) == normalised(b.where.value) &&
          CompareRuns
            .similarity(CompareRuns.Text(a.problem.value), CompareRuns.Text(b.problem.value))
            .value >= ReviewComparison.ProblemSimilarity)
    }

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def paragraphs(body: Fix.Body): Vector[String] =
    body.value.replace("\r\n", "\n").split("\n[ \t]*\n").map(_.trim).filter(_.nonEmpty).toVector

  private val Heading = "^#{1,6}\\s+(.+?)\\s*#*\\s*$".r

  /** The passages a fix changed: a paragraph-level diff of `before` and `after` (longest common subsequence of
    * whitespace-normalised paragraphs), each changed paragraph of `after` widened by `margin` paragraphs on each side,
    * adjacent ones joined; plus the paragraphs of `before` that are gone.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def editedPassages(
      before: Fix.Body,
      after: Fix.Body,
      margin: PassageMargin = PassageMargin(1)
  ): Edited =
    val a = paragraphs(before)
    val b = paragraphs(after)
    def key(p: String) = p.replaceAll("\\s+", " ")
    val (ka, kb) = (a.map(key), b.map(key))
    // LCS table, suffix form: t(i)(j) = LCS of a.drop(i), b.drop(j)
    val t = Array.ofDim[Int](a.size + 1, b.size + 1)
    for i <- a.indices.reverse; j <- b.indices.reverse do
      t(i)(j) = if ka(i) == kb(j) then t(i + 1)(j + 1) + 1 else math.max(t(i + 1)(j), t(i)(j + 1))
    @annotation.tailrec
    def walk(i: Int, j: Int, keptA: Set[Int], keptB: Set[Int]): (Set[Int], Set[Int]) =
      if i >= a.size || j >= b.size then (keptA, keptB)
      else if ka(i) == kb(j) then walk(i + 1, j + 1, keptA + i, keptB + j)
      else if t(i + 1)(j) >= t(i)(j + 1) then walk(i + 1, j, keptA, keptB)
      else walk(i, j + 1, keptA, keptB)
    val (keptA, keptB) = walk(0, 0, Set.empty, Set.empty)
    val changed = b.indices.filterNot(keptB)
    val m = math.max(0, margin.value)
    val shown = changed.flatMap(i => (i - m) to (i + m)).filter(b.indices.contains).distinct.sorted
    val runs = shown
      .foldLeft(List.empty[List[Int]]) {
        case (cur :: rest, i) if cur.head == i - 1 => (i :: cur) :: rest
        case (acc, i)                              => List(i) :: acc
      }
      .reverse
      .map(_.reverse)
    def headingOf(i: Int): Option[Excerpt.Heading] =
      (i to 0 by -1).iterator.map(b(_)).collectFirst {
        case p if p.linesIterator.nextOption().exists(Heading.matches) =>
          Excerpt.Heading(p.linesIterator.next() match { case Heading(h) => h.trim; case h => h })
      }
    Edited(
      runs.map(r => Excerpt(headingOf(r.head), Excerpt.Text(r.map(b(_)).mkString("\n\n")))),
      a.indices.filterNot(keptA).map(i => Edited.Removed(a(i))).toList
    )

  import com.histmersion.domain.research.workflows.Regression.Rationale

  /** The text of the article's one collapsed item — as [[taskText]] writes it, or as the interactive skill does
    * (`review-article <slug>: 2 open MAJORs — …`) — so a run whose ledger lost the item's id rewrites the open one
    * instead of adding a second. Not a [[legacyTask]] (those are closed).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def articleTask(slug: ArticleTaskSlug, text: TaskText): Boolean =
    val t = text.value.trim
    !legacyTask(slug, TaskText(t)) &&
    (t.startsWith(s"review-article [[$slug]]: ") || t.startsWith(s"review-article $slug: ")) &&
    "^review-article \\S+: \\d+ open MAJORs?\\b".r.findPrefixOf(t).isDefined

  /** The text of the per-finding items earlier runs left for the article (before #934). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def legacyTask(slug: research.Slug, text: TaskText): Boolean =
    text.value.startsWith(s"review-article [[$slug]]: MAJOR ")
