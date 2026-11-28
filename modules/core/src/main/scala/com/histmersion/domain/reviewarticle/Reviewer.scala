package com.histmersion
package domain.reviewarticle

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.api.WireEnum
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.Newtype
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.Content
import com.histmersion.util.problem.Problem.Raw
import com.histmersion.util.problem.Problem.Content.PanelRule
import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.research.CritiqueRef
import com.histmersion.domain.ReviewArticle.MaxEvidence
import com.histmersion.domain.reviewarticle.Severity
import com.histmersion.domain.reviewarticle.Finding
import com.histmersion.domain.reviewarticle.FindingAnswer
import com.histmersion.domain.ReviewArticle.EvidenceCheck
import com.histmersion.domain.ReviewArticle.NoEvidence
import com.histmersion.api.research.CritiqueState
import com.histmersion.api.automation.Approval
import com.histmersion.domain.Proposal
import com.histmersion.domain.reviewarticle.CritiqueRule
import com.histmersion.domain.reviewarticle.Fix
import com.histmersion.domain.ReviewArticle.qualifiedClaim
import com.histmersion.domain.ReviewArticle.CheckCritiquesLast
import com.histmersion.api.research.CritiquePair
import com.histmersion.domain.reviewarticle.CheckAnswer
import com.histmersion.domain.reviewarticle.Check
import com.histmersion.domain.reviewarticle.Prior
import com.histmersion.domain.ReviewArticle.findQuote
import com.histmersion.domain.ReviewArticle.LastRound
import com.histmersion.domain.ReviewArticle.LessonChars
import com.histmersion.domain.ReviewArticle.MaxLessonsPerAnswer
import com.histmersion.domain.reviewarticle.Recheck
import com.histmersion.domain.ReviewArticle.CheckRecheckLast
import com.histmersion.domain.reviewarticle.RecheckAnswer
import com.histmersion.domain.ReviewArticle

