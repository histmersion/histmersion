package com.histmersion
package domain

import com.histmersion.domain.reviewarticle.BodyCorpus
import com.histmersion.domain.reviewarticle.Quotation
import com.histmersion.domain.reviewarticle.Merged
import com.histmersion.domain.reviewarticle.CritiqueRule
import com.histmersion.domain.reviewarticle.Snapshot
import com.histmersion.domain.reviewarticle.Fix.headings
import com.histmersion.domain.reviewarticle.Reviewer
import com.histmersion.domain.reviewarticle.EvidenceAnswer
import com.histmersion.domain.reviewarticle.Report
import com.histmersion.domain.reviewarticle.SpotCheck
import com.histmersion.domain.reviewarticle.SpotCheckAnswer
import com.histmersion.domain.reviewarticle.SpotRule
import com.histmersion.domain.reviewarticle.ColdReaderAnswer
import com.histmersion.domain.reviewarticle.NotMaterialAnswer
import com.histmersion.domain.reviewarticle.Prior
import com.histmersion.domain.reviewarticle.CheckAnswer
import com.histmersion.domain.reviewarticle.Check
import com.histmersion.domain.reviewarticle.SetAsideAnswer
import com.histmersion.domain.reviewarticle.SetAside
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.{Content, Raw}
import com.histmersion.util.problem.Problem.Content.PanelRule
import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.{Plural, WireEnum}
import com.histmersion.api.research.{
  CitedSource,
  Coverage,
  CritiquePair,
  CritiquePairing,
  CritiqueReason,
  CritiqueRef,
  CritiqueState,
  DriftKind
}
import com.histmersion.api.checks.Lint
import com.histmersion.api.given

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.WireEnumValues
import com.histmersion.api.automation.Approval
import com.histmersion.api.research.Article
import com.histmersion.api.library.Citekey as ApiCitekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.automation.agents.ToolUse
import com.histmersion.domain.automation.Run
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** The `review-article` pipeline's typed answers and its pure logic (docs/research-embedded-agents.md §4.3): the
  * mechanical checks, the validation of each reviewer's answer, the merge of the panel (worst verdict wins,
  * deduplicated, escalation of findings the fixer rejected and a reviewer raised again), the gate, the validation of
  * the fixer's revision (a rejection must quote the record) and the bookkeeping lines. The server runs the steps;
  * everything here is pure and table-tested.
  *
  * JVM-only, like [[EnrichSource]], because quote matching needs `java.text.Normalizer`.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@movesTo(FeaturePlace.ResearchWorkflows)
