package com.histmersion
package domain.reviewarticle

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.research.Coverage
import com.histmersion.api.research.CritiqueReason
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.api.research.Article
import com.histmersion.domain.reviewarticle.Rejection
import com.histmersion.domain.reviewarticle.SetAside
import com.histmersion.domain.reviewarticle.WontFix
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.reviewarticle.Finding
import com.histmersion.Newtype
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.ReviewArticle.Sentence
import com.histmersion.domain.reviewarticle.Evidence
import com.histmersion.domain.reviewarticle.CritiqueRule
import com.histmersion.domain.ReviewArticle.findQuote
import com.histmersion.api.research.CritiquePair

final case class Fix(
    body: Fix.Body,
    edits: List[Article.Edits.EditResult],
    applied: List[Fix.Applied],
    rejected: List[Rejection],
    unverified: List[Fix.Unverified],
    unresolvedMoved: List[Fix.UnresolvedMoved],
    notes: Fix.Notes,
    wordsBefore: Fix.WordsBefore,
    wordsAfter: Fix.WordsAfter,
    /** The coverage reasons the fixer set (an empty one removes), already checked against the coverage. */
    reasons: List[Coverage.Reason] = Nil,
    /** Minor findings declined, with the reason. */
    wontFix: List[WontFix] = Nil,
    /** Missing-critique findings set aside as not material, each with the pair and the reason stored. */
    setAside: List[SetAside] = Nil,
    /** Findings code's pairing repair answered (§6.9): claimed like the applied ones. */
    repaired: List[Fix.Repaired] = Nil,
    /** What the pairing repair did, one line per citation added. */
    repairs: List[Fix.Repairs] = Nil,
    /** Findings the fixer neither applied nor rejected, or claimed without the change code checks for: each with why —
      * the ledger records it as not fixed (§6.9).
      */
    notFixed: List[WontFix] = Nil,
    /** Pairs of the checklist set aside with no finding (a pair the revision brought in). */
    pairsAside: List[CritiqueReason] = Nil
) derives JsonCodec

object Fix:
  export Article.Body
  export Finding.{Id as Applied, Id as Unverified}
  type UnresolvedMoved = UnresolvedMoved.T
  object UnresolvedMoved extends Newtype[String, Pure]
  type Notes = Notes.T
  object Notes extends Newtype[String, Pure]
  export Article.Edits.ArticleCheck.{WordsAfter, WordsBefore}
  export Finding.Id as Repaired
  type Repairs = Repairs.T
  object Repairs extends Newtype[String, Pure]

  private val HeadingLine = "^#{1,6}\\s+(.+?)\\s*#*\\s*$".r

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def headings(body: Fix.Body): List[String] =
    body.value.linesIterator.collect { case HeadingLine(h) => h.trim }.toList

  private val SentenceEnd = "(?<=[.!?])[\"”’)]*\\s+(?=[\\p{Lu}\"“(\\[*])".r

  private val Abbrev = Set(
    "al",
    "c",
    "ca",
    "cf",
    "e.g",
    "i.e",
    "etc",
    "vs",
    "fl",
    "st",
    "mt",
    "no",
    "vol",
    "pp",
    "p",
    "ed",
    "eds",
    "fig",
    "dr",
    "mr",
    "mrs",
    "ms",
    "prof",
    "ff",
    "ch",
    "repr",
    "trans",
    "n"
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def sentences(p: Fix.Body): List[Sentence] =
    val text = p.value
    val cuts = SentenceEnd
      .findAllMatchIn(text)
      .filter { m =>
        val word = text
          .substring(0, m.start)
          .split("[\\s(\\[]")
          .lastOption
          .getOrElse("")
          .stripSuffix(".")
          .stripSuffix(".")
          .replaceAll("^[^\\p{L}]+", "")
          .toLowerCase(java.util.Locale.ROOT)
        !(Abbrev(word) || (word.length == 1 && word.head.isLetter))
      }
      .map(_.end)
      .toList
    (0 :: cuts).zip(cuts :+ text.length).map((a, b) => text.substring(a, b).trim).filter(_.nonEmpty).map(Sentence(_))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def claimSentence(body: Fix.Body, quote: Evidence.Quote, minRatio: CritiqueRule.MinRatio): Option[Sentence] =
    val q = Option(quote).map(_.value.trim).getOrElse("")
    def has(t: String) = findQuote(q, t, minRatio).accepted
    if q.isEmpty then None
    else
      CritiquePair.paragraphs(body).map(_.value).find(has).map { p =>
        val ss = sentences(Fix.Body(p)).map(_.value)
        Sentence(
          (1 to math.min(4, ss.size)).iterator.flatMap(n => ss.sliding(n).map(_.mkString(" "))).find(has).getOrElse(p)
        )
      }
