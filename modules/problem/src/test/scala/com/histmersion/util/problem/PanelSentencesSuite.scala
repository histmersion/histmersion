package com.histmersion
package util.problem

import scala.compiletime.constValueTuple
import scala.deriving.Mirror

/** The review panel's and the article plan's sentences, typed in G60 step 5 (ReviewArticle's checks, PlanArticles'
  * dropped proposals): each case, with its vocabulary's variants, renders word for word what the string the check built
  * before said (`expected` spells that string as the old code interpolated it).
  */
class PanelSentencesSuite extends munit.FunSuite:
  import Problem.*
  import Problem.Content.{Decision, Discussed, PanelRule, Remedy, Requirement, Shows, Unrejectable}
  import Text.sentence

  private inline def cases[T](using m: Mirror.SumOf[T]): Set[String] =
    constValueTuple[m.MirroredElemLabels].toList.map(label => s"$label").toSet

  private def answer(rule: PanelRule): Problem = Content.Answer(Requirement.Panel(rule))
  private def at(where: Where, rule: PanelRule): Problem = Content.Proposal(Requirement.Panel(rule), where)

  /** A quote longer than the 80 characters a sentence shows of it. */
  private val quote =
    "Tin reached the Aegean by sea from the west, carried on ships that sailed past Crete and Rhodes to the Levant"

  /** Each case once at least (every variant of its nested vocabulary), with the sentence the old check wrote. */
  private val panel: Vector[(Problem, String)] = Vector(
    at(Where("[[knapp2016_tin]]"), PanelRule.RecordsCited) -> "[[knapp2016_tin]]: this reviewer cites no records",
    answer(
      PanelRule.QuoteInSourceRecord(
        Raw("knapp2016_tin"),
        Raw(
          "Tin reached the Aegean by sea from the west, carried on ships that sailed past Crete and Rhodes to the Levant"
        )
      )
    ) -> s"the quote is not in the record of [[knapp2016_tin]]: \"${quote.take(80)}\"",
    at(Where("finding 1 (wordy)"), PanelRule.SeverityKnown(Raw("severe"))) ->
      "finding 1 (wordy): severity 'severe' is neither major nor minor",
    at(Where("finding 1 ( )"), PanelRule.ProblemStated) -> "finding 1 ( ): no problem stated",
    at(Where("finding 1 (wordy)"), PanelRule.WhereStated) ->
      "finding 1 (wordy): no where stated — give the section heading or the passage's first words",
    answer(PanelRule.VerdictGiven) -> "no verdict given — PASS, MINOR or MAJOR",
    answer(PanelRule.VerdictKnown(Raw("BAD"))) -> "verdict 'BAD' is not PASS, MINOR or MAJOR",
    answer(PanelRule.VerdictShown(Raw("MAJOR"))) ->
      s"the verdict is MAJOR but no finding is ${"MAJOR".toLowerCase} — list the finding that makes it so",
    at(Where("spot-check 2"), PanelRule.CitekeyGiven) -> "spot-check 2: no citekey",
    at(Where("spot-check 1 ([[atlantis]])"), PanelRule.SpotCited) ->
      "spot-check 1 ([[atlantis]]): the article does not cite it — check a source it cites",
    at(
      Where("spot-check 1 ([[x]])"),
      PanelRule.SpotVerdict(Raw("supported"), Vector(Raw("attributable"), Raw("extrapolatory"), Raw("contradictory")))
    ) -> s"spot-check 1 ([[x]]): verdict 'supported' is not one of ${List("attributable", "extrapolatory", "contradictory").mkString(", ")}",
    at(Where("spot-check 1 ([[x]])"), PanelRule.ClaimInArticle) ->
      "spot-check 1 ([[x]]): the claim is not in the article — copy the article's words exactly",
    at(Where("spot-check 1 ([[x]])"), PanelRule.RecordRead) ->
      "spot-check 1 ([[x]]): no get_source or passages call for it in this step — read the record before reporting what it says",
    answer(PanelRule.SpotChecksVerified(Count(1L), Count(2L), Count(3L))) ->
      ("spot-checks: 1 of the 2 required are verified (on different cited sources; " +
        "this step made 3 get_source/passages call(s)) — read the records now with get_source or passages, and " +
        "report each check with the record's sentence copied from the result"),
    answer(PanelRule.SlotKnown(Raw("vibes"), Vector(Raw("question"), Raw("answer")))) ->
      "empty slot 'vibes' is not one of question, answer",
    answer(PanelRule.ExplainBackGiven) -> "no explain-back",
    at(Where("S9 (missing-critique, major)"), PanelRule.CriticisedClaimQuoted) ->
      ("S9 (missing-critique, major): its quote is not in the article — quote the article's sentence that states the criticised " +
        "claim (or the conclusion the critique would change), copied exactly"),
    answer(PanelRule.PairNamed(Raw("S1"))) ->
      ("S1 (missing-critique, major) names no pair of the critique checklist (its `pair` is empty): name the pair it is about in " +
        "`pair` (src>dst) — a disagreement with no recorded edge is a proposal, not a MAJOR"),
    at(Where("not material a>b"), PanelRule.ChecklistPair) -> "not material a>b: not a pair of the critique checklist",
    at(Where("not material a>b"), PanelRule.PairOpen(Raw("addressed"))) ->
      "not material a>b: the pair is already addressed",
    at(Where("not material a>b"), PanelRule.FindingStands) ->
      "not material a>b: a missing-critique MAJOR names it too (the finding stands)",
    at(Where("not material a>b"), PanelRule.ReasonStated) -> "not material a>b: no reason",
    at(Where("not material a>b"), PanelRule.ReasonWithin(Count(301L), Count(300L))) ->
      "not material a>b: the reason is 301 characters (at most 300)",
    answer(PanelRule.PairsRuled(Vector(Raw("a>b"), Raw("c>d")))) ->
      ("open pairs of the critique checklist without a ruling: a>b, c>d — for each, a missing-critique " +
        "MAJOR naming it in `pair` (it bears on a claim the article makes), or a `notMaterial` entry with the reason"),
    answer(PanelRule.CheckId(Vector(Raw("R1"), Raw("R4")))) ->
      "a check with no id — give the id of the finding it checks (R1, R4)",
    at(Where("check of 'S2'"), PanelRule.AskedFinding(Vector(Raw("R1"), Raw("R4")))) ->
      "check of 'S2': not one of the findings you were asked to check (R1, R4)",
    at(
      Where("check of R1"),
      PanelRule.QuoteInArticle(
        Raw(
          "Tin reached the Aegean by sea from the west, carried on ships that sailed past Crete and Rhodes to the Levant"
        )
      )
    ) -> s"check of R1: the quote is not in the current article — copy it exactly: \"${quote.take(80)}\"",
    at(Where("check of R1"), PanelRule.CheckQuoted(Shows.Fixed)) ->
      "check of R1: no quote — quote the current text that shows it fixed",
    at(Where("check of R4"), PanelRule.CheckQuoted(Shows.StillThere)) ->
      "check of R4: no quote — quote the current text that shows it still there",
    at(Where("check of R1"), PanelRule.CheckNoted) ->
      "check of R1: no note — say why it is not fixed (the fixer reads it)",
    at(Where("R1"), PanelRule.CheckGiven) -> "R1: no check — say whether it is fixed",
    answer(PanelRule.KnownFinding(Raw("Z9"))) -> "there is no finding 'Z9'",
    at(Where("rejection of 'Z9'"), PanelRule.FindingListed) -> "rejection of 'Z9': no such finding",
    answer(PanelRule.Rejectable(Raw("M1"), Unrejectable.MechanicalCheck)) ->
      "M1 is a mechanical check; it cannot be rejected, only fixed",
    answer(PanelRule.Rejectable(Raw("C1"), Unrejectable.ReadersConfusion)) ->
      "C1 is a reader's confusion; it cannot be rejected — define or restructure instead",
    answer(PanelRule.RejectionReason(Raw("S1"))) -> "rejection of S1 gives no reason — say why the finding is wrong",
    answer(PanelRule.RejectionCitekey(Raw("S1"), Raw("nobody"))) ->
      "rejection of S1 names 'nobody', which is not a citekey",
    at(Where("rejection of S1"), PanelRule.RejectionQuote(Raw("knapp2016_tin"))) ->
      "rejection of S1: the quote is not in the record of knapp2016_tin — quote it exactly or apply the finding",
    answer(PanelRule.WontFixId) -> "a won't-fix with no id — give the id of the minor finding you decline",
    at(Where("won't fix 'Z9'"), PanelRule.SuchFinding) -> "won't fix 'Z9': there is no such finding",
    answer(PanelRule.OnlyMinorDeclined(Raw("R8"))) ->
      "R8 is MAJOR: apply it or reject it — only a minor finding can be declined",
    at(Where("won't fix R1"), PanelRule.GiveReason) -> "won't fix R1: give the reason",
    answer(PanelRule.PairAnswered(Raw("S1"), Raw("zz2024_iso"), Raw("knapp2016_tin"))) ->
      ("S1 is listed as applied, but [[zz2024_iso]] and [[knapp2016_tin]] are not cited in one paragraph — cite both where " +
        "the critique is discussed, or set it aside (`setAside`) with a reason"),
    answer(
      PanelRule.CritiquesDiscussed(
        Vector(Raw("zz2020_new"), Raw("zz2019_old")),
        Vector(
          Discussed(Raw("zz2021_crit>zz2020_new"), Vector(Raw("challenges"))),
          Discussed(Raw("zz2022_b>zz2019_old"), Vector(Raw("refutes"), Raw("updates")))
        )
      )
    ) -> ("your edits cite [[zz2020_new]], [[zz2019_old]], whose recorded critiques no paragraph discusses: " +
      "zz2021_crit>zz2020_new (challenges), zz2022_b>zz2019_old (refutes/updates) — for each, cite the critic in the " +
      "paragraph that cites the criticised work, or set the pair aside (`setAside` with its `pair`, no `id`) with a reason"),
    answer(PanelRule.ClosedKept(Raw("R1 (closed): the text it fixed is back"))) ->
      "your edits reopen a closed finding — R1 (closed): the text it fixed is back: keep what fixed it",
    answer(PanelRule.MajorDecided(Raw("S1"), Decision.ApplyOrReject)) ->
      "major finding S1 is neither applied nor rejected",
    answer(PanelRule.MajorDecided(Raw("R8"), Decision.DeclineUnfiltered)) ->
      ("major finding R8 is neither applied, rejected nor declined — a coverage finding on an article that records " +
        "no filter needs the filter recorded, which no edit does: put it in wontFix with that reason (it stays open for a person)"),
    answer(PanelRule.EditMade(Vector(Raw("C1"), Raw("S1")))) ->
      "you list C1, S1 as applied, but no edit changed the article",
    at(Where("set aside a>b"), PanelRule.ChecklistEdge(Raw("a>b"), Remedy.Unstated)) ->
      "set aside a>b: a>b is not a pair of the article's critique checklist (no recorded edge)",
    at(Where("set aside S1"), PanelRule.ChecklistEdge(Raw("a>b"), Remedy.ApplyOrReject)) ->
      "set aside S1: a>b is not a pair of the article's critique checklist (no recorded edge) — apply or reject the finding",
    answer(PanelRule.SetAsideNamed) ->
      "a set-aside with neither an id nor a pair — give the finding's id, or the pair (src>dst)",
    at(Where("set aside R1"), PanelRule.MissingCritiqueOnly) ->
      "set aside R1: only a missing-critique finding can be set aside",
    at(Where("set aside S2"), PanelRule.FindingPair) ->
      "set aside S2: the finding names no pair — give it in `pair` (src>dst)",
    answer(PanelRule.ReasonsUnfiltered) ->
      ("the article records no filter, so it has no generated Not used list: " + "leave `reasons` empty"),
    at(Where("reason for [[x]]"), PanelRule.StoredReason) -> "reason for [[x]]: empty, and no reason is stored for it",
    at(Where("reason for [[x]]"), PanelRule.FilterReturns) ->
      "reason for [[x]]: the filter does not return it — give reasons only for its sources",
    at(Where("reason for [[x]]"), PanelRule.LeftOut) ->
      "reason for [[x]]: the article cites it — a reason is only for a source it leaves out",
    answer(PanelRule.StillUnplaced(Vector(Raw("M1")), Vector(Raw("a"), Raw("b")))) ->
      "M1: still neither cited nor given a reason: [[a]], [[b]]"
  )

  test("every panel rule renders the sentence the check wrote before it was typed"):
    panel.foreach((problem, expected) => assertEquals(problem.sentence, expected))
    val shown = panel.collect { case (Content.Answer(Requirement.Panel(r)), _) => r.productPrefix } ++
      panel.collect { case (Content.Proposal(Requirement.Panel(r), _), _) => r.productPrefix }
    assertEquals(shown.toSet, cases[PanelRule])

  test("more than twenty sources still unplaced: the first twenty show, then how many more"):
    val keys =
      Vector("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t")
    val left = Vector(
      Raw("a"),
      Raw("b"),
      Raw("c"),
      Raw("d"),
      Raw("e"),
      Raw("f"),
      Raw("g"),
      Raw("h"),
      Raw("i"),
      Raw("j"),
      Raw("k"),
      Raw("l"),
      Raw("m"),
      Raw("n"),
      Raw("o"),
      Raw("p"),
      Raw("q"),
      Raw("r"),
      Raw("s"),
      Raw("t"),
      Raw("u"),
      Raw("v"),
      Raw("w")
    )
    assertEquals(
      answer(PanelRule.StillUnplaced(Vector(Raw("M1"), Raw("M2")), left)).sentence,
      "M1, M2: still neither cited nor given a reason: " + keys.map(ck => s"[[$ck]]").mkString(", ") + " (+3 more)"
    )

  test("a dropped proposal of an article plan says what the plan's check said before it was typed"):
    assertEquals(Content.Answer(Requirement.ProposedOnce(Raw("tin"))).sentence, "'tin' proposed twice")
    assertEquals(
      Content.Proposal(Requirement.SlugFree, Where("'old'")).sentence,
      "'old': an article with this slug exists (or did)"
    )
    assertEquals(
      Content
        .Proposal(Requirement.ArticleSources(Raw("search {topics: tin}"), Count(0L), Count(2L)), Where("'thin'"))
        .sentence,
      "'thin': search {topics: tin} returns 0 sources; at least 2"
    )
    assertEquals(
      Content
        .Proposal(Requirement.ArticleSources(Raw("search {topics: tin}"), Count(1L), Count(2L)), Where("'thin'"))
        .sentence,
      "'thin': search {topics: tin} returns 1 source; at least 2"
    )
    // the old text: f"'$slug': ${share * 100}%.0f%% of its sources are [[$o]]'s already (less than ${max * 100}%.0f%%)"
    assertEquals(
      Content
        .Proposal(Requirement.ArticleOverlap(Share(0.756), Raw("cop"), Share(0.5)), Where("'copper-again'"))
        .sentence,
      f"'copper-again': ${0.756 * 100}%.0f%% of its sources are [[cop]]'s already (less than ${0.5 * 100}%.0f%%)"
    )
    assertEquals(
      (Content.NotProposed(Content.Proposal(Requirement.ArticleTitle, Where("article 'x'"))): Problem).sentence,
      "not proposed: article 'x': it has no title"
    )
    assertEquals(
      Content.Answer(Requirement.ArticlePassed).sentence,
      "no proposed article passed the checks: fix the ones above or propose others"
    )
