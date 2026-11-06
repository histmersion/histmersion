package com.histmersion.domain

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

class EnrichSourceSuite extends munit.FunSuite:

  /** No guard fires: every place resolves, no phrase, a gap no source reaches (#1424 has its own suite). */
  private val open = FindingGuards(0.0, com.histmersion.domain.EnrichSource.FindingGuards.CenturyGapYears(1000000L), Nil, (_, _, _) => true)

  private val window = Window(
    com.histmersion.domain.EnrichSource.Window.Index(1),
    Some(com.histmersion.domain.EnrichSource.Window.Heading("Results")),
    "The Uluburun ship carried ten tons of copper and one ton of tin. Its cargo dates to about 1320 BCE."
  )

  private def finding(quote: String, conf: String = "high", start: Option[StatedYear] = None) =
    FindingProposal(
      com.histmersion.domain.EnrichSource.FindingProposal.Claim("The ship carried copper and tin."),
      quote,
      Some(com.histmersion.domain.EnrichSource.FindingProposal.Place("Uluburun")),
      None,
      start,
      None,
      None,
      List(com.histmersion.domain.EnrichSource.Tag("Bronze Age")),
      conf
    )

  test("windows: paragraph-aligned, under the size, each under its heading"):
    val body = "# One\n\n" + ("a" * 400) + "\n\n" + ("b" * 400) + "\n\n# Two\n\n" + ("c" * 400)
    val ws = windows(body, com.histmersion.domain.EnrichSource.WindowChars(900), com.histmersion.domain.EnrichSource.MaxWindows(10))
    assert(ws.forall(_.text.length <= 900), ws.map(_.text.length))
    assertEquals(ws.head.heading, Some("One"))
    assertEquals(ws.last.heading, Some("Two"))
    assertEquals(ws.map(_.index), (1 to ws.size).toList)

  test("windows: more than the cap are spread evenly from first to last"):
    val body = (1 to 20).map(i => s"# H$i\n\n" + ("x" * 450)).mkString("\n\n")
    val ws = windows(body, com.histmersion.domain.EnrichSource.WindowChars(500), com.histmersion.domain.EnrichSource.MaxWindows(4))
    assertEquals(ws.size, 4)
    assertEquals(ws.head.heading, Some("H1"))
    assertEquals(ws.last.heading, Some("H20"))

  test("classification: status vocabulary, summary, period converted in code"):
    val c = Classification(
      com.histmersion.domain.EnrichSource.Classification.Summary("A summary."),
      List(com.histmersion.domain.EnrichSource.Topic("trade")),
      List(com.histmersion.domain.EnrichSource.Region("Anatolia")),
      List(com.histmersion.domain.EnrichSource.Tag("Bronze Age"), com.histmersion.domain.EnrichSource.Tag("tin trade")),
      Some(StatedPeriod(StatedYear(com.histmersion.domain.AgentVerify.ToSignedYearValue(1600), com.histmersion.domain.AgentVerify.YearUnit.Wire("BCE")), StatedYear(com.histmersion.domain.AgentVerify.ToSignedYearValue(1200), com.histmersion.domain.AgentVerify.YearUnit.Wire("BCE")))),
      com.histmersion.domain.library.Source.Status.Wire("current"),
      com.histmersion.domain.EnrichSource.Classification.StatusEvidence("widely cited")
    )
    val (v, ws) = checkClassification(c, com.histmersion.domain.EnrichSource.CheckClassificationMaxAbsYear(3000000), last = com.histmersion.domain.EnrichSource.Last(false)).toOption.get
    assertEquals(v.period, Some(Period(com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1200))))
    assertEquals(v.status, Source.Status.Current)
    assertEquals(v.tags, List("bronze_age", "tin_trade"))
    assertEquals(ws, Nil)

  test("classification: a bad status is rejected; a raw-BP period is retried, then dropped on the last attempt"):
    val bad = Classification(com.histmersion.domain.EnrichSource.Classification.Summary("S"), Nil, Nil, Nil, None, com.histmersion.domain.library.Source.Status.Wire("debunk"), com.histmersion.domain.EnrichSource.Classification.StatusEvidence(""))
    assert(checkClassification(bad, com.histmersion.domain.EnrichSource.CheckClassificationMaxAbsYear(3000000), last = com.histmersion.domain.EnrichSource.Last(true)).isLeft)
    val bp = Classification(
      com.histmersion.domain.EnrichSource.Classification.Summary("S"),
      Nil,
      Nil,
      Nil,
      Some(StatedPeriod(StatedYear(com.histmersion.domain.AgentVerify.ToSignedYearValue(3270), com.histmersion.domain.AgentVerify.YearUnit.Wire("BP")), StatedYear(com.histmersion.domain.AgentVerify.ToSignedYearValue(3200), com.histmersion.domain.AgentVerify.YearUnit.Wire("BP")))),
      com.histmersion.domain.library.Source.Status.Wire("unassessed"),
      com.histmersion.domain.EnrichSource.Classification.StatusEvidence("")
    )
    assert(checkClassification(bp, com.histmersion.domain.EnrichSource.CheckClassificationMaxAbsYear(3000000), last = com.histmersion.domain.EnrichSource.Last(false)).left.exists(_.exists(_.contains("uncalibrated"))))
    val (v, ws) = checkClassification(bp, com.histmersion.domain.EnrichSource.CheckClassificationMaxAbsYear(3000000), last = com.histmersion.domain.EnrichSource.Last(true)).toOption.get
    assertEquals(v.period, None)
    assert(ws.exists(_.contains("period dropped")))

  test("findings: a quote that is not in the window sends the answer back; on the last attempt it is dropped"):
    val f = Findings(
      List(
        finding("The Uluburun ship carried ten tons of copper and one ton of tin."),
        finding("The ship was built from Lebanese cedar in a Phoenician yard.")
      )
    )
    val first = checkFindings(f, window, 0.9, com.histmersion.domain.EnrichSource.CheckFindingsMaxAbsYear(3000000), None, Set.empty, open, last = com.histmersion.domain.EnrichSource.Last(false))
    assert(first.left.exists(ps => ps.size == 1 && ps.head.contains("quote is not in the text")), first)
    val (kept, warnings) = checkFindings(f, window, 0.9, com.histmersion.domain.EnrichSource.CheckFindingsMaxAbsYear(3000000), None, Set.empty, open, last = com.histmersion.domain.EnrichSource.Last(true)).toOption.get
    assertEquals(kept.findings.size, 1)
    assertEquals(kept.findings.head.ratio, 1.0)
    assert(warnings.exists(_.startsWith("dropped finding 2")))
    // #992: what the verifier checked travels with what it kept (agent compare's quote check)
    assertEquals((kept.proposed, kept.quoteFailed), (Some(2), Some(1)))

  test("findings: years converted, confidence checked, existing claims and repeated quotes skipped"):
    val q = "Its cargo dates to about 1320 BCE."
    val f = Findings(List(finding(q, start = Some(StatedYear(com.histmersion.domain.AgentVerify.ToSignedYearValue(1320), com.histmersion.domain.AgentVerify.YearUnit.Wire("BCE")))), finding(q), finding(q, conf = "sure")))
    assert(
      checkFindings(f, window, 0.9, com.histmersion.domain.EnrichSource.CheckFindingsMaxAbsYear(3000000), None, Set.empty, open, last = com.histmersion.domain.EnrichSource.Last(false)).left
        .exists(_.exists(_.contains("confidence")))
    )
    val (kept, _) = checkFindings(f, window, 0.9, com.histmersion.domain.EnrichSource.CheckFindingsMaxAbsYear(3000000), None, Set.empty, open, last = com.histmersion.domain.EnrichSource.Last(true)).toOption.get
    assertEquals(kept.findings.map(_.start), List(Some(-1320L))) // the repeat is gone, the bad one dropped
    assertEquals(kept.findings.head.tags, List("bronze_age"))
    val (none, _) = checkFindings(
      f,
      window,
      0.9,
      com.histmersion.domain.EnrichSource.CheckFindingsMaxAbsYear(3000000),
      None,
      Set(com.histmersion.domain.EnrichSource.Loaded.ExistingClaims("the ship carried copper and tin.")),
      open,
      last = com.histmersion.domain.EnrichSource.Last(true)
    ).toOption.get
    assertEquals(none.findings, Nil)

  test("findings outside the source's own period are kept with a warning"):
    val f = Findings(List(finding("Its cargo dates to about 1320 BCE.", start = Some(StatedYear(com.histmersion.domain.AgentVerify.ToSignedYearValue(1320), com.histmersion.domain.AgentVerify.YearUnit.Wire("BCE"))))))
    val (kept, ws) =
      checkFindings(f, window, 0.9, com.histmersion.domain.EnrichSource.CheckFindingsMaxAbsYear(3000000), Some(Period(com.histmersion.api.catalogue.Year(-3000), com.histmersion.api.catalogue.Year(-2000))), Set.empty, open, last = com.histmersion.domain.EnrichSource.Last(false)).toOption.get
    assertEquals(kept.findings.size, 1)
    assert(ws.exists(_.contains("outside")), ws)

  test("#981 findings: the model's place kind becomes a typed hint; a word it does not know is no hint"):
    val q = "The Uluburun ship carried ten tons of copper and one ton of tin."
    def kind(k: Option[String]) =
      checkFindings(
        Findings(List(finding(q).modify(_.placeKind).setTo(k))),
        window,
        0.85,
        com.histmersion.domain.EnrichSource.CheckFindingsMaxAbsYear(3000000),
        None,
        Set.empty,
        open,
        last = com.histmersion.domain.EnrichSource.Last(false)
      ).toOption.get._1.findings.head.placeKind
    assertEquals(kind(Some("site")), Some(Geocode.KindHint.Site))
    assertEquals(kind(Some("Water")), Some(Geocode.KindHint.Water))
    assertEquals(kind(Some("unknown")), None)
    assertEquals(kind(Some("nebula")), None)
    assertEquals(kind(None), None)

  test("#981 findings: a checkpoint written before placeKind existed still decodes (a paused run resumes)"):
    import io.circe.parser.decode
    val old = """{"findings":[{"claim":"c","quote":"q","ratio":1.0,"place":"Albania","start":800,"end":900,""" +
      """"periodName":null,"tags":[],"confidence":"high"}]}"""
    val v = decode[VerifiedFindings](old).fold(e => fail(e.getMessage), identity)
    assertEquals(v.findings.head.place, Some("Albania"))
    assertEquals(v.findings.head.placeKind, None)
    assertEquals(v.findings.head.period, Some(Period(com.histmersion.api.catalogue.Year(800), com.histmersion.api.catalogue.Year(900))))

  test("notes: dangling links are rejected"):
    assert(checkNotes(Notes(com.histmersion.domain.EnrichSource.Notes.Value("See [[zz2020_x]].")), Set("zz2020_x"), _ => false).isRight)
    assert(checkNotes(Notes(com.histmersion.domain.EnrichSource.Notes.Value("See [[nope]].")), Set("zz2020_x"), _ => false).isLeft)
    assert(checkNotes(Notes(com.histmersion.domain.EnrichSource.Notes.Value("  ")), _ => true, _ => true).isLeft)

  private val loaded = Loaded(
    com.histmersion.domain.library.Citekey("zz2020_x"),
    com.histmersion.domain.EnrichSource.Loaded.Title("T"),
    Nil,
    Some(com.histmersion.Year(2020)),
    None,
    com.histmersion.api.library.Source.Status.Wire("unassessed"),
    hasSummary = com.histmersion.domain.EnrichSource.Loaded.HasSummary(false),
    hasNotes = com.histmersion.domain.EnrichSource.Loaded.HasNotes(true),
    hasPeriod = com.histmersion.domain.EnrichSource.Loaded.HasPeriod(false),
    None,
    List(com.histmersion.domain.EnrichSource.Topic("trade")),
    Nil,
    Nil,
    Some(com.histmersion.api.library.Source.UpdatedAt("2026-01-01T00:00:00.000000Z")),
    com.histmersion.domain.EnrichSource.Loaded.Head(""),
    Nil
  )
  private val classified = Classified(
    com.histmersion.domain.EnrichSource.Classified.Summary("Summary."),
    List(com.histmersion.domain.EnrichSource.Topic("trade"), com.histmersion.domain.EnrichSource.Topic("metallurgy")),
    List(com.histmersion.domain.EnrichSource.Region("Anatolia")),
    List(com.histmersion.domain.EnrichSource.Tag("tin")),
    Some(Period(com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1200))),
    None,
    Source.Status.Current,
    com.histmersion.domain.EnrichSource.Classified.StatusEvidence("cited")
  )

  test("patch: fills only what is empty, merges lists, carries the read's updatedAt"):
    val (p, fields, proposal) = patch(loaded, classified, List(Finding.Input(com.histmersion.api.library.Finding.Claim("c"))), Some(com.histmersion.domain.EnrichSource.PatchNotes("Notes [[zz2020_x]].")))
    assertEquals(p.summary, Some("Summary."))
    assertEquals(p.notes, None) // the source already has notes
    assertEquals(p.topics, Some(List("trade", "metallurgy")))
    assertEquals(p.period, Some(Period.Input(com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1200))))
    assertEquals(p.metadata.flatMap(_.status), Some(Source.Status.Current))
    assertEquals(p.ifUpdatedAt, loaded.updatedAt)
    assertEquals(proposal, None)
    assert(fields.contains("1 finding(s)") && !fields.contains("notes"), fields)
    // a tag the source already has is neither re-sent nor reported
    val (p2, fields2, _) = patch(loaded.modify(_.tags).setTo(List("tin")), classified, Nil, None)
    assertEquals(p2.tags, None)
    assert(!fields2.contains("tags"), fields2)

  test("patch: a status other than current/foundational is proposed to a person, not written"):
    val (p, _, proposal) =
      patch(loaded, classified.modify(_.status).setTo(Source.Status.Debunked).modify(_.statusEvidence).setTo("refuted by X"), Nil, None)
    assertEquals(p.metadata, None)
    assertEquals(proposal, Some("debunked: refuted by X"))
    val (p2, _, prop2) = patch(loaded.modify(_.status).setTo("contested"), classified, Nil, None)
    assertEquals(p2.metadata, None) // never overwrites an assessed status
    assertEquals(prop2, Some("current: cited"))

  test("metadata: the record against the upstream one — title, year, first author, the citekey's year"):
    val w = WorkRef(title = (Some("Bronze Age Tin: a Review")).map(com.histmersion.api.library.Title.apply), year = (Some(2017)).map(com.histmersion.api.catalogue.Year.apply), authors = (List("A. Bernard Knapp")).map(com.histmersion.api.library.AuthorName.apply))
    val diffs = compareMetadata(com.histmersion.domain.library.Citekey("knapp2016_tin"), com.histmersion.domain.Title("Bronze age tin — a review"), List(com.histmersion.domain.AuthorName("Knapp, A. Bernard")), Some(com.histmersion.Year(2016)), w)
    assertEquals(diffs.size, 2, diffs)
    assert(diffs.exists(_.contains("year 2016 differs from the upstream record's 2017")), diffs)
    assert(diffs.exists(_.startsWith("citekey year:")), diffs)
    assertEquals(
      compareMetadata(
        com.histmersion.domain.library.Citekey("knapp2016_tin"),
        com.histmersion.domain.Title("Bronze Age Tin: a Review"),
        List(com.histmersion.domain.AuthorName("Knapp, A. Bernard")),
        Some(com.histmersion.Year(2016)),
        w.modify(_.year).setTo(Some(com.histmersion.api.catalogue.Year(2016)))
      ),
      Nil
    )
    val other =
      compareMetadata(com.histmersion.domain.library.Citekey("knapp2016_tin"), com.histmersion.domain.Title("Another title"), List(com.histmersion.domain.AuthorName("Smith, J.")), Some(com.histmersion.Year(2016)), w.modify(_.year).setTo(Some(com.histmersion.api.catalogue.Year(2016))))
    assert(other.exists(_.startsWith("title differs")), other)
    assert(other.exists(_.contains("A. Bernard Knapp is not among")), other)
    assert(
      compareMetadata(com.histmersion.domain.library.Citekey("x2016_y"), com.histmersion.domain.Title("T"), Nil, None, WorkRef(year = (Some(2016)).map(com.histmersion.api.catalogue.Year.apply))).exists(_.contains("no year recorded"))
    )

  test("close lint: no findings, no period, still unassessed"):
    assertEquals(
      closeLint(com.histmersion.domain.EnrichSource.FindingCount(0), hasPeriod = com.histmersion.domain.EnrichSource.Loaded.HasPeriod(false), Source.Status.Unassessed).map(_.takeWhile(_ != ':')),
      List(Lint.NoFindings, Lint.NoPeriod, Lint.UnassessedStatus)
    )
    assertEquals(closeLint(com.histmersion.domain.EnrichSource.FindingCount(3), hasPeriod = com.histmersion.domain.EnrichSource.Loaded.HasPeriod(true), Source.Status.Current), Nil)