object ReviewArticle:

  /** Why a finding is not fixed: the fixer's words, or the account of what the fixer left; not a failure. */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]
  type CitedInCorpusCount = CitedInCorpusCount.T
  object CitedInCorpusCount extends Newtype[Int, Pure]
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  type Criticised = com.histmersion.api.library.Citekey
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  val Criticised: com.histmersion.api.library.Citekey.type = com.histmersion.api.library.Citekey
  type DefaultRounds = DefaultRounds.T
  object DefaultRounds extends Newtype[Int, Pure]
  type FindingCount = FindingCount.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object FindingCount extends Newtype[Int, Pure]
  type FixRun = FixRun.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object FixRun extends Newtype[Boolean, Pure]
  type LastRound = LastRound.T
  object LastRound extends Newtype[Boolean, Pure]
  type RequestedRounds = RequestedRounds.T
  object RequestedRounds extends Newtype[Int, Pure]
  type RoundCap = RoundCap.T
  object RoundCap extends Newtype[Int, Pure]
  type RoundCount = RoundCount.T
  object RoundCount extends Newtype[Int, Pure]
  type Sentence = Sentence.T
  object Sentence extends Newtype[String, Pure]
  type SpotCheckCount = SpotCheckCount.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object SpotCheckCount extends Newtype[Int, Pure]
  type SpotCheckMin = SpotCheckMin.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object SpotCheckMin extends Newtype[Int, Pure]
  type TurnCap = TurnCap.T
  object TurnCap extends Newtype[Int, Pure]

  // ── vocabularies ───────────────────────────────────────────────────────────────────────────────

  /** Who raised a finding. The prefix is the finding id's letter (`R3`, `C1`, `S2`, `M1`). */

  type ExplainSlots = ExplainSlotsValue
  type ExplainSlotsValue = ExplainSlotsValue.T
  object ExplainSlotsValue extends Newtype[String, Pure]
  export Article.Body as MechanicalBody
  export BodyCorpus.Generated as MechanicalGenerated
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type VerdictOfLast = LastRound
  val VerdictOfLast: LastRound.type = LastRound
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type CheckRulesLast = LastRound
  val CheckRulesLast: LastRound.type = LastRound
  type SpotCheckTools = SpotCheckToolsValue
  export ToolUse.Tool as SpotCheckToolsValue
  type SpotVerdicts = SpotVerdictsValue
  type SpotVerdictsValue = SpotVerdictsValue.T
  object SpotVerdictsValue extends Newtype[String, Pure]
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type CheckSpotChecksLast = LastRound
  val CheckSpotChecksLast: LastRound.type = LastRound
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type CheckColdReaderLast = LastRound
  val CheckColdReaderLast: LastRound.type = LastRound
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type CheckSkepticLast = LastRound
  val CheckSkepticLast: LastRound.type = LastRound
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type LabelledSentence = Sentence
  val LabelledSentence: Sentence.type = Sentence
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type QualifiedClaimSentence = Sentence
  val QualifiedClaimSentence: Sentence.type = Sentence
  export Quotation.Quote as QualifiedClaimQuote
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type QualifiedClaimCriticised = Criticised
  val QualifiedClaimCriticised: Criticised.type = Criticised
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type CheckCritiquesLast = LastRound
  val CheckCritiquesLast: LastRound.type = LastRound
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type CheckNotMaterialLast = LastRound
  val CheckNotMaterialLast: LastRound.type = LastRound
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type ChecksOfLast = LastRound
  val ChecksOfLast: LastRound.type = LastRound
  export Article.Body as CheckRecheckEdited
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type CheckRecheckLast = LastRound
  val CheckRecheckLast: LastRound.type = LastRound
  export Merged.Round as MergeRound

  /** The four slots of the cold reader's explain-back (question, answer, disagreement, open). */
  val ExplainSlots: List[ExplainSlots] = List(
    ExplainSlotsValue("question"),
    ExplainSlotsValue("answer"),
    ExplainSlotsValue("disagreement"),
    ExplainSlotsValue("open")
  )

  /** At most this many records per finding (more are dropped unread). */
  val MaxEvidence = 3

  private[domain] def ChecksDescription: String =
    "One check per earlier finding of yours that the review ledger lists as open (empty when it lists none)"

  /** One line per source the article cites — `[[citekey]] · year · status · title` — so a reviewer reads every cited
    * status from the brief instead of querying for it (a Codex rules step selected the whole `sources` table for it,
    * 2026-10-16). Empty when it cites none.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def citedLines(cited: List[CitedSource]): String =
    cited
      .map { c =>
        val status = c.status.fold("not in the corpus")(_.wire)
        val title = c.title.map(_.value).map(t => if t.length > 120 then t.take(119).trim + "…" else t)
        (s"- [[${c.citekey}]]" :: List(c.year.map(_.value.toInt).map(_.toString), Some(status), title).flatten)
          .mkString(" · ")
      }
      .mkString("\n")

  private def norm(h: String): String = h.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z ]", "").trim

  /** The finding kind of the coverage checks (#958): hand-written generated sections, and drift. */
  val CoverageKind: domain.reviewarticle.Finding.Kind = domain.reviewarticle.Finding.Kind("coverage")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[domain] def findQuote(quote: String, body: String, minRatio: CritiqueRule.MinRatio): AgentVerify.QuoteMatch =
    AgentVerify.findQuote(domain.Proposal.Quote(quote), AgentVerify.SourceBody(body), minRatio)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[domain] def normalise(s: String): String = AgentVerify.normalise(AgentVerify.Text(s)).value

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def wikilinks(text: String): List[String] = AgentVerify.wikilinks(AgentVerify.Text(text)).map(_.value)

  /** The checks code can make with certainty, as `M<n>` findings: banned filler, a paragraph with no `[[citekey]]`,
    * escaped link brackets, a hand-written Sources cited heading, a missing compulsory section, a duplicated heading,
    * and `[[links]]` that resolve to nothing. With `generated` (the article records a filter, #958) a hand-written Not
    * used section or `_Method_` note is one too: code generates both, so a written one duplicates them and goes stale.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def mechanical(
      slug: research.Slug,
      kind: Snapshot.Kind,
      body: MechanicalBody,
      isTarget: research.Slug => Boolean,
      generated: MechanicalGenerated = MechanicalGenerated(false)
  ): List[domain.reviewarticle.Finding] =
    val hs = headings(body)
    def f(
        sev: domain.reviewarticle.Severity,
        kind: domain.reviewarticle.Finding.Kind,
        where: domain.reviewarticle.Finding.Where,
        quote: domain.reviewarticle.Finding.Quote,
        problem: domain.reviewarticle.Finding.Problem,
        fix: domain.reviewarticle.Finding.Fix
    ) =
      (sev, kind, where, quote, problem, fix)
    val articleSlug = Article.Slug(slug.value)
    val filler = Article.Lint
      .articleFiller(List(articleSlug -> body))
      .map(i =>
        f(
          domain.reviewarticle.Severity.Minor,
          domain.reviewarticle.Finding.Kind("language"),
          domain.reviewarticle.Finding.Where("body"),
          domain.reviewarticle.Finding.Quote(""),
          domain.reviewarticle.Finding.Problem.of(
            domain.reviewarticle.Finding.Problem.Cause
              .Filler(domain.reviewarticle.Finding.Problem.Rest(i.detail.fillerMatches))
          ),
          domain.reviewarticle.Finding.Fix("rewrite the sentence")
        )
      )
    // the lint rule's own test, one paragraph at a time (a link to an article is not a citation)
    val uncited = Lint
      .extractParagraphs(Lint.ProseBody(body.value))
      .filter(p => Article.Lint.articleUncitedParagraphs(List(articleSlug -> Article.Body(p.value))).nonEmpty)
      .map(p =>
        f(
          domain.reviewarticle.Severity.Major,
          domain.reviewarticle.Finding.Kind("citation"),
          domain.reviewarticle.Finding.Where(p.value.split("\\s+").take(8).mkString(" ")),
          domain.reviewarticle.Finding.Quote(p.value.take(200)),
          domain.reviewarticle.Finding.Problem.of(domain.reviewarticle.Finding.Problem.Cause.UncitedParagraph),
          domain.reviewarticle.Finding
            .Fix("cite the source the paragraph rests on, or move it to the unresolved section")
        )
      )
    val escaped = Option.when(body.value.contains("\\[\\["))(
      f(
        domain.reviewarticle.Severity.Major,
        domain.reviewarticle.Finding.Kind("citation"),
        domain.reviewarticle.Finding.Where("body"),
        domain.reviewarticle.Finding.Quote("\\[\\["),
        domain.reviewarticle.Finding.Problem("escaped link brackets render as text and link nothing"),
        domain.reviewarticle.Finding.Fix("write [[citekey]] unescaped")
      )
    )
    val sourcesCited = hs
      .filter(h => norm(h) == "sources cited")
      .take(1)
      .map(h =>
        f(
          domain.reviewarticle.Severity.Major,
          domain.reviewarticle.Finding.Kind("section"),
          domain.reviewarticle.Finding.Where(h),
          domain.reviewarticle.Finding.Quote(s"## $h"),
          domain.reviewarticle.Finding.Problem.of(domain.reviewarticle.Finding.Problem.Cause.HandWrittenSourcesCited),
          domain.reviewarticle.Finding.Fix("delete the section")
        )
      )
    val handWritten =
      if !generated.value then Nil
      else
        hs.filter(h => norm(h).startsWith("not used"))
          .take(1)
          .map(h =>
            f(
              domain.reviewarticle.Severity.Major,
              CoverageKind,
              domain.reviewarticle.Finding.Where(h),
              domain.reviewarticle.Finding.Quote(s"## $h"),
              domain.reviewarticle.Finding.Problem.of(domain.reviewarticle.Finding.Problem.Cause.HandWrittenNotUsed),
              domain.reviewarticle.Finding
                .Fix("delete the section; give each left-out source's reason in `reasons` instead")
            )
          ) ++
          body.value.linesIterator
            .map(_.trim)
            .find(_.startsWith("_Method"))
            .toList
            .map(l =>
              f(
                domain.reviewarticle.Severity.Major,
                CoverageKind,
                domain.reviewarticle.Finding.Where("Method"),
                domain.reviewarticle.Finding.Quote(l.take(200)),
                domain.reviewarticle.Finding.Problem.of(domain.reviewarticle.Finding.Problem.Cause.HandWrittenMethod),
                domain.reviewarticle.Finding.Fix("delete the note")
              )
            )
    val missing = Snapshot
      .requiredSections(kind, generated)
      .filterNot(alts => hs.exists(h => alts.exists(a => norm(h).startsWith(a))))
      .map(alts =>
        f(
          domain.reviewarticle.Severity.Major,
          domain.reviewarticle.Finding.Kind("section"),
          domain.reviewarticle.Finding.Where("headings"),
          domain.reviewarticle.Finding.Quote(""),
          domain.reviewarticle.Finding.Problem.of(
            domain.reviewarticle.Finding.Problem.Cause
              .MissingSection(alts.map(domain.reviewarticle.Finding.Problem.Section(_)))
          ),
          domain.reviewarticle.Finding.Fix("add the section")
        )
      )
    val dup = hs
      .groupBy(norm)
      .collect { case (_, xs) if xs.size > 1 => xs.head }
      .toList
      .sorted
      .map(h =>
        f(
          domain.reviewarticle.Severity.Minor,
          domain.reviewarticle.Finding.Kind("section"),
          domain.reviewarticle.Finding.Where(h),
          domain.reviewarticle.Finding.Quote(s"## $h"),
          domain.reviewarticle.Finding.Problem("a duplicated heading"),
          domain.reviewarticle.Finding.Fix("merge the two sections")
        )
      )
    val dangling = wikilinks(body.value)
      .filterNot(t => research.Slug.option(t).exists(isTarget))
      .map(t =>
        f(
          domain.reviewarticle.Severity.Major,
          domain.reviewarticle.Finding.Kind("citation"),
          domain.reviewarticle.Finding.Where(s"[[$t]]"),
          domain.reviewarticle.Finding.Quote(s"[[$t]]"),
          domain.reviewarticle.Finding.Problem(s"[[$t]] is neither a citekey nor an article"),
          domain.reviewarticle.Finding.Fix("link a real citekey or article")
        )
      )
    (filler ++ uncited ++ escaped ++ sourcesCited ++ handWritten ++ missing ++ dup ++ dangling).zipWithIndex.map {
      case ((sev, k, w, q, p, fx), i) =>
        domain.reviewarticle.Finding(
          domain.reviewarticle.Finding.Id(s"M${i + 1}"),
          Reviewer.Mechanical,
          sev,
          k,
          w,
          q,
          p,
          fx
        )
    }

  /** The coverage drift of an article that records a filter (#958), as findings to follow the [[mechanical]] ones
    * (`M<from>`…): a source of the filter neither cited nor given a reason is MAJOR (the guide: every source ends in
    * Sources cited or Not used, with a reason); a reason for a source the body now cites, or one the filter no longer
    * returns, is minor (it is not shown).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def coverageDrift(c: Coverage, from: Merged.Round): List[domain.reviewarticle.Finding] =
    def keys(cks: List[ApiCitekey]) =
      val shown = cks.take(40).map(ck => s"[[$ck]]").mkString(", ")
      if cks.size > 40 then s"$shown (+${cks.size - 40} more)" else shown
    c.drift.zipWithIndex.map { (d, i) =>
      val (sev, problem, fix) = d.kind match
        case DriftKind.Unplaced =>
          (
            domain.reviewarticle.Severity.Major,
            domain.reviewarticle.Finding.Problem.of(
              domain.reviewarticle.Finding.Problem.Cause
                .UnplacedSources(domain.reviewarticle.Finding.Problem.Count(d.citekeys.size), d.citekeys.take(40))
            ),
            "cite each one where its record bears on the question, or give its reason in `reasons` (read the record first)"
          )
        case DriftKind.ReasonForCited =>
          (
            domain.reviewarticle.Severity.Minor,
            domain.reviewarticle.Finding.Problem(
              s"a reason is stored for ${Plural.count(Plural.CountN(d.citekeys.size.toLong), Plural.CountSingular("source"))} the article cites: ${keys(d.citekeys)}"
            ),
            "remove the reason (an empty one in `reasons`)"
          )
        case DriftKind.ReasonOutside =>
          (
            domain.reviewarticle.Severity.Minor,
            domain.reviewarticle.Finding.Problem(
              s"a reason is stored for ${Plural.count(Plural.CountN(d.citekeys.size.toLong), Plural.CountSingular("source"))} the filter no longer returns: ${keys(d.citekeys)}"
            ),
            "remove the reason (an empty one in `reasons`)"
          )
      domain.reviewarticle.Finding(
        domain.reviewarticle.Finding.Id(s"M${from.value + i}"),
        Reviewer.Mechanical,
        sev,
        CoverageKind,
        domain.reviewarticle.Finding.Where(Article.Coverage.NotUsedHeading),
        domain.reviewarticle.Finding.Quote(""),
        problem,
        domain.reviewarticle.Finding.Fix(fix)
      )
    }

  // ── validating a reviewer's answer ──────────────────────────────────────────────────────────────

  /** How a stated evidence record is checked: a verified [[Evidence]], or why it was dropped. */
  type EvidenceCheck = EvidenceAnswer => Either[Problem, Quotation]

  /** No evidence is checked (the cold reader has none). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val NoEvidence: EvidenceCheck =
    e =>
      Left(
        Content.Proposal(Content.Requirement.Panel(PanelRule.RecordsCited), Problem.Where(s"[[${e.citekey}]]"))
      )

  /** The check every reviewer's evidence passes: a real citekey, and a quote that IS in its record (the OCR-tolerant
    * matcher the fixer's rejections use). `text` is the record as get_source shows it (summary, findings, notes, body).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def evidenceCheck(
      isCitekey: library.Citekey => Boolean,
      text: library.Citekey => Option[AgentVerify.SourceBody],
      minRatio: CritiqueRule.MinRatio
  ): EvidenceCheck =
    e =>
      val raw = Option(e.citekey).map(_.normalised).getOrElse("")
      val q = Option(e.quote).map(_.value.trim).getOrElse("")
      if q.isEmpty then Left(Content.Proposal(Content.Requirement.QuoteGiven, Problem.Where(s"[[$raw]]")))
      else
        library.Citekey.option(raw).filter(isCitekey) match
          case None     => Left(Content.Answer(Content.Requirement.KnownCitekey(Raw(raw))))
          case Some(ck) =>
            text(ck).map(t => findQuote(q, t.value, minRatio)).filter(_.accepted) match
              case Some(m) =>
                Right(Quotation(ck, e.offset.flatMap(o => Offset.option(o.value)), Quotation.Quote(q), m.ratio))
              case None =>
                Left(Content.Answer(Content.Requirement.Panel(PanelRule.QuoteInSourceRecord(Raw.of(ck), Raw(q)))))

  /** The stated verdict must not be worse than the findings show (a MAJOR with no major finding is a finding left out);
    * a milder one is simply corrected. On the last attempt the findings decide.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[domain] def verdictOf(
      stated: domain.reviewarticle.Verdict.Wire,
      fs: List[domain.reviewarticle.Finding],
      last: VerdictOfLast
  ): Either[Problem, domain.reviewarticle.Verdict] =
    val derived = domain.reviewarticle.Verdict.of(fs)
    domain.reviewarticle.Verdict.parse(Option(stated).getOrElse(domain.reviewarticle.Verdict.Wire(""))) match
      case None if !last.value && Option(stated).forall(_.value.trim.isEmpty) =>
        Left(Content.Answer(Content.Requirement.Panel(PanelRule.VerdictGiven)))
      case None if !last.value =>
        Left(Content.Answer(Content.Requirement.Panel(PanelRule.VerdictKnown(Raw.of(stated)))))
      case Some(v) if v.rank > derived.rank && !last.value =>
        Left(Content.Answer(Content.Requirement.Panel(PanelRule.VerdictShown(Raw.of(v.wire)))))
      case _ => Right(derived)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[domain] def settle(
      r: Reviewer,
      verdict: Either[Problem, domain.reviewarticle.Verdict],
      fs: List[domain.reviewarticle.Finding],
      problems: List[Problem],
      dropped: List[String],
      last: LastRound
  )(build: domain.reviewarticle.Verdict => Report): Either[List[Problem], (Report, List[String])] =
    val all = problems ++ verdict.left.toOption.toList
    if all.nonEmpty && !last.value then Left(all)
    else
      Right(
        (
          build(verdict.getOrElse(domain.reviewarticle.Verdict.of(fs))),
          all.map(p => s"${r.wire}: dropped — ${p.sentence}") ++ dropped
        )
      )

  // ── spot-checks: the rules reviewer's lookups, verified (#1840) ────────────────────────────────

  /** The tools whose result shows a source's record: a spot-check rests on one of them. */
  val SpotCheckTools: Set[SpotCheckTools] = Set(SpotCheck.Tool("get_source"), SpotCheck.Tool("passages"))

  val SpotVerdicts: List[SpotVerdicts] =
    List(SpotVerdictsValue("attributable"), SpotVerdictsValue("extrapolatory"), SpotVerdictsValue("contradictory"))

  /** How many spot-checks a full pass needs: `min` of them, or one per source the article cites in the corpus when it
    * cites fewer.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def spotChecksRequired(min: SpotCheckMin, citedInCorpus: CitedInCorpusCount): SpotCheckCount = SpotCheckCount(
    math.max(0, math.min(min.value, citedInCorpus.value))
  )

  /** Does tool call `u` read the record of `ck`? A get_source or passages call whose input names it. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def reads(u: AgentVerify.ToolUse, ck: library.Citekey): Boolean = readsText(u, ck.value)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def readsText(u: AgentVerify.ToolUse, ck: String): Boolean =
    SpotCheckTools(SpotCheck.Tool(u.tool.value.trim)) && ck.nonEmpty &&
      Option(u.input).exists(_.value.toLowerCase(java.util.Locale.ROOT).contains(ck.toLowerCase(java.util.Locale.ROOT)))

  /** Verify the spot-checks: each needs a cited citekey, the claim in the article, a verdict from [[SpotVerdicts]], a
    * tool call of this step that read that source ([[reads]]) and the record's sentence found in its record (`ev`, the
    * check evidence passes). Fewer than `required` verified, on different sources, is a problem to send back — the
    * model's own count of its checks is not taken on trust (sonnet answered from the brief alone with a review ledger
    * in it, #1840); on the last attempt it is a warning. A spot-check that fails beyond those required is dropped with
    * a warning. Returns (verified, problems, warnings).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def checkSpotChecks(
      as: List[SpotCheckAnswer],
      rule: SpotRule,
      ev: EvidenceCheck,
      last: CheckSpotChecksLast
  ): (List[SpotCheck], List[Problem], List[String]) =
    val results = as.zipWithIndex.map { (a, i) =>
      val ck = Option(a.citekey).map(_.normalised).getOrElse("")
      val claim = Option(a.claim).map(_.value.trim).getOrElse("")
      val verdict =
        SpotVerdictsValue(Option(a.verdict).map(_.value.trim.toLowerCase(java.util.Locale.ROOT)).getOrElse(""))
      val label = Problem.Where(s"spot-check ${i + 1} ([[$ck]])")
      def refused(rule: PanelRule) = Left(Content.Proposal(Content.Requirement.Panel(rule), label))
      val checked: Either[Problem, SpotCheck] =
        if ck.isEmpty then
          Left(
            Content.Proposal(Content.Requirement.Panel(PanelRule.CitekeyGiven), Problem.Where(s"spot-check ${i + 1}"))
          )
        else if rule.cited.nonEmpty && !rule.cited.exists(_.value.equalsIgnoreCase(ck)) then
          refused(PanelRule.SpotCited)
        else if !SpotVerdicts.contains(verdict) then
          refused(PanelRule.SpotVerdict(Raw.of(a.verdict), SpotVerdicts.toVector.map(Raw.of(_))))
        else if claim.isEmpty || !findQuote(claim, rule.body.value, rule.minRatio).accepted then
          refused(PanelRule.ClaimInArticle)
        else
          rule.calls.find(readsText(_, ck)) match
            case None       => refused(PanelRule.RecordRead)
            case Some(call) =>
              ev(
                EvidenceAnswer(EvidenceAnswer.Citekey(ck), None, Option(a.recordQuote).getOrElse(Quotation.Quote("")))
              ) match
                case Left(m)  => Left(m.inside(label))
                case Right(e) =>
                  Right(
                    SpotCheck(
                      e.citekey,
                      SpotCheck.Claim(claim),
                      verdict,
                      e.quote,
                      e.ratio,
                      SpotCheck.Tool(call.tool.value.trim)
                    )
                  )
      checked
    }
    val verified = results.collect { case Right(s) => s }
    val failed = results.collect { case Left(m) => m }
    val sources = verified.map(_.citekey.value.toLowerCase(java.util.Locale.ROOT)).distinct.size
    if sources >= rule.required.value then (verified, Nil, failed.map(m => s"rules: dropped — ${m.sentence}"))
    else
      val read = rule.calls.filter(u => SpotCheckTools(SpotCheck.Tool(u.tool.value.trim))).size
      val short = Content.Answer(
        Content.Requirement.Panel(
          PanelRule
            .SpotChecksVerified(
              Problem.Count(sources.toLong),
              Problem.Count.of(rule.required),
              Problem.Count(read.toLong)
            )
        )
      )
      val problems: List[Problem] = short :: failed
      if last.value then (verified, Nil, problems.map(p => s"rules: unverified — ${p.sentence}"))
      else (verified, problems, Nil)

  /** The finding code adds from the cold reader's `termsUndefined` count: above [[TermCountThreshold]] terms, MINOR at
    * any count. The count is inflated (the reader lists places, peoples and periods a general reader knows), and as a
    * MAJOR it made PASS unreachable (docs/research-review-convergence.md §4); the reader's individually named undefined
    * terms keep the severity the reader gives them. It stays a finding so the fixer sees it, and it is the one count
    * entry of the ledger ([[ReviewLedger.absorb]] matches it across counts and replaces an earlier MAJOR one).
    */
  object TermCount:

    val Kind: domain.reviewarticle.Finding.Kind = domain.reviewarticle.Finding.Kind("term-count")
    val Where: domain.reviewarticle.Finding.Where = domain.reviewarticle.Finding.Where("explain-back")
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def problem(n: ColdReaderAnswer.TermsUndefined): domain.reviewarticle.Finding.Problem =
      domain.reviewarticle.Finding.Problem(
        s"$n terms undefined (count only; the individual findings carry the severity)"
      )
    val Fix: domain.reviewarticle.Finding.Fix =
      domain.reviewarticle.Finding.Fix("define each on first use, and add a Glossary")

    /** The count finding — this one, or the MAJOR "N terms are undefined (more than 8)" earlier runs stored. */
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def is(f: domain.reviewarticle.Finding): Boolean =
      f.reviewer == Reviewer.ColdReader && Option(f.quote).forall(_.value.trim.isEmpty) &&
        normalise(Option(f.where).map(_.value).getOrElse("")) == normalise(Where.value) &&
        (f.kind == Kind || (f.kind.value == "term" && Option(f.problem).exists(_.isTermCount)))

  private val NoSlot = "^(none|no\\b|nothing|n/?a\\b|all\\b|-+$|—).*".r

  /** The reader's `emptySlots`, normalised (the schema asks for slot names; a model may still write prose): blanks and
    * "none…"/"n/a"/"all filled…" entries mean NO empty slot and are dropped; an entry whose first word is a slot name,
    * singular or plural ("open questions", "Disagreements:") is that slot. Returns the slots and the entries that are
    * neither.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def emptySlotsOf(raw: List[ColdReaderAnswer.EmptySlots]): (List[ColdReaderAnswer.EmptySlots], List[String]) =
    val entries = raw.map(_.value.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty).filterNot(NoSlot.matches)
    val read = entries.map { e =>
      val first = e.takeWhile(_.isLetter)
      ExplainSlots.find(s => first == s.value || first == s.value + "s").toLeft(e)
    }
    (read.collect { case Left(s) => s }.distinct, read.collect { case Right(e) => e }.distinct)

  /** Above this many undefined terms code adds the (minor) count finding. */
  val TermCountThreshold = 8

  // ── the critique checklist (docs/research-review-convergence.md §6.8) ──────────────────────────

  /** The finding kind of an omitted critique. */
  val MissingCritique: domain.reviewarticle.Finding.Kind = domain.reviewarticle.Finding.Kind("missing-critique")

  /** GRADE hedges and contest words: a sentence carrying one already qualifies its claim. */
  private val Hedge =
    ("(?i)(?<!\\p{L})(may|might|could|probably|possibly|perhaps|likely|unlikely|uncertain|tentative|tentatively|" +
      "disputed|contested|debated|controversial|challenged|challenges|questioned|questions|doubt|doubts|doubtful|doubted|" +
      "criticised|criticized|criticises|criticizes|critics|unproven|unproved|hypothetical|speculative|conjectural|" +
      "not settled|open question|remains open|is open)(?!\\p{L})").r

  /** Does the text carry a hedge or a contest word (outside its links)? */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def qualified(text: Sentence): Boolean =
    Hedge.findFirstIn("""\[\[[^\]]*\]\]""".r.replaceAllIn(text.value, " ")).isDefined

  private val ClauseEnd = "(;|,\\s+(?:and|but|while|whereas)\\s|\\s(?:but|whereas)\\s)".r

  /** The words a claim stands in: the quote and what follows it in its sentence up to the end of its clause (`;`,
    * `, and …`, `but …`) — so a hedge or a status label on ANOTHER source in the same sentence ("Gray and Atkinson
    * (2003) dated the split… [[gray2003_language]], and Bouckaert et al. (2012, contested)…") does not qualify it. The
    * whole sentence when the quote's end is not found.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def claimWindow(sentence: Sentence, quote: Quotation.Quote): Sentence =
    val ws = quote.value.split("[^\\p{L}\\p{N}]+").filter(_.nonEmpty).takeRight(3)
    if ws.isEmpty then sentence
    else
      val re = ("(?i)" + ws.map(java.util.regex.Pattern.quote).mkString("[^\\p{L}\\p{N}]+")).r
      Sentence(re.findAllMatchIn(sentence.value).toList.lastOption.fold(sentence.value) { m =>
        val after = sentence.value.substring(m.end)
        val tail = ClauseEnd.findFirstMatchIn(after).fold(after)(c => after.substring(0, c.start))
        sentence.value.substring(0, m.end) + tail
      })

  private val StatusLabel = "(contested|challenged|disputed|superseded|debunked|retracted)"

  /** Does the sentence label the criticised work itself as disputed — its link followed by a status
    * (`([[smith1998_death]], contested — challenged by …)`, `[[x]] (contested`)? A short form of the citekey
    * (`[[knapp2016]]` for `knapp2016_seven`) counts.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def labelled(sentence: LabelledSentence, criticised: Criticised): Boolean =
    val ck = criticised.value.trim.toLowerCase(java.util.Locale.ROOT)
    val forms = (ck :: Option.when(ck.contains("_"))(ck.takeWhile(_ != '_')).toList).map(java.util.regex.Pattern.quote)
    ("(?i)\\[\\[(" + forms.mkString("|") + ")\\]\\][\\s,;(—–-]{0,6}" + StatusLabel).r
      .findFirstIn(sentence.value)
      .isDefined

  /** Is the claim `quote` states already qualified in `sentence`: a hedge or contest word in its own clause
    * ([[claimWindow]]), or a status label on the criticised work ([[labelled]])?
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def qualifiedClaim(
      sentence: QualifiedClaimSentence,
      quote: QualifiedClaimQuote,
      criticised: QualifiedClaimCriticised
  ): Boolean =
    qualified(claimWindow(sentence, quote)) || labelled(sentence, criticised)

  /** One missing-critique finding per pair and pass (§6.9): of the findings naming the same two sources (either
    * direction), the skeptic's is kept — its pair is the checklist's — and the others are dropped, as is a non-skeptic
    * one whose pair an open skeptic entry of the ledger already holds (the cold reader repeating a ledger finding it
    * read; the rules reviewer the skeptic's). Returns the findings kept and a warning per one dropped.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def onePerPair(
      fs: List[domain.reviewarticle.Finding],
      heldBySkeptic: List[CritiqueRef]
  ): (List[domain.reviewarticle.Finding], List[String]) =
    val withPair = fs.filter(f => f.isMissingCritique && f.pair.isDefined)
    val keep = fs.filter { f =>
      if !f.isMissingCritique || f.pair.isEmpty then true
      else
        val ref = f.pair.get
        val rivals = withPair.filter(g => g.pair.exists(_.same(ref)))
        val first = rivals.find(_.reviewer == Reviewer.Skeptic).getOrElse(rivals.head)
        val held = f.reviewer != Reviewer.Skeptic && heldBySkeptic.exists(_.same(ref))
        (first eq f) && !held
    }
    val dropped = fs.filterNot(f => keep.exists(_ eq f))
    (
      keep,
      dropped.map(f =>
        s"${f.reviewer.wire}: ${f.id} dropped — another missing-critique finding already names ${f.pair.fold("")(_.wire)}"
      )
    )

  /** The skeptic's rulings: every open pair it was shown in full is named by a missing-critique finding (MAJOR, or
    * minor after [[checkCritiques]]'s calibration) or ruled not material here, with a reason. A not-material ruling of
    * an open pair of the checklist becomes a [[CritiqueReason]] to store (one the skeptic also raised as a MAJOR is
    * dropped: the finding wins). A pair it did not rule on sends the answer back; on the last attempt it is a warning
    * (the pair stays open). Returns (reasons, problems, warnings).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def checkNotMaterial(
      as: List[NotMaterialAnswer],
      fs: List[domain.reviewarticle.Finding],
      rule: CritiqueRule,
      last: CheckNotMaterialLast
  ): (List[CritiqueReason], List[Problem], List[String]) =
    val named = fs.filter(f => f.major && f.isMissingCritique).flatMap(_.pair)
    val results = as.map { a =>
      val reason = Option(a.reason).map(w => s"$w".trim.replaceAll("\\s+", " ")).getOrElse("")
      CritiqueRef.parse(Option(a.pair).getOrElse(CritiqueRef.Wire(""))).flatMap { ref =>
        def refused(r: CritiqueRef, rule: PanelRule) =
          Left(Content.Proposal(Content.Requirement.Panel(rule), Problem.Where(s"not material ${r.wire}")))
        CritiquePair.find(rule.pairs, ref) match
          case None                                   => refused(ref, PanelRule.ChecklistPair)
          case Some(p) if !p.open                     => refused(p.ref, PanelRule.PairOpen(Raw.of(p.state.wire)))
          case Some(p) if named.exists(_.same(p.ref)) => refused(p.ref, PanelRule.FindingStands)
          case Some(_) if reason.isEmpty              => refused(ref, PanelRule.ReasonStated)
          case Some(p) if reason.length > CritiqueReason.MaxChars =>
            refused(
              p.ref,
              PanelRule.ReasonWithin(Problem.Count(reason.length.toLong), Problem.Count(CritiqueReason.MaxChars.toLong))
            )
          case Some(p) => Right(CritiqueReason(p.ref.src, p.ref.dst, Coverage.Rationale(reason)))
      }
    }
    val ok = results.collect { case Right(c) => c }.distinctBy(c => (c.src, c.dst))
    val ruled = fs.filter(_.isMissingCritique).flatMap(_.pair) ++ ok.map(_.ref)
    val unruled = rule.shown.filter(p => p.open && !ruled.exists(_.same(p.ref))).map(_.ref.wire)
    val missing: List[Problem] = Option
      .when(unruled.nonEmpty)(
        Content.Answer(Content.Requirement.Panel(PanelRule.PairsRuled(unruled.toVector.map(Raw.of(_)))))
      )
      .toList
    val dropped = results.collect { case Left(m) => s"skeptic: dropped — ${m.sentence}" }
    if last.value then (ok, Nil, dropped ++ missing.map(m => s"skeptic: ${m.sentence} (left open)"))
    else (ok, missing, dropped)

  /** A full panel's checks: only when the ledger listed findings of this reviewer; otherwise ignored. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private[domain] def checksOf(
      r: Reviewer,
      prior: Option[Prior],
      as: List[CheckAnswer],
      last: ChecksOfLast
  ): (List[Check], List[Problem], List[String]) =
    prior.filter(_.asked.nonEmpty).fold((Nil, Nil, Nil))(p => r.checkChecks(p, as, last))

  /** A reviewer's word that a missing-critique finding is fixed, held to what code can see (§6.9): while its pair still
    * bears on the article — open, and the criticised work still cited — neither cited in one paragraph nor given a
    * reason, the check is recorded NOT fixed, with that as the note (a critique described without the work it answers
    * was "fixed" and raised again, run after run). `asked`: the findings the checks are of. Returns the checks and a
    * warning per one held.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def holdPairChecks(
      checks: List[Check],
      asked: List[domain.reviewarticle.Finding],
      pairs: List[CritiquePair]
  ): (List[Check], List[String]) =
    val held = checks.map { c =>
      asked.find(_.id == c.id).flatMap(_.pair).flatMap(CritiquePair.find(pairs, _)) match
        case Some(p) if c.fixed.value && p.bearing =>
          val why =
            s"[[${p.src.citekey}]] and [[${p.dst.citekey}]] are still not cited in one paragraph, and no reason is stored"
          (
            c.modify(_.fixed)
              .setTo(Check.Fixed(false))
              .modify(_.note)
              .setTo(
                Check.Note(
                  s"$why (code's check)${Option(c.note).filter(_.value.trim.nonEmpty).fold("")(n => s"; the reviewer: $n")}"
                    .take(300)
                )
              ),
            Some(s"${c.id}: checked as fixed, but $why — recorded not fixed")
          )
        case _ => (c, None)
    }
    (held.map(_._1), held.flatMap(_._2))

  /** The kinds of finding that are factual errors: the only MAJORs a verification pass may raise outside the edited
    * passages, and only with evidence code verified.
    */
  val FactualKinds: Set[domain.reviewarticle.Finding.Kind] =
    Set(
      domain.reviewarticle.Finding.Kind("contradictory"),
      domain.reviewarticle.Finding.Kind("extrapolatory"),
      domain.reviewarticle.Finding.Kind("decorative"),
      domain.reviewarticle.Finding.Kind("recall"),
      domain.reviewarticle.Finding.Kind("date"),
      domain.reviewarticle.Finding.Kind("status"),
      domain.reviewarticle.Finding.Kind("settled-but-contested"),
      domain.reviewarticle.Finding.Kind("number")
    )

  /** At most this many lessons per answer, each at most this long. */
  val MaxLessonsPerAnswer = 3
  val LessonChars = 200

  // ── merge and gate (§4.3 steps 4–5) ─────────────────────────────────────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[domain] def key(f: domain.reviewarticle.Finding): (String, String) =
    (normalise(f.where.value), normalise(f.problem.value))

  /** One findings list for the round: mechanical first, then rules, cold reader, skeptic; duplicates (the same `where`
    * + `problem`, whoever raised it) dropped; a cold-reader and a skeptic finding on the same passage both KEPT (the
    * fixer must satisfy both). A finding the fixer rejected in an earlier round and a reviewer raises again is flagged
    * `escalated`. A dropped duplicate's evidence joins the finding that is kept. The verdict is the worst of every
    * report and every kept finding.
    */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def merge(
      round: MergeRound,
      mechanicalFindings: List[domain.reviewarticle.Finding],
      reports: List[Report],
      rejectedBefore: List[domain.reviewarticle.Finding]
  ): Merged =
    val all = mechanicalFindings ++ reports.sortBy(_.reviewer.ordinal).flatMap(_.findings)
    // a duplicate is dropped, but the records it cites join the finding that is kept
    val kept = all
      .foldLeft(Vector.empty[domain.reviewarticle.Finding]) { (acc, f) =>
        acc.indexWhere(g => key(g) == key(f)) match
          case -1 => acc :+ f
          case i  => acc.updated(i, acc(i).copy(evidence = mergeEvidence(acc(i).evidence, f.evidence)))
      }
      .toList
      .map(f =>
        if rejectedBefore.exists(_.sameFinding(f)) then
          f.modify(_.escalated).setTo(domain.reviewarticle.Finding.Escalated(true))
        else f
      )
    val verdict = domain.reviewarticle.Verdict.worst(reports.map(_.verdict) :+ domain.reviewarticle.Verdict.of(kept))
    val cold = reports.find(_.reviewer == Reviewer.ColdReader)
    Merged(
      round,
      verdict,
      kept,
      cold.flatMap(_.explainBack),
      cold.toList.flatMap(_.emptySlots),
      reports.flatMap(_.proposals).distinct
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def mergeEvidence(a: List[Quotation], b: List[Quotation]): List[Quotation] =
    (a ++ b)
      .foldLeft(Vector.empty[Quotation]) { (acc, e) =>
        if acc.exists(x => x.citekey == e.citekey && normalise(x.quote.value) == normalise(e.quote.value))
        then acc
        else acc :+ e
      }
      .take(MaxEvidence)
      .toList

  /** The panel rounds a run gets: what it asked for, else the configured default, never more than the cap. The fixer
    * works BETWEEN rounds, so a run that fixes gets at least two (one to find, one to check the fix).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def rounds(requested: Option[RequestedRounds], default: DefaultRounds, cap: RoundCap, fix: FixRun): RoundCount =
    val floor = if fix.value then 2 else 1
    RoundCount(math.max(floor, math.min(math.max(floor, cap.value), requested.map(_.value).getOrElse(default.value))))

  // ── the fixer (§4.3 step 6) ─────────────────────────────────────────────────────────────────────

  def words(s: String): Int = s.split("\\s+").count(_.nonEmpty)

  /** The fixer's turns: its agent's `max-turns`, plus `perFinding` for every finding that may need a lookup — each
    * MAJOR, and each minor of the rules reviewer or the skeptic (a factual one) — never more than `cap` (and never
    * fewer than `base`). Eighteen findings in twelve turns ran out.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fixerTurns(base: Int, m: Merged, perFinding: Int, cap: TurnCap): Int =
    val b = math.max(1, base)
    val n = m.findings.count(f => f.major || f.reviewer == Reviewer.Rules || f.reviewer == Reviewer.Skeptic)
    math.max(b, math.min(cap.value, b + math.max(0, perFinding) * n))

  /** THE check of a draft — the fixer's edits against the body the round reviewed, or a whole body — shared by the
    * pipeline's validation of the fixer's answer and the `check_edits` tool, so the two can never disagree. Edits apply
    * in order ([[ArticleEdits.applyAll]]); an edit whose text links to nothing is never applied. The result's problems:
    * each edit that did not apply; links to nothing; a hand-written Sources cited heading; up to three uncited
    * paragraphs; banned filler; and formatting errors the draft introduced (ones already in `before` are not the
    * draft's). FATAL (never written, even on a last attempt): an empty body, or a link problem `before` did not have.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def checkBody(
      slug: research.Slug,
      before: domain.reviewarticle.Fix.Body,
      kind: Snapshot.Kind,
      draft: Article.Edits.Draft,
      corpus: BodyCorpus
  ): Article.Edits.ArticleCheck =
    def links(t: String): List[Problem] =
      AgentVerify
        .linkProblems(AgentVerify.LinkProblemsText(t), Problem.Where("text"), corpus.isCitekey, corpus.isTarget)
    val applied = draft match
      case Article.Edits.Draft.Edits(es) =>
        Article.Edits.applyAll(
          before,
          es,
          a => a.text.flatMap(t => links(t.value).headOption).map(_.inside(Problem.Where("its text is refused")))
        )
      case Article.Edits.Draft.Body(t) => Article.Edits.Applied(domain.reviewarticle.Fix.Body(t.value.trim), Nil)
    val body = applied.body
    val bodyProblems: List[Problem] =
      if body.value.trim.isEmpty then List(Content.Answer(Content.Requirement.BodyGiven))
      else
        val again = mechanical(slug, kind, body, corpus.isTarget, corpus.generated)
        val formatBefore = corpus.formatErrors(before).toSet
        links(body.value) ++
          again
            .filter(_.problem.isHandWrittenSourcesCited)
            .flatMap(_.problem.cause.shown) ++
          again.filter(_.kind == CoverageKind).flatMap(_.problem.cause.shown) ++
          again
            .filter(_.problem.isUncitedParagraph)
            .take(3)
            .map(f => Content.Answer(Content.Requirement.StillUncited(Raw.of(f.quote)))) ++
          again.filter(_.kind.value == "language").flatMap(_.problem.cause.shown) ++
          corpus.formatErrors(body).filterNot(formatBefore).map(_.inside(Problem.Where("formatting")))
    val fatal = body.value.trim.isEmpty || links(body.value).toSet.diff(links(before.value).toSet).nonEmpty
    Article.Edits.ArticleCheck(
      Article.Slug(slug.value),
      kind,
      applied.results,
      applied.results.flatMap(r => r.problem.map(_.inside(Problem.Where(r.label)))) ++ bodyProblems,
      (if fatal then Article.Edits.ArticleCheck.Fatal.Fatal else Article.Edits.ArticleCheck.Fatal.Recoverable),
      Article.Edits.ArticleCheck.WordsBefore(words(before.value)),
      Article.Edits.ArticleCheck.WordsAfter(words(body.value)),
      body
    )

  object Revision:
    export Article.Body

  /** The fixer's set-asides (§6.8): each names a missing-critique finding, the pair (its own, or the finding's), which
    * must be a pair of the article's critique checklist, and a one-line reason. An entry with NO id and a `pair` sets
    * aside a pair of the checklist that no finding names (one the revision brought in, §6.9). Returns the verified
    * finding set-asides, the pair-only reasons and the problems.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def checkSetAside(
      as: List[SetAsideAnswer],
      byId: Map[SetAside.Id, domain.reviewarticle.Finding],
      critiques: List[CritiquePair]
  ): (List[SetAside], List[CritiqueReason], List[Problem]) =
    def reasonOf(a: SetAsideAnswer) = Option(a.reason).map(w => s"$w".trim.replaceAll("\\s+", " ")).getOrElse("")
    def held(
        label: Problem.Where,
        r: CritiqueRef,
        reason: String,
        remedy: Content.Remedy
    ): Either[Problem, CritiquePair] =
      def refused(rule: PanelRule) = Left(Content.Proposal(Content.Requirement.Panel(rule), label))
      CritiquePair.find(critiques, r) match
        case None                      => refused(PanelRule.ChecklistEdge(Raw.of(r.wire), remedy))
        case Some(_) if reason.isEmpty => refused(PanelRule.GiveReason)
        case Some(_) if reason.length > CritiqueReason.MaxChars =>
          refused(
            PanelRule.ReasonWithin(Problem.Count(reason.length.toLong), Problem.Count(CritiqueReason.MaxChars.toLong))
          )
        case Some(p) => Right(p)
    val results: List[Either[Problem, Either[SetAside, CritiqueReason]]] = as.map { a =>
      val idText = Option(a.id).map(_.value.trim).getOrElse("")
      val id = domain.reviewarticle.Finding.Id(idText)
      val reason = reasonOf(a)
      val named = Option(a.pair).flatten
        .map(_.value.trim)
        .filter(_.nonEmpty)
        .map(t => CritiqueRef.parse(CritiqueRef.Wire(t)))
      if idText.isEmpty then
        named match
          case None           => Left(Content.Answer(Content.Requirement.Panel(PanelRule.SetAsideNamed)))
          case Some(Left(e))  => Left(e.inside(Problem.Where("set aside")))
          case Some(Right(r)) =>
            held(Problem.Where(s"set aside ${r.wire}"), r, reason, Content.Remedy.Unstated).map(p =>
              Right(CritiqueReason(p.ref.src, p.ref.dst, Coverage.Rationale(reason)))
            )
      else
        def refused(at: Problem.Where, rule: PanelRule) = Left(Content.Proposal(Content.Requirement.Panel(rule), at))
        byId.get(id) match
          case None                            => refused(Problem.Where(s"set aside '$id'"), PanelRule.SuchFinding)
          case Some(f) if !f.isMissingCritique =>
            refused(Problem.Where(s"set aside $id"), PanelRule.MissingCritiqueOnly)
          case Some(f) =>
            val ref: Either[Problem, CritiqueRef] = named match
              case Some(r) => r
              case None    =>
                f.pair.toRight(
                  Content.Proposal(Content.Requirement.Panel(PanelRule.FindingPair), Problem.Where(s"set aside $id"))
                )
            ref
              .flatMap(r => held(Problem.Where(s"set aside $id"), r, reason, Content.Remedy.ApplyOrReject))
              .map(p => Left(SetAside(id, p.ref, Coverage.Rationale(reason))))
    }
    val ok = results.collect { case Right(v) => v }
    (
      ok.collect { case Left(s) => s }.distinctBy(_.id),
      ok.collect { case Right(r) => r }.distinctBy(r => (r.src, r.dst)),
      results.collect { case Left(p) => p }
    )

  /** The fixer's coverage reasons (#958) against the coverage the round reviewed: each names a source the filter
    * returns that the revised body leaves out (an empty one may remove any stored reason); an unplaced-sources finding
    * listed as applied leaves none unplaced. Returns the reasons that pass (written on the last attempt) and the
    * problems.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def checkReasons(
      stated: List[Coverage.Reason],
      coverage: Option[Coverage],
      body: domain.reviewarticle.Fix.Body,
      isCitekey: library.Citekey => Boolean,
      m: Merged,
      applied: Set[domain.reviewarticle.Fix.Applied]
  ): (List[Coverage.Reason], List[Problem]) =
    coverage match
      case None =>
        (
          Nil,
          Option
            .when(stated.nonEmpty)(Content.Answer(Content.Requirement.Panel(PanelRule.ReasonsUnfiltered)))
            .toList
        )
      case Some(c) =>
        val citedAfter = wikilinks(Article.Coverage.withoutNotUsed(body).value)
          .filter(t => library.Citekey.option(t).exists(isCitekey))
          .map(com.histmersion.api.library.Citekey(_))
          .toSet
        val inFilter = (c.notUsed.map(_.source.citekey) ++ c.cited.map(_.citekey)).toSet
        val stored = c.reasons.map(r => r.citekey -> r.reason).toMap
        val checked: List[Either[Problem, Coverage.Reason]] = stated.map { r =>
          val ck = com.histmersion.api.library.Citekey(
            Option(r.citekey).map(_.value.trim.stripPrefix("[[").stripSuffix("]]").trim.toLowerCase).getOrElse("")
          )
          def refused(rule: PanelRule) =
            Left(Content.Proposal(Content.Requirement.Panel(rule), Problem.Where(s"reason for [[$ck]]")))
          val removes = Option(r.reason).forall(w => s"$w".trim.isEmpty)
          if removes && stored.contains(ck) then Right(r.modify(_.citekey).setTo(ck))
          else if removes then refused(PanelRule.StoredReason)
          else if !inFilter(ck) then refused(PanelRule.FilterReturns)
          else if citedAfter(ck) then refused(PanelRule.LeftOut)
          else
            Coverage.Reason
              .merge(Map.empty, List(r.modify(_.citekey).setTo(ck)))
              .map(_ => r.modify(_.citekey).setTo(ck))
        }
        val ok = checked.collect { case Right(r) => r }
        val merged = Coverage.Reason.merge(stored, ok).getOrElse(stored)
        val unplacedIds = m.findings
          .filter(f =>
            f.reviewer == Reviewer.Mechanical && f.kind == CoverageKind &&
              f.major && f.problem.isUnplacedSources
          )
          .map(_.id)
        val left = Article.Coverage.unplacedAfter(c, citedAfter, merged)
        val still = Option.when(unplacedIds.exists(applied) && left.nonEmpty)(
          Content.Answer(
            Content.Requirement.Panel(
              PanelRule.StillUnplaced(unplacedIds.filter(applied).toVector.map(Raw.of(_)), left.toVector.map(Raw.of(_)))
            )
          )
        )
        (ok, checked.collect { case Left(p) => p } ++ still.toList)
