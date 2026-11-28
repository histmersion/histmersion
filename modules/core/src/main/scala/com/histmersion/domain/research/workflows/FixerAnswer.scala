package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.research.Coverage
import com.histmersion.api.research.Article
import com.histmersion.domain.research.workflows.RejectionAnswer
import com.histmersion.domain.research.workflows.WontFixAnswer
import com.histmersion.domain.research.workflows.SetAsideAnswer
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.research.workflows.Finding
import com.histmersion.domain.research.workflows.Fix
import com.histmersion.api.research.CritiquePair
import com.histmersion.api.research.CritiquePairing
import com.histmersion.domain.ReviewLedger
import com.histmersion.api.research.critiquepairing.MissedPair
import com.histmersion.domain.research.workflows.Regression
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.Content
import com.histmersion.util.problem.Problem.Raw
import com.histmersion.util.problem.Problem.Content.PanelRule
import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.research.CritiqueRef
import com.histmersion.domain.AgentVerify
import com.histmersion.domain.library
import com.histmersion.domain.research.workflows.Reviewer
import com.histmersion.domain.ReviewArticle.checkReasons
import com.histmersion.domain.ReviewArticle.checkBody
import com.histmersion.domain.research.workflows.WontFix
import com.histmersion.domain.research.workflows.BodyCorpus
import com.histmersion.domain.ReviewArticle.Rationale
import com.histmersion.domain.research.workflows.Snapshot
import com.histmersion.domain.ReviewArticle.checkSetAside
import com.histmersion.domain.research.workflows.Rejection
import com.histmersion.domain.research.workflows.CritiqueRule
import com.histmersion.domain.research
import com.histmersion.domain.research.workflows.FixGuards
import com.histmersion.domain.research.workflows.Merged
import com.histmersion.domain.ReviewArticle.words
import com.histmersion.domain.ReviewArticle.findQuote
import com.histmersion.domain.ReviewArticle.LastRound

final case class FixerAnswer(
    edits: List[Article.Edits.EditAnswer] = Nil,
    applied: List[FixerAnswer.Applied] = Nil,
    rejected: List[RejectionAnswer] = Nil,
    unresolvedMoved: List[FixerAnswer.UnresolvedMoved] = Nil,
    notes: FixerAnswer.Notes,
    reasons: List[Coverage.Reason] = Nil,
    wontFix: List[WontFixAnswer] = Nil,
    setAside: List[SetAsideAnswer] = Nil
)

