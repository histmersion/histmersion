package com.histmersion
package domain.reviewarticle

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.research.CritiqueRef
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.reviewarticle.Severity
import com.histmersion.domain.reviewarticle.Quotation
import com.histmersion.domain.reviewarticle.Reviewer
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.Content
import com.histmersion.util.problem.Problem.Raw
import com.histmersion.api.Plural
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Citekey
import com.histmersion.util
import com.histmersion.Newtype
import com.histmersion.domain.ReviewArticle.MissingCritique
import com.histmersion.api.research.CritiquePair
import com.histmersion.domain.ReviewArticle.FactualKinds
import com.histmersion.domain.reviewarticle.CritiqueRule
import com.histmersion.domain.reviewarticle.Fix
import com.histmersion.domain.ReviewArticle.findQuote
import com.histmersion.domain.ReviewArticle.normalise
import com.histmersion.domain.ReviewArticle.key
import com.histmersion.api.research.Coverage
import com.histmersion.api.library.Citekey as ApiCitekey

@tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
final case class Finding(
    id: Finding.Id,
    reviewer: Reviewer,
    severity: Severity,
    kind: Finding.Kind,
    where: Finding.Where,
    quote: Finding.Quote,
    problem: Finding.Problem,
    fix: Finding.Fix,
    evidence: List[Quotation] = Nil,
    escalated: Finding.Escalated = Finding.Escalated(false),
    pair: Option[CritiqueRef] = None
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema:
  def major: Boolean = severity == Severity.Major

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def line: String =
    val at = Option(where).map(_.value.trim).filter(_.nonEmpty).fold("")(w => s" ($w)")
    s"$id ${severity.wire}$at: ${problem.value.trim}"

object Finding:

  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Where = Where.T
  object Where extends Newtype[String, Pure]
  export Quotation.Quote
  type Problem = Problem.T

  @tolerated(LintRule.G5Problem, "pending G60 (one typed Problem ADT)")
  object Problem extends Newtype[String, Pure]:
    type Section = Section.T
    object Section extends Newtype[String, Pure]
    type Count = Count.T
    object Count extends Newtype[Int, Pure]:
      extension (c: Count) def toInt: Int = c.value
    type Rest = Rest.T
    object Rest extends Newtype[String, Pure]

    enum Cause derives CanEqual:
      case UncitedParagraph

      case HandWrittenSourcesCited

      case MissingSection(alternatives: List[Section])

      case UnplacedSources(count: Count, listed: List[ApiCitekey])

      case TermCount(count: Count, rest: Rest)

      case HandWrittenNotUsed

      case HandWrittenMethod

      case Filler(matches: Rest)

      case Other(text: Problem)

    private val MissingRe = """^no ('[^']*'(?: or '[^']*')*) section \(writing guide rule 4\)$""".r
    private val UnplacedRe =
      """^\d+ sources? the filter returns (?:is|are) neither cited nor given a reason: (.+?)(?: \(\+(\d+) more\))?$""".r
    private val KeyRe = """\[\[([^\[\]]+)\]\]""".r
    private val FillerRe = """^banned filler — (.*)$""".r
    private val TermsRe = """^(\d+) terms ((?:are )?undefined\b.*)$""".r

    private def keys(listed: String): List[String] =
      val parts = listed.split(", ").toList
      if parts.nonEmpty && parts.forall(KeyRe.matches) then parts.map(p => KeyRe.findFirstMatchIn(p).get.group(1))
      else Nil

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def of(c: Cause): Problem = c match
      case Cause.UncitedParagraph        => Problem("a paragraph with no [[citekey]]")
      case Cause.HandWrittenSourcesCited => Problem("a hand-written Sources cited section (the page generates it)")
      case Cause.MissingSection(alts)    =>
        Problem(s"no ${alts.map(a => s"'$a'").mkString(" or ")} section (writing guide rule 4)")
      case Cause.UnplacedSources(n, listed) =>
        val more = n.toInt - listed.size
        Problem(
          s"${Plural.count(Plural.CountN(n.toInt.toLong), Plural.CountSingular("source"))} the filter returns ${
              if n.toInt == 1 then "is" else "are"
            } neither cited nor given a reason: ${listed.map(ck => s"[[$ck]]").mkString(", ")}${
              if more > 0 then s" (+$more more)" else ""
            }"
        )
      case Cause.HandWrittenNotUsed =>
        Problem("a hand-written Not used section: the page generates it from the article's filter and its reasons")
      case Cause.HandWrittenMethod =>
        Problem("a hand-written Method note: the page generates it (the filter, its counts, the statuses, the edges)")
      case Cause.Filler(matches)    => Problem(s"banned filler — $matches")
      case Cause.TermCount(n, rest) => Problem(s"$n terms $rest")
      case Cause.Other(text)        => text

    extension (c: Cause)
      def shown: Option[util.problem.Problem] =
        import util.problem.Problem.{Content, Raw}
        c match
          case Cause.HandWrittenSourcesCited => Some(Content.Finding(Content.Flaw.HandWrittenSourcesCited))
          case Cause.HandWrittenNotUsed      => Some(Content.Answer(Content.Requirement.NotUsedHandWritten))
          case Cause.HandWrittenMethod       => Some(Content.Answer(Content.Requirement.MethodHandWritten))
          case Cause.Filler(matches)         => Some(Content.Answer(Content.Requirement.NoFiller(Raw.of(matches))))
          case Cause.MissingSection(alts)    =>
            Some(Content.Answer(Content.Requirement.SectionPresent(alts.toVector.map(Raw.of(_)))))
          case _ => None

    extension (p: Problem)
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def cause: Cause =
        val text: String = p.value
        val read: Cause = text match
          case "a paragraph with no [[citekey]]"                              => Cause.UncitedParagraph
          case "a hand-written Sources cited section (the page generates it)" => Cause.HandWrittenSourcesCited
          case MissingRe(alts)                                                =>
            Cause.MissingSection(
              alts.split(" or ").toList.map(a => Section(a.stripPrefix("'").stripSuffix("'")))
            )
          case UnplacedRe(listed, more) =>
            val cks = keys(listed)
            Cause.UnplacedSources(
              Count(cks.size + Option(more).fold(0)(_.toInt)),
              cks.map(ck => ApiCitekey(ck))
            )
          case "a hand-written Not used section: the page generates it from the article's filter and its reasons" =>
            Cause.HandWrittenNotUsed
          case "a hand-written Method note: the page generates it (the filter, its counts, the statuses, the edges)" =>
            Cause.HandWrittenMethod
          case FillerRe(matches) => Cause.Filler(Rest(matches))
          case TermsRe(n, rest)  => Cause.TermCount(Count(n.toInt), Rest(rest))
          case _                 => Cause.Other(p)
        if of(read).value == text then read else Cause.Other(p)

      def head: String = p.value.takeWhile(_ != ':')

      def isUncitedParagraph: Boolean = p.cause == Cause.UncitedParagraph
      def isHandWrittenSourcesCited: Boolean = p.cause == Cause.HandWrittenSourcesCited
      def isMissingSection: Boolean = p.cause match
        case Cause.MissingSection(_) => true
        case _                       => false
      def isUnplacedSources: Boolean = p.cause match
        case Cause.UnplacedSources(_, _) => true
        case _                           => false

      def isTermCount: Boolean = TermsRe.matches(p.value.trim)

  type Fix = Fix.T
  object Fix extends Newtype[String, Pure]
  type Escalated = Escalated.T
  object Escalated extends Newtype[Boolean, Pure]

  extension (f: Finding)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def isMissingCritique: Boolean =
      f.kind.value.trim.toLowerCase(java.util.Locale.ROOT) == MissingCritique.value

  extension (f: Finding)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def finalRegression(
        start: List[CritiquePair],
        now: List[CritiquePair],
        edited: domain.reviewarticle.Fix.Body,
        minRatio: CritiqueRule.MinRatio
    ): Boolean =
      val pairBack = f.isMissingCritique && f.pair.exists(ref =>
        CritiquePair.find(start, ref).exists(!_.open) && CritiquePair.find(now, ref).forall(_.open)
      )
      val kind = f.kind.value.trim.toLowerCase(java.util.Locale.ROOT)
      val wrote = (FactualKinds(
        Kind(kind)
      ) || kind == "synth") && f.quote.value.trim.nonEmpty && edited.value.trim.nonEmpty &&
        findQuote(f.quote.value, edited.value, minRatio).accepted
      pairBack || wrote

  extension (f: Finding)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def scope(edited: domain.reviewarticle.Fix.Body, minRatio: CritiqueRule.MinRatio): (Finding, Option[String]) =
      val inside = f.quote.value.trim.nonEmpty && findQuote(f.quote.value, edited.value, minRatio).accepted
      if !f.major || inside || (FactualKinds(
          Kind(f.kind.value.trim.toLowerCase(java.util.Locale.ROOT))
        ) && f.evidence.nonEmpty)
      then (f, None)
      else
        (
          f.modify(_.severity).setTo(Severity.Minor),
          Some(
            s"${f.reviewer.wire}: \"${f.problem.value.take(80)}\" is outside the edited passages and not a factual error with evidence — recorded as minor"
          )
        )

  extension (a: Finding)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def sameFinding(b: Finding): Boolean =
      a.reviewer == b.reviewer && {
        val (qa, qb) = (normalise(a.quote.value), normalise(b.quote.value))
        if qa.nonEmpty && qb.nonEmpty then qa == qb else key(a) == key(b)
      }

  extension (f: Finding)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def needsFilter(coverage: Option[Coverage]): Boolean =
      f.major && coverage.isEmpty && f.kind.value.trim.equalsIgnoreCase("coverage")