enum Reviewer(val wire: Reviewer.Wire, val prefix: Reviewer.Prefix) derives CanEqual:
  case Mechanical extends Reviewer(Reviewer.Wire("mechanical"), Reviewer.Prefix("M"))
  case Rules extends Reviewer(Reviewer.Wire("rules"), Reviewer.Prefix("R"))
  case ColdReader extends Reviewer(Reviewer.Wire("cold-reader"), Reviewer.Prefix("C"))
  case Skeptic extends Reviewer(Reviewer.Wire("skeptic"), Reviewer.Prefix("S"))

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object Reviewer:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  type Prefix = Prefix.T
  object Prefix extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[Reviewer] = values.find(_.wire.value == s.value.trim.toLowerCase)
  given WireEnumValues[Reviewer]:
    def all: Array[Reviewer] = values
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire(e: Reviewer): WireEnumValues.Wire = WireEnumValues.Wire(e.wire.value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromWire(s: WireEnumValues.Wire): Option[Reviewer] = parse(Wire(s.value))
  given JsonValueCodec[Reviewer] = WireEnum.codec
  given Schema[Reviewer] = WireEnum.schema

  extension (r: Reviewer)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def checkFindings(
        as: List[FindingAnswer],
        ev: EvidenceCheck = NoEvidence
    ): (List[Finding], List[Problem], List[String]) =
      val checked = as.zipWithIndex.map { (a, i) =>
        val label = Problem.Where(s"finding ${i + 1} (${Option(a.problem).map(_.value).getOrElse("").take(60)})")
        def refused(rule: PanelRule) = Left(Content.Proposal(Content.Requirement.Panel(rule), label))
        Severity.parse(Option(a.severity).getOrElse(Severity.Wire(""))) match
          case None => refused(PanelRule.SeverityKnown(Raw.of(a.severity)))
          case Some(_) if Option(a.problem).forall(_.value.trim.isEmpty) => refused(PanelRule.ProblemStated)
          case Some(_) if Option(a.where).forall(_.value.trim.isEmpty)   => refused(PanelRule.WhereStated)
          case Some(s)                                                   => Right((s, a))
      }
      val built = checked.collect { case Right(v) => v }.zipWithIndex.map { case ((s, a), i) =>
        val id = Finding.Id(s"${r.prefix}${i + 1}")
        val evs = Option(a.evidence).getOrElse(Nil).take(MaxEvidence).map(ev)
        val f = Finding(
          id,
          r,
          s,
          Option(a.kind).map(k => Finding.Kind(k.value.trim)).getOrElse(Finding.Kind("")),
          Option(a.where).map(w => Finding.Where(w.value.trim)).getOrElse(Finding.Where("")),
          Option(a.quote).map(q => Finding.Quote(q.value.trim)).getOrElse(Finding.Quote("")),
          Finding.Problem(a.problem.value.trim),
          Option(a.fix).map(x => Finding.Fix(x.value.trim)).getOrElse(Finding.Fix("")),
          evs.collect { case Right(e) => e },
          pair = Option(a.pair).flatten.flatMap(CritiqueRef.parse(_).toOption)
        )
        (f, evs.collect { case Left(m) => s"${r.wire}: $id evidence dropped — ${m.sentence}" })
      }
      (built.map(_._1), checked.collect { case Left(m) => m }, built.flatMap(_._2))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def recordedMinor(f: Finding, why: String): Finding =
    f.modify(_.severity)
      .setTo(Severity.Minor)
      .modify(_.problem)
      .setTo(Finding.Problem(s"${f.problem.value.trim} (recorded minor: $why)"))

  extension (r: Reviewer)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    def checkCritiques(
        fs: List[Finding],
        rule: CritiqueRule,
        proposals: List[Proposal],
        last: CheckCritiquesLast
    ): (List[Finding], List[Problem], List[String]) =
      val results = fs.map { f =>
        if !f.isMissingCritique || !f.major then Right((f, None))
        else
          val label = s"${f.id} (missing-critique, major)"
          def minor(g: Finding, why: String) =
            Right((recordedMinor(g, why), Some(s"${r.wire}: $label — $why; recorded minor")))
          f.pair.flatMap(CritiquePair.find(rule.pairs, _)) match
            case Some(p) if p.open =>
              val g = f.modify(_.pair).setTo(Some(p.ref))
              if !p.criticised.cited.cited then
                minor(g, s"the article does not cite [[${p.criticised.citekey}]], so it states none of its claims")
              else if rule.body.value.isEmpty then Right((g, None))
              else
                Fix.claimSentence(rule.body, f.quote, rule.minRatio) match
                  case None if rule.full.value && !last.value =>
                    Left(
                      Content.Proposal(Content.Requirement.Panel(PanelRule.CriticisedClaimQuoted), Problem.Where(label))
                    )
                  case None => minor(g, "its quote is not in the article, so no unqualified claim is shown")
                  case Some(sentence) if qualifiedClaim(sentence, f.quote, p.criticised.citekey) =>
                    minor(g, s"the article already qualifies the claim: \"${sentence.value.trim.take(120)}\"")
                  case Some(_) => Right((g, None))
            case Some(p) =>
              val why =
                if p.state == CritiqueState.Addressed then
                  s"${p.ref.wire} is addressed — both sources are cited in one paragraph"
                else
                  s"${p.ref.wire} is set aside with a stored reason: \"${p.reason.map(w => s"$w").getOrElse("").take(160)}\""
              minor(f.modify(_.pair).setTo(Some(p.ref)), why)
            case None if f.pair.isEmpty && rule.full.value && !last.value =>
              Left(Content.Answer(Content.Requirement.Panel(PanelRule.PairNamed(Raw.of(f.id)))))
            case None =>
              val proposed = f.pair.exists(ref =>
                proposals.exists(pp =>
                  pp.kind == Approval.Kind.Edge && pp.target.exists(t =>
                    CritiqueRef(
                      com.histmersion.api.library.Citekey(pp.citekey.value),
                      com.histmersion.api.library.Citekey(t.value)
                    ).same(ref)
                  )
                )
              )
              val why = f.pair.fold("it names no checklist pair")(ref =>
                s"no edge is recorded between [[${ref.src}]] and [[${ref.dst}]]" +
                  (if proposed then " — its proposed edge waits for approval and joins the next run's checklist"
                   else "")
              )
              minor(f, why)
      }
      (
        results.collect { case Right((f, _)) => f },
        results.collect { case Left(p) => p },
        results.collect { case Right((_, Some(w))) => w }
      )

  extension (r: Reviewer)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def checkChecks(prior: Prior, as: List[CheckAnswer], last: LastRound): (List[Check], List[Problem], List[String]) =
      val byId = prior.asked.map(f => f.id -> f).toMap
      val asked = byId.keys.toVector.sortBy(_.value).map(Raw.of(_))
      def gone(f: Finding) =
        f.quote.value.trim.isEmpty || !findQuote(f.quote.value, prior.body.value, prior.minRatio).accepted
      val results = Option(as).getOrElse(Nil).map { c =>
        val idText = Option(c.id).map(_.value.trim).getOrElse("")
        val id = Finding.Id(idText)
        val q = Option(c.quote).map(_.value.trim).getOrElse("")
        val note = Option(c.note).map(_.value.trim).getOrElse("")
        def refused(rule: PanelRule) =
          Left(Content.Proposal(Content.Requirement.Panel(rule), Problem.Where(s"check of $id")))
        val checked: Either[Problem, Check] = byId.get(id) match
          case None if idText.isEmpty => Left(Content.Answer(Content.Requirement.Panel(PanelRule.CheckId(asked))))
          case None                   =>
            Left(
              Content
                .Proposal(Content.Requirement.Panel(PanelRule.AskedFinding(asked)), Problem.Where(s"check of '$id'"))
            )
          case Some(_) if q.nonEmpty =>
            if findQuote(q, prior.body.value, prior.minRatio).accepted then
              Right(Check(id, c.fixed, Check.Quote(q), Check.Note(note)))
            else refused(PanelRule.QuoteInArticle(Raw(q)))
          case Some(f) if gone(f) => Right(Check(id, c.fixed, Check.Quote(""), Check.Note(note)))
          case Some(_)            =>
            refused(
              PanelRule.CheckQuoted(if c.fixed.value then Content.Shows.Fixed else Content.Shows.StillThere)
            )
        checked match
          case Right(k) if !k.fixed.value && k.note.value.isEmpty => refused(PanelRule.CheckNoted)
          case other                                              => other
      }
      val checks = results
        .collect { case Right(c) => c }
        .foldLeft(Vector.empty[Check])((acc, c) => if acc.exists(_.id == c.id) then acc else acc :+ c)
        .toList
      val missing: List[Problem] =
        prior.asked
          .map(_.id)
          .filterNot(checks.map(_.id).toSet)
          .map(id => Content.Proposal(Content.Requirement.Panel(PanelRule.CheckGiven), Problem.Where.of(id)))
      val problems = results.collect { case Left(p) => p } ++ missing
      if last.value then (checks, Nil, problems.map(p => s"${r.wire}: ${p.sentence} (left open)"))
      else (checks, problems, Nil)

  extension (r: Reviewer)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def checkRecheck(
        a: RecheckAnswer,
        prior: Prior,
        edited: ReviewArticle.CheckRecheckEdited,
        ev: EvidenceCheck,
        last: CheckRecheckLast,
        critiques: List[CritiquePair] = Nil
    ): Either[List[Problem], (Recheck, List[String])] =
      val (cs, cps, cws) = r.checkChecks(prior, a.checks, last)
      val (raw, ps, ds0) = r.checkFindings(Option(a.findings).getOrElse(Nil), ev)
      val (fs, _, kws) =
        r.checkCritiques(
          raw,
          CritiqueRule(critiques, Nil, full = CritiqueRule.Full(false), prior.body, prior.minRatio),
          Nil,
          last
        )
      val ds = ds0 ++ kws
      val scoped = fs.map(_.scope(edited, prior.minRatio))
      val lessons = Option(a.lessons)
        .getOrElse(Nil)
        .map(_.value.trim)
        .filter(_.nonEmpty)
        .distinct
        .take(MaxLessonsPerAnswer)
        .map(l => Recheck.Lessons(l.take(LessonChars)))
      val problems = cps ++ ps
      if problems.nonEmpty && !last.value then Left(problems)
      else
        Right(
          (
            Recheck(r, cs, scoped.map(_._1), lessons),
            cws ++ ps.map(p => s"${r.wire}: dropped — ${p.sentence}") ++ ds ++ scoped.flatMap(_._2)
          )
        )