object FixerAnswer:
  export Finding.Id as Applied
  export Fix.{Notes, UnresolvedMoved}

  final private case class Revision(
      check: Article.Edits.ArticleCheck,
      body: Article.Body,
      hits: List[MissedPair],
      pairs: List[CritiquePair],
      regressions: List[Regression]
  )

  extension (a: FixerAnswer)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
    def checkFix(
        m: Merged,
        slug: research.Slug,
        before: Fix.Body,
        kind: Snapshot.Kind,
        corpus: BodyCorpus,
        isCitekey: library.Citekey => Boolean,
        text: library.Citekey => Option[AgentVerify.SourceBody],
        minRatio: CritiqueRule.MinRatio,
        last: LastRound,
        coverage: Option[Coverage] = None,
        critiques: List[CritiquePair] = Nil,
        guards: Option[FixGuards] = None
    ): Either[List[Problem], (Fix, List[String])] =
      val ids = m.findings.map(_.id).toSet
      val byId = m.findings.map(f => f.id -> f).toMap
      val applied0 = a.applied.map(x => Finding.Id(x.value.trim)).filter(_.value.nonEmpty).distinct
      val unknown: List[Problem] = (applied0 ++ a.rejected
        .map(r => Finding.Id(r.id.value.trim)))
        .filterNot(ids)
        .distinct
        .map(i => Content.Answer(Content.Requirement.Panel(PanelRule.KnownFinding(Raw.of(i)))))
      val rejections: List[Either[(Finding.Id, Problem), Rejection]] = a.rejected.map { r =>
        val id = Finding.Id(r.id.value.trim)
        val ckText = r.citekey.normalised
        def refused(rule: PanelRule) = Left(id -> Content.Answer(Content.Requirement.Panel(rule)))
        byId.get(id) match
          case None =>
            Left(
              id -> Content.Proposal(
                Content.Requirement.Panel(PanelRule.FindingListed),
                Problem.Where(s"rejection of '$id'")
              )
            )
          case Some(f) if f.reviewer == Reviewer.Mechanical =>
            refused(PanelRule.Rejectable(Raw.of(id), Content.Unrejectable.MechanicalCheck))
          case Some(f) if f.reviewer == Reviewer.ColdReader =>
            refused(PanelRule.Rejectable(Raw.of(id), Content.Unrejectable.ReadersConfusion))
          case Some(_) if Option(r.reason).forall(w => s"$w".trim.isEmpty) =>
            refused(PanelRule.RejectionReason(Raw.of(id)))
          case Some(_) =>
            library.Citekey.option(ckText).filter(isCitekey) match
              case None     => refused(PanelRule.RejectionCitekey(Raw.of(id), Raw(ckText)))
              case Some(ck) =>
                val q = Option(r.quote).map(_.value.trim).getOrElse("")
                val hit = text(ck).map(t => findQuote(q, t.value, minRatio))
                hit.filter(_.accepted) match
                  case Some(qm) =>
                    Right(
                      Rejection(
                        id,
                        Rationale(s"${r.reason}".trim),
                        ck,
                        Rejection.Quote(q),
                        qm.ratio
                      )
                    )
                  case None =>
                    Left(
                      id -> Content.Proposal(
                        Content.Requirement.Panel(PanelRule.RejectionQuote(Raw.of(ck))),
                        Problem.Where(s"rejection of $id")
                      )
                    )
      }
      val rejectedOk = rejections.collect { case Right(r) => r }
      val rejectedBad = rejections.collect { case Left(v) => v }
      val wont: List[Either[Problem, WontFix]] = Option(a.wontFix).getOrElse(Nil).map { w =>
        val idText = Option(w.id).map(_.value.trim).getOrElse("")
        val id = Finding.Id(idText)
        byId.get(id) match
          case None if idText.isEmpty => Left(Content.Answer(Content.Requirement.Panel(PanelRule.WontFixId)))
          case None                   =>
            Left(Content.Proposal(Content.Requirement.Panel(PanelRule.SuchFinding), Problem.Where(s"won't fix '$id'")))
          case Some(f) if f.major && !f.needsFilter(coverage) =>
            Left(Content.Answer(Content.Requirement.Panel(PanelRule.OnlyMinorDeclined(Raw.of(id)))))
          case Some(_) if Option(w.reason).forall(r => s"$r".trim.isEmpty) =>
            Left(Content.Proposal(Content.Requirement.Panel(PanelRule.GiveReason), Problem.Where(s"won't fix $id")))
          case Some(_) => Right(WontFix(id, Rationale(s"${w.reason}".trim)))
      }
      val edits = Option(a.edits).getOrElse(Nil)

      def evaluate(es: List[Article.Edits.EditAnswer]): Revision =
        val check = checkBody(slug, before, kind, Article.Edits.Draft.Edits(es), corpus)
        guards match
          case Some(g) if !check.fatal.fatal =>
            val (body, hits) = g.repair(check.body)
            Revision(check, body, hits, g.pairsOf(body), g.regressions(before, body))
          case _ => Revision(check, check.body, Nil, critiques, Nil)

      val first = evaluate(edits)
      val (asides, pairsAside, asideProblems) =
        checkSetAside(Option(a.setAside).getOrElse(Nil), byId, critiques ++ first.pairs)
      val aside = asides.map(_.pair) ++ pairsAside.map(_.ref)
      def answered(r: CritiqueRef, rev: Revision) =
        aside.exists(_.same(r)) || CritiquePair.find(rev.pairs, r).forall(!_.bearing)

      val rev =
        if !last.value || first.regressions.isEmpty || guards.isEmpty then first
        else
          first.check.edits.filter(_.applied).map(_.index).reverse.foldLeft(first) { (cur, i) =>
            if cur.regressions.isEmpty then cur
            else
              val keep = cur.check.edits.filter(e => e.applied && e.index != i).map(_.index).toSet
              val trial =
                evaluate(edits.zipWithIndex.collect { case (e, j) if keep(Article.Edits.EditResult.Index(j + 1)) => e })
              if trial.regressions.size < cur.regressions.size && !trial.check.fatal.fatal then trial else cur
          }
      val dropped =
        first.check.edits.filter(_.applied).map(_.index).diff(rev.check.edits.filter(_.applied).map(_.index))
      val droppedNote = Option.when(dropped.nonEmpty)(
        s"edit(s) ${dropped.mkString(", ")} dropped: they reopened ${first.regressions.map(_.id).diff(rev.regressions.map(_.id)).mkString(", ")}"
      )

      val pairUnanswered = applied0
        .flatMap(byId.get)
        .filter(f => guards.isDefined && f.isMissingCritique)
        .flatMap(f => f.pair.filterNot(answered(_, rev)).map(r => f.id -> r))
      def unansweredText(r: CritiqueRef) = s"[[${r.src}]] and [[${r.dst}]] are not cited in one paragraph"
      val pairProblems: List[Problem] = pairUnanswered.map((id, r) =>
        Content.Answer(Content.Requirement.Panel(PanelRule.PairAnswered(Raw.of(id), Raw.of(r.src), Raw.of(r.dst))))
      )
      val applied = if last.value then applied0.filterNot(id => pairUnanswered.exists(_._1 == id)) else applied0
      val brought = rev.pairs
        .filter(p => p.bearing && !critiques.exists(_.ref.same(p.ref)) && !aside.exists(_.same(p.ref)))
        .foldLeft(Vector.empty[CritiquePair])((acc, p) => if acc.exists(_.ref.same(p.ref)) then acc else acc :+ p)
        .toList
      val broughtProblem = Option.when(brought.nonEmpty)(
        Content.Answer(
          Content.Requirement.Panel(
            PanelRule.CritiquesDiscussed(
              brought.map(p => Raw.of(p.criticised.citekey)).distinct.toVector,
              brought.toVector.map(p => Content.Discussed(Raw.of(p.ref.wire), p.kinds.toVector.map(Raw.of(_))))
            )
          )
        )
      )
      val regressionProblems: List[Problem] =
        rev.regressions.map(r => Content.Answer(Content.Requirement.Panel(PanelRule.ClosedKept(Raw(r.line)))))

      val repaired = m.findings
        .filter(f => f.isMissingCritique && !applied.contains(f.id))
        .filter(f => f.pair.exists(r => rev.hits.exists(_.ref.same(r)) && answered(r, rev)))
        .map(_.id)

      val decided = applied.toSet ++ rejectedOk.map(_.id) ++ rejectedBad.map(_._1) ++ asides.map(_.id) ++ repaired
      val declinedOk = wont.collect { case Right(w) => w.id }.toSet
      val undecided =
        m.majors.filterNot(f => decided(f.id) || declinedOk(f.id) || pairUnanswered.exists(_._1 == f.id)).map { f =>
          val decision =
            if f.needsFilter(coverage) then Content.Decision.DeclineUnfiltered else Content.Decision.ApplyOrReject
          Content.Answer(Content.Requirement.Panel(PanelRule.MajorDecided(Raw.of(f.id), decision)))
        }
      val check = rev.check
      val stated = Option(a.reasons).getOrElse(Nil)
      val (reasons, reasonProblems) = checkReasons(stated, coverage, rev.body, corpus.isCitekey, m, applied.toSet)
      val noEdit = Option.when(
        applied.exists(ids) && !check.edits.exists(_.applied) && reasons.isEmpty && asides.isEmpty &&
          rev.hits.isEmpty
      )(Content.Answer(Content.Requirement.Panel(PanelRule.EditMade(applied.toVector.map(Raw.of(_))))))
      val problems: List[Problem] =
        check.problems ++ reasonProblems ++ noEdit.toList ++ unknown ++ undecided ++ rejectedBad.map(_._2) ++
          wont.collect { case Left(p) => p } ++ asideProblems ++ pairProblems ++ broughtProblem.toList ++
          regressionProblems
      if problems.isEmpty || (last.value && !check.fatal.fatal) then
        val wontOk = wont.collect { case Right(w) => w }.filterNot(w => applied.contains(w.id)).distinctBy(_.id)
        val setAside = asides.filterNot(s => applied.contains(s.id) || rejectedOk.exists(_.id == s.id))
        val answeredIds = applied.toSet ++ rejectedOk.map(_.id) ++ wontOk.map(_.id) ++ setAside.map(_.id) ++ repaired
        val notFixed = m.findings.filterNot(f => answeredIds(f.id) || m.deferred.contains(f.id)).map { f =>
          val why = pairUnanswered
            .find(_._1 == f.id)
            .map((_, r) => s"listed as applied, but ${unansweredText(r)}")
            .orElse(rejectedBad.find(_._1 == f.id).map(_ => "its rejection's quote is not in the record it names"))
            .getOrElse(if f.major then "the fixer neither applied nor rejected it" else "the fixer did not answer it")
          WontFix(f.id, Rationale(why))
        }
        val fix = Fix(
          rev.body,
          check.edits,
          applied.filter(ids),
          rejectedOk,
          rejectedBad.map(_._1).filter(ids).distinct,
          a.unresolvedMoved.map(u => Fix.UnresolvedMoved(u.value.trim)).filter(_.value.nonEmpty),
          Option(a.notes).map(n => Fix.Notes(n.value.trim)).getOrElse(Fix.Notes("")),
          check.wordsBefore,
          Fix.WordsAfter(words(rev.body.value)),
          reasons,
          wontOk,
          setAside,
          repaired,
          rev.hits.map(h => Fix.Repairs(h.line)),
          notFixed,
          pairsAside
        )
        Right(
          (
            fix,
            (problems.map(p => s"fixer: kept despite — ${p.sentence}") ++ droppedNote.map(n => s"fixer: $n").toList)
          )
        )
      else Left(problems)
