package com.histmersion.domain

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
  private val open = FindingGuards(0.0, 1000000L, Nil, (_, _, _) => true)

  private val window = Window(
    1,
    Some("Results"),
    "The Uluburun ship carried ten tons of copper and one ton of tin. Its cargo dates to about 1320 BCE."
  )

  private def finding(quote: String, conf: String = "high", start: Option[StatedYear] = None) =
    FindingProposal(
      "The ship carried copper and tin.",
      quote,
      Some("Uluburun"),
      None,
      start,
      None,
      None,
      List("Bronze Age"),
      conf
    )

  test("windows: paragraph-aligned, under the size, each under its heading"):
    val body = "# One\n\n" + ("a" * 400) + "\n\n" + ("b" * 400) + "\n\n# Two\n\n" + ("c" * 400)
    val ws = windows(body, 900, 10)
    assert(ws.forall(_.text.length <= 900), ws.map(_.text.length))
    assertEquals(ws.head.heading, Some("One"))
    assertEquals(ws.last.heading, Some("Two"))
    assertEquals(ws.map(_.index), (1 to ws.size).toList)

  test("windows: more than the cap are spread evenly from first to last"):
    val body = (1 to 20).map(i => s"# H$i\n\n" + ("x" * 450)).mkString("\n\n")
    val ws = windows(body, 500, 4)
    assertEquals(ws.size, 4)
    assertEquals(ws.head.heading, Some("H1"))
    assertEquals(ws.last.heading, Some("H20"))

  test("classification: status vocabulary, summary, period converted in code"):
    val c = Classification(
      "A summary.",
      List("trade"),
      List("Anatolia"),
      List("Bronze Age", "tin trade"),
      Some(StatedPeriod(StatedYear(1600, "BCE"), StatedYear(1200, "BCE"))),
      "current",
      "widely cited"
    )
    val (v, ws) = checkClassification(c, 3000000, last = false).toOption.get
    assertEquals(v.period, Some(Period(-1600, -1200)))
    assertEquals(v.status, Source.Status.Current)
    assertEquals(v.tags, List("bronze_age", "tin_trade"))
    assertEquals(ws, Nil)

  test("classification: a bad status is rejected; a raw-BP period is retried, then dropped on the last attempt"):
    val bad = Classification("S", Nil, Nil, Nil, None, "debunk", "")
    assert(checkClassification(bad, 3000000, last = true).isLeft)
    val bp = Classification(
      "S",
      Nil,
      Nil,
      Nil,
      Some(StatedPeriod(StatedYear(3270, "BP"), StatedYear(3200, "BP"))),
      "unassessed",
      ""
    )
    assert(checkClassification(bp, 3000000, last = false).left.exists(_.exists(_.contains("uncalibrated"))))
    val (v, ws) = checkClassification(bp, 3000000, last = true).toOption.get
    assertEquals(v.period, None)
    assert(ws.exists(_.contains("period dropped")))

  test("findings: a quote that is not in the window sends the answer back; on the last attempt it is dropped"):
    val f = Findings(
      List(
        finding("The Uluburun ship carried ten tons of copper and one ton of tin."),
        finding("The ship was built from Lebanese cedar in a Phoenician yard.")
      )
    )
    val first = checkFindings(f, window, 0.9, 3000000, None, Set.empty, open, last = false)
    assert(first.left.exists(ps => ps.size == 1 && ps.head.contains("quote is not in the text")), first)
    val (kept, warnings) = checkFindings(f, window, 0.9, 3000000, None, Set.empty, open, last = true).toOption.get
    assertEquals(kept.findings.size, 1)
    assertEquals(kept.findings.head.ratio, 1.0)
    assert(warnings.exists(_.startsWith("dropped finding 2")))
    // #992: what the verifier checked travels with what it kept (agent compare's quote check)
    assertEquals((kept.proposed, kept.quoteFailed), (Some(2), Some(1)))

  test("findings: years converted, confidence checked, existing claims and repeated quotes skipped"):
    val q = "Its cargo dates to about 1320 BCE."
    val f = Findings(List(finding(q, start = Some(StatedYear(1320, "BCE"))), finding(q), finding(q, conf = "sure")))
    assert(
      checkFindings(f, window, 0.9, 3000000, None, Set.empty, open, last = false).left
        .exists(_.exists(_.contains("confidence")))
    )
    val (kept, _) = checkFindings(f, window, 0.9, 3000000, None, Set.empty, open, last = true).toOption.get
    assertEquals(kept.findings.map(_.start), List(Some(-1320L))) // the repeat is gone, the bad one dropped
    assertEquals(kept.findings.head.tags, List("bronze_age"))
    val (none, _) = checkFindings(
      f,
      window,
      0.9,
      3000000,
      None,
      Set("the ship carried copper and tin."),
      open,
      last = true
    ).toOption.get
    assertEquals(none.findings, Nil)

  test("findings outside the source's own period are kept with a warning"):
    val f = Findings(List(finding("Its cargo dates to about 1320 BCE.", start = Some(StatedYear(1320, "BCE")))))
    val (kept, ws) =
      checkFindings(f, window, 0.9, 3000000, Some(Period(-3000, -2000)), Set.empty, open, last = false).toOption.get
    assertEquals(kept.findings.size, 1)
    assert(ws.exists(_.contains("outside")), ws)

  test("#981 findings: the model's place kind becomes a typed hint; a word it does not know is no hint"):
    val q = "The Uluburun ship carried ten tons of copper and one ton of tin."
    def kind(k: Option[String]) =
      checkFindings(
        Findings(List(finding(q).modify(_.placeKind).setTo(k))),
        window,
        0.85,
        3000000,
        None,
        Set.empty,
        open,
        last = false
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
    assertEquals(v.findings.head.period, Some(Period(800, 900)))

  test("notes: dangling links are rejected"):
    assert(checkNotes(Notes("See [[zz2020_x]]."), Set("zz2020_x"), _ => false).isRight)
    assert(checkNotes(Notes("See [[nope]]."), Set("zz2020_x"), _ => false).isLeft)
    assert(checkNotes(Notes("  "), _ => true, _ => true).isLeft)

  private val loaded = Loaded(
    "zz2020_x",
    "T",
    Nil,
    Some(2020),
    None,
    "unassessed",
    hasSummary = false,
    hasNotes = true,
    hasPeriod = false,
    None,
    List("trade"),
    Nil,
    Nil,
    Some("2026-01-01T00:00:00.000000Z"),
    "",
    Nil
  )
  private val classified = Classified(
    "Summary.",
    List("trade", "metallurgy"),
    List("Anatolia"),
    List("tin"),
    Some(Period(-1600, -1200)),
    None,
    Source.Status.Current,
    "cited"
  )

  test("patch: fills only what is empty, merges lists, carries the read's updatedAt"):
    val (p, fields, proposal) = patch(loaded, classified, List(Finding.Input("c")), Some("Notes [[zz2020_x]]."))
    assertEquals(p.summary, Some("Summary."))
    assertEquals(p.notes, None) // the source already has notes
    assertEquals(p.topics, Some(List("trade", "metallurgy")))
    assertEquals(p.period, Some(Period.Input(-1600, -1200)))
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
    val w = WorkRef(title = Some("Bronze Age Tin: a Review"), year = Some(2017), authors = List("A. Bernard Knapp"))
    val diffs = compareMetadata("knapp2016_tin", "Bronze age tin — a review", List("Knapp, A. Bernard"), Some(2016), w)
    assertEquals(diffs.size, 2, diffs)
    assert(diffs.exists(_.contains("year 2016 differs from the upstream record's 2017")), diffs)
    assert(diffs.exists(_.startsWith("citekey year:")), diffs)
    assertEquals(
      compareMetadata(
        "knapp2016_tin",
        "Bronze Age Tin: a Review",
        List("Knapp, A. Bernard"),
        Some(2016),
        w.modify(_.year).setTo(Some(2016))
      ),
      Nil
    )
    val other =
      compareMetadata("knapp2016_tin", "Another title", List("Smith, J."), Some(2016), w.modify(_.year).setTo(Some(2016)))
    assert(other.exists(_.startsWith("title differs")), other)
    assert(other.exists(_.contains("A. Bernard Knapp is not among")), other)
    assert(
      compareMetadata("x2016_y", "T", Nil, None, WorkRef(year = Some(2016))).exists(_.contains("no year recorded"))
    )

  test("close lint: no findings, no period, still unassessed"):
    assertEquals(
      closeLint(0, hasPeriod = false, Source.Status.Unassessed).map(_.takeWhile(_ != ':')),
      List(Lint.NoFindings, Lint.NoPeriod, Lint.UnassessedStatus)
    )
    assertEquals(closeLint(3, hasPeriod = true, Source.Status.Current), Nil)
