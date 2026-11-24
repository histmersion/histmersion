package com.histmersion
package domain
import com.histmersion.util.json.RawJson

import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.EnrichSource.*
import com.histmersion.api.catalogue.{Geocode, Period}
import com.histmersion.api.checks.Lint
import com.histmersion.api.library.WorkRef
import com.histmersion.api.library.Source
import com.histmersion.api.library.Finding
import com.histmersion.domain.library.Confidence
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.library.Citekey
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
@tolerated(LintRule.SilentCompare, "pending G53 (no comparison of unrelated types)")
class EnrichSourceSuite extends munit.FunSuite:

  /** No guard fires: every place resolves, no phrase, a gap no source reaches (#1424 has its own suite). */
  private val open = FindingGuards(
    EnrichSource.FindingGuards.MinPlacedShare(0.0),
    EnrichSource.FindingGuards.CenturyGapYears(1000000L),
    Nil,
    (_, _, _) => true
  )

  private val window = Window(
    EnrichSource.Window.Index(1),
    Some(EnrichSource.Window.Heading("Results")),
    EnrichSource.Window.Text(
      "The Uluburun ship carried ten tons of copper and one ton of tin. Its cargo dates to about 1320 BCE."
    )
  )

  private def finding(
      quote: AgentProposals.Proposal.Quote,
      conf: Confidence.Wire = Confidence.Wire("high"),
      start: Option[StatedYear] = None
  ) =
    FindingProposal(
      EnrichSource.FindingProposal.Claim("The ship carried copper and tin."),
      quote,
      Some(EnrichSource.FindingProposal.Place("Uluburun")),
      None,
      start,
      None,
      None,
      List(EnrichSource.Tag("Bronze Age")),
      conf
    )

  test("windows: paragraph-aligned, under the size, each under its heading"):
    val body = SourceBody("# One\n\n" + ("a" * 400) + "\n\n" + ("b" * 400) + "\n\n# Two\n\n" + ("c" * 400))
    val ws = windows(body, EnrichSource.WindowChars(900), EnrichSource.MaxWindows(10))
    assert(ws.forall(_.text.value.length <= 900), ws.map(_.text.value.length))
    assertEquals(ws.head.heading, Some("One"))
    assertEquals(ws.last.heading, Some("Two"))
    assertEquals(ws.map(_.index), (1 to ws.size).toList)

  test("windows: more than the cap are spread evenly from first to last"):
    val body = SourceBody((1 to 20).map(i => s"# H$i\n\n" + ("x" * 450)).mkString("\n\n"))
    val ws = windows(body, EnrichSource.WindowChars(500), EnrichSource.MaxWindows(4))
    assertEquals(ws.size, 4)
    assertEquals(ws.head.heading, Some("H1"))
    assertEquals(ws.last.heading, Some("H20"))

  test("classification: status vocabulary, summary, period converted in code"):
    val c = Classification(
      EnrichSource.Classification.Summary("A summary."),
      List(EnrichSource.Topic("trade")),
      List(EnrichSource.Region("Anatolia")),
      List(EnrichSource.Tag("Bronze Age"), EnrichSource.Tag("tin trade")),
      Some(
        StatedPeriod(
          StatedYear(AgentVerify.ToSignedYearValue(1600), AgentVerify.YearUnit.Wire("BCE")),
          StatedYear(AgentVerify.ToSignedYearValue(1200), AgentVerify.YearUnit.Wire("BCE"))
        )
      ),
      library.Source.Status.Wire("current"),
      EnrichSource.Classification.StatusEvidence("widely cited")
    )
    val (v, ws) = checkClassification(
      c,
      EnrichSource.CheckClassificationMaxAbsYear(3000000),
      last = EnrichSource.Last(false)
    ).toOption.get
    assertEquals(v.period, Some(Period(Year(-1600), Year(-1200))))
    assertEquals(v.status, Source.Status.Current)
    assertEquals(v.tags, List("bronze_age", "tin_trade"))
    assertEquals(ws, Nil)

  test("classification: a bad status is rejected; a raw-BP period is retried, then dropped on the last attempt"):
    val bad = Classification(
      EnrichSource.Classification.Summary("S"),
      Nil,
      Nil,
      Nil,
      None,
      library.Source.Status.Wire("debunk"),
      EnrichSource.Classification.StatusEvidence("")
    )
    assert(
      checkClassification(
        bad,
        EnrichSource.CheckClassificationMaxAbsYear(3000000),
        last = EnrichSource.Last(true)
      ).isLeft
    )
    val bp = Classification(
      EnrichSource.Classification.Summary("S"),
      Nil,
      Nil,
      Nil,
      Some(
        StatedPeriod(
          StatedYear(AgentVerify.ToSignedYearValue(3270), AgentVerify.YearUnit.Wire("BP")),
          StatedYear(AgentVerify.ToSignedYearValue(3200), AgentVerify.YearUnit.Wire("BP"))
        )
      ),
      library.Source.Status.Wire("unassessed"),
      EnrichSource.Classification.StatusEvidence("")
    )
    assert(
      checkClassification(bp, EnrichSource.CheckClassificationMaxAbsYear(3000000), last = EnrichSource.Last(false)).left
        .exists(
          _.exists(
            _.sentence == "period: 3270 BP is an uncalibrated radiocarbon age; it cannot be stored as a calendar year"
          )
        )
    )
    val mya = StatedPeriod(
      StatedYear(AgentVerify.ToSignedYearValue(3), AgentVerify.YearUnit.Wire("Ma")),
      StatedYear(AgentVerify.ToSignedYearValue(2), AgentVerify.YearUnit.Wire("Ma"))
    )
    assertEquals(
      convert(mya, AgentVerify.MaxAbsYear(3000000), None).left.map(_.sentences),
      Left(Vector("period: unknown year unit 'Ma' (one of: BCE, CE, calBP, BP)"))
    )
    val (v, ws) = checkClassification(
      bp,
      EnrichSource.CheckClassificationMaxAbsYear(3000000),
      last = EnrichSource.Last(true)
    ).toOption.get
    assertEquals(v.period, None)
    assert(ws.exists(_.value.contains("period dropped")))

  test("findings: a quote that is not in the window sends the answer back; on the last attempt it is dropped"):
    val f = Findings(
      List(
        finding(AgentProposals.Proposal.Quote("The Uluburun ship carried ten tons of copper and one ton of tin.")),
        finding(AgentProposals.Proposal.Quote("The ship was built from Lebanese cedar in a Phoenician yard."))
      )
    )
    val first = checkFindings(
      f,
      window,
      MinRatio(0.9),
      EnrichSource.CheckFindingsMaxAbsYear(3000000),
      None,
      Set.empty,
      open,
      last = EnrichSource.Last(false)
    )
    assert(first.left.exists(ps => ps.size == 1 && ps.head.sentence.contains("quote is not in the text")), first)
    val (kept, warnings) = checkFindings(
      f,
      window,
      MinRatio(0.9),
      EnrichSource.CheckFindingsMaxAbsYear(3000000),
      None,
      Set.empty,
      open,
      last = EnrichSource.Last(true)
    ).toOption.get
    assertEquals(kept.findings.size, 1)
    assertEquals(kept.findings.head.ratio, 1.0)
    assert(warnings.exists(_.value.startsWith("dropped finding 2")))
    // #992: what the verifier checked travels with what it kept (agent compare's quote check)
    assertEquals((kept.proposed, kept.quoteFailed), (Some(2), Some(1)))

  test("findings: years converted, confidence checked, existing claims and repeated quotes skipped"):
    val q = AgentProposals.Proposal.Quote("Its cargo dates to about 1320 BCE.")
    val f = Findings(
      List(
        finding(q, start = Some(StatedYear(AgentVerify.ToSignedYearValue(1320), AgentVerify.YearUnit.Wire("BCE")))),
        finding(q),
        finding(q, conf = Confidence.Wire("sure"))
      )
    )
    assert(
      checkFindings(
        f,
        window,
        MinRatio(0.9),
        EnrichSource.CheckFindingsMaxAbsYear(3000000),
        None,
        Set.empty,
        open,
        last = EnrichSource.Last(false)
      ).left
        .exists(_.exists(_.sentence.contains("confidence")))
    )
    val (kept, _) = checkFindings(
      f,
      window,
      MinRatio(0.9),
      EnrichSource.CheckFindingsMaxAbsYear(3000000),
      None,
      Set.empty,
      open,
      last = EnrichSource.Last(true)
    ).toOption.get
    assertEquals(kept.findings.map(_.start), List(Some(-1320L))) // the repeat is gone, the bad one dropped
    assertEquals(kept.findings.head.tags, List("bronze_age"))
    val (none, _) = checkFindings(
      f,
      window,
      MinRatio(0.9),
      EnrichSource.CheckFindingsMaxAbsYear(3000000),
      None,
      Set(EnrichSource.Loaded.ExistingClaims("the ship carried copper and tin.")),
      open,
      last = EnrichSource.Last(true)
    ).toOption.get
    assertEquals(none.findings, Nil)

  test("findings outside the source's own period are kept with a warning"):
    val f = Findings(
      List(
        finding(
          AgentProposals.Proposal.Quote("Its cargo dates to about 1320 BCE."),
          start = Some(StatedYear(AgentVerify.ToSignedYearValue(1320), AgentVerify.YearUnit.Wire("BCE")))
        )
      )
    )
    val (kept, ws) =
      checkFindings(
        f,
        window,
        MinRatio(0.9),
        EnrichSource.CheckFindingsMaxAbsYear(3000000),
        Some(Period(Year(-3000), Year(-2000))),
        Set.empty,
        open,
        last = EnrichSource.Last(false)
      ).toOption.get
    assertEquals(kept.findings.size, 1)
    assert(ws.exists(_.value.contains("outside")), ws)

  test("#981 findings: the model's place kind becomes a typed hint; a word it does not know is no hint"):
    val q = AgentProposals.Proposal.Quote("The Uluburun ship carried ten tons of copper and one ton of tin.")
    def kind(k: Option[Geocode.KindHint.Wire]) =
      checkFindings(
        Findings(List(finding(q).modify(_.placeKind).setTo(k))),
        window,
        MinRatio(0.85),
        EnrichSource.CheckFindingsMaxAbsYear(3000000),
        None,
        Set.empty,
        open,
        last = EnrichSource.Last(false)
      ).toOption.get._1.findings.head.placeKind
    assertEquals(kind(Some(Geocode.KindHint.Wire("site"))), Some(Geocode.KindHint.Site))
    assertEquals(kind(Some(Geocode.KindHint.Wire("Water"))), Some(Geocode.KindHint.Water))
    assertEquals(kind(Some(Geocode.KindHint.Wire("unknown"))), None)
    assertEquals(kind(Some(Geocode.KindHint.Wire("nebula"))), None)
    assertEquals(kind(None), None)

  test("#981 findings: a checkpoint written before placeKind existed still decodes (a paused run resumes)"):
    val old = """{"findings":[{"claim":"c","quote":"q","ratio":1.0,"place":"Albania","start":800,"end":900,""" +
      """"periodName":null,"tags":[],"confidence":"high"}]}"""
    val v = RawJson.decode[VerifiedFindings](old).fold(e => fail(e), identity)
    assertEquals(v.findings.head.place, Some("Albania"))
    assertEquals(v.findings.head.placeKind, None)
    assertEquals(v.findings.head.period, Some(Period(Year(800), Year(900))))

  test("notes: dangling links are rejected"):
    assert(checkNotes(Notes(EnrichSource.Notes.Value("See [[zz2020_x]].")), Set("zz2020_x"), _ => false).isRight)
    assert(checkNotes(Notes(EnrichSource.Notes.Value("See [[nope]].")), Set("zz2020_x"), _ => false).isLeft)
    assert(checkNotes(Notes(EnrichSource.Notes.Value("  ")), _ => true, _ => true).isLeft)

  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  private val loaded = Loaded(
    Citekey("zz2020_x"),
    EnrichSource.Loaded.Title("T"),
    Nil,
    Some(com.histmersion.Year(2020)),
    None,
    Source.Status.Wire("unassessed"),
    hasSummary = EnrichSource.Loaded.HasSummary(false),
    hasNotes = EnrichSource.Loaded.HasNotes(true),
    hasPeriod = EnrichSource.Loaded.HasPeriod(false),
    None,
    List(EnrichSource.Topic("trade")),
    Nil,
    Nil,
    Some(Source.UpdatedAt("2026-01-01T00:00:00.000000Z")),
    EnrichSource.Loaded.Head(""),
    Nil
  )
  private val classified = Classified(
    EnrichSource.Classified.Summary("Summary."),
    List(EnrichSource.Topic("trade"), EnrichSource.Topic("metallurgy")),
    List(EnrichSource.Region("Anatolia")),
    List(EnrichSource.Tag("tin")),
    Some(Period(Year(-1600), Year(-1200))),
    None,
    Source.Status.Current,
    EnrichSource.Classified.StatusEvidence("cited")
  )

  test("patch: fills only what is empty, merges lists, carries the read's updatedAt"):
    val (p, fields, proposal) = patch(
      loaded,
      classified,
      List(Finding.Input(Finding.Claim("c"))),
      Some(EnrichSource.PatchNotes("Notes [[zz2020_x]]."))
    )
    assertEquals(p.summary, Some("Summary."))
    assertEquals(p.notes, None) // the source already has notes
    assertEquals(p.topics, Some(List("trade", "metallurgy")))
    assertEquals(p.period, Some(Period.Input(Year(-1600), Year(-1200))))
    assertEquals(p.metadata.flatMap(_.status), Some(Source.Status.Current))
    assertEquals(p.ifUpdatedAt, loaded.updatedAt)
    assertEquals(proposal, None)
    assert(fields.contains("1 finding(s)") && !fields.contains("notes"), fields)
    // a tag the source already has is neither re-sent nor reported
    val (p2, fields2, _) = patch(loaded.modify(_.tags).setTo(List(EnrichSource.Tag("tin"))), classified, Nil, None)
    assertEquals(p2.tags, None)
    assert(!fields2.contains("tags"), fields2)

  test("patch: a status other than current/foundational is proposed to a person, not written"):
    val (p, _, proposal) =
      patch(
        loaded,
        classified
          .modify(_.status)
          .setTo(Source.Status.Debunked)
          .modify(_.statusEvidence)
          .setTo(EnrichSource.Classified.StatusEvidence("refuted by X")),
        Nil,
        None
      )
    assertEquals(p.metadata, None)
    assertEquals(proposal, Some("debunked: refuted by X"))
    val (p2, _, prop2) = patch(loaded.modify(_.status).setTo(Source.Status.Wire("contested")), classified, Nil, None)
    assertEquals(p2.metadata, None) // never overwrites an assessed status
    assertEquals(prop2, Some("current: cited"))

  test("metadata: the record against the upstream one — title, year, first author, the citekey's year"):
    val w = WorkRef(
      title = (Some("Bronze Age Tin: a Review")).map(api.library.Title.apply),
      year = (Some(2017)).map(Year.apply),
      authors = (List("A. Bernard Knapp")).map(api.library.AuthorName.apply)
    )
    val diffs = compareMetadata(
      Citekey("knapp2016_tin"),
      Title("Bronze age tin — a review"),
      List(AuthorName("Knapp, A. Bernard")),
      Some(com.histmersion.Year(2016)),
      w
    )
    assertEquals(diffs.size, 2, diffs)
    assert(diffs.exists(_.value.contains("year 2016 differs from the upstream record's 2017")), diffs)
    assert(diffs.exists(_.value.startsWith("citekey year:")), diffs)
    assertEquals(
      compareMetadata(
        Citekey("knapp2016_tin"),
        Title("Bronze Age Tin: a Review"),
        List(AuthorName("Knapp, A. Bernard")),
        Some(com.histmersion.Year(2016)),
        w.modify(_.year).setTo(Some(Year(2016)))
      ),
      Nil
    )
    val other =
      compareMetadata(
        Citekey("knapp2016_tin"),
        Title("Another title"),
        List(AuthorName("Smith, J.")),
        Some(com.histmersion.Year(2016)),
        w.modify(_.year).setTo(Some(Year(2016)))
      )
    assert(other.exists(_.value.startsWith("title differs")), other)
    assert(other.exists(_.value.contains("A. Bernard Knapp is not among")), other)
    assert(
      compareMetadata(Citekey("x2016_y"), Title("T"), Nil, None, WorkRef(year = (Some(2016)).map(Year.apply)))
        .exists(_.value.contains("no year recorded"))
    )

  test("close lint: no findings, no period, still unassessed"):
    assertEquals(
      closeLint(
        EnrichSource.FindingCount(0),
        hasPeriod = EnrichSource.Loaded.HasPeriod(false),
        Source.Status.Unassessed
      ).map(_.value.takeWhile(_ != ':')),
      List(Lint.NoFindings, Lint.NoPeriod, Lint.UnassessedStatus)
    )
    assertEquals(
      closeLint(EnrichSource.FindingCount(3), hasPeriod = EnrichSource.Loaded.HasPeriod(true), Source.Status.Current),
      Nil
    )
