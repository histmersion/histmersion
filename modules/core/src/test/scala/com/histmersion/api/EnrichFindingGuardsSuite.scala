package com.histmersion
package api

import com.histmersion.domain.library.workflows.Frame
import com.histmersion.domain.library.workflows.FindingGuards
import com.histmersion.domain.library.workflows.StatedYear
import com.histmersion.domain.library.workflows.FindingProposal
import com.histmersion.domain.library.workflows.Findings
import com.histmersion.api.catalogue.geocode.KindHint

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

import com.histmersion.domain.EnrichSource.*
import com.histmersion.api.catalogue.{Geocode, Period}
import com.histmersion.domain.EnrichSource
import com.histmersion.domain.library.Confidence
import com.histmersion.domain.AgentVerify
import com.histmersion.api.catalogue.Year
import com.histmersion.lint.{tolerated, LintRule}

/** #1424: the findings step's guards against what a smaller extractor (Haiku) got wrong — places the gazetteer does not
  * know, centuries stored as years, claims that point at a table. Each sends the answer back while attempts remain and
  * keeps it with a warning on the last attempt.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class EnrichFindingGuardsSuite extends munit.FunSuite:

  private val text =
    "The Uluburun ship carried ten tons of copper and one ton of tin. Its cargo dates to about 1320 BCE. " +
      "The town was walled in the 11th century. Vesuvius buried Pompeii in 79 CE. " +
      "Rainfall rose sharply in the next period. The hoard was buried in the 15th c. by its owners."
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val window = Frame(Frame.Index(1), None, Frame.Text(text))

  private val known = Set("Uluburun", "Pompeii")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def guards(
      share: FindingGuards.MinPlacedShare = FindingGuards.MinPlacedShare(0.5),
      gap: FindingGuards.CenturyGapYears = FindingGuards.CenturyGapYears(500),
      phrases: List[FindingGuards.PointerPhrases] =
        List("this date", "the next column", "as listed").map(FindingGuards.PointerPhrases(_))
  ) =
    FindingGuards(share, gap, phrases, (name, _, _) => known.contains(name.value))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def finding(
      claim: String,
      quote: String,
      place: Option[String] = None,
      start: Option[StatedYear] = None,
      end: Option[StatedYear] = None,
      periodName: Option[String] = None
  ) =
    FindingProposal(
      FindingProposal.Claim(claim),
      Proposal.Quote(quote),
      place.map(FindingProposal.Place(_)),
      None,
      start,
      end,
      periodName.map(FindingProposal.PeriodName(_)),
      Nil,
      Confidence.Wire("high")
    )

  private val copper = "The Uluburun ship carried ten tons of copper and one ton of tin."
  private val cargo = "Its cargo dates to about 1320 BCE."
  private val walled = "The town was walled in the 11th century."
  private val pompeii = "Vesuvius buried Pompeii in 79 CE."
  private val rain = "Rainfall rose sharply in the next period."
  private val hoard = "The hoard was buried in the 15th c. by its owners."

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def check(
      fs: List[FindingProposal],
      g: FindingGuards = guards(),
      within: Option[Period] = None,
      last: Boolean = false
  ) =
    Findings(fs).checkFindings(
      window,
      EnrichSource.MinRatio(0.9),
      EnrichSource.CheckFindingsMaxAbsYear(3000000),
      within,
      Set.empty,
      g,
      EnrichSource.Last(last)
    )

  // ── 1. places the gazetteer knows ─────────────────────────────────────────────────────────────

  test("placement: fewer than the share of named places resolving sends the answer back, listing the names"):
    val fs = List(
      finding("A ship carried copper.", copper, Some("Uluburun")),
      finding("The cargo is Late Bronze Age.", cargo, Some("eastern shipping lanes")),
      finding("A town was walled.", walled, Some("the citadel area")),
      finding("Rainfall rose.", rain, Some("the hinterland"))
    )
    val ps = check(fs).left.toOption.get
    assertEquals(ps.size, 1, ps)
    val p = ps.head
    assert(p.sentence.startsWith("only 1 of 4 places resolve to a gazetteer place (at least 50% must)"), p)
    assert(
      p.sentence.contains("'eastern shipping lanes' (finding 2)") && p.sentence.contains(
        "'the hinterland' (finding 4)"
      ),
      p
    )
    assert(
      p.sentence.contains(
        "give a place name the gazetteer knows (a site, city or region), or leave place null for a non-spatial claim"
      ),
      p
    )

  test("placement: at the share it passes; findings with no place are non-spatial and do not count"):
    val half = List(
      finding("A ship carried copper.", copper, Some("Uluburun")),
      finding(
        "Vesuvius buried Pompeii.",
        pompeii,
        Some("Pompeii"),
        Some(StatedYear(AgentVerify.ToSignedYearValue(79), AgentVerify.YearUnit.Wire("CE")))
      ),
      finding("The cargo is Late Bronze Age.", cargo, Some("eastern shipping lanes")),
      finding("Rainfall rose.", rain, Some("the hinterland"))
    )
    assert(check(half).isRight, check(half))
    // one named place, resolved, and three non-spatial claims: 1 of 1
    val nonSpatial = List(
      finding("A ship carried copper.", copper, Some("Uluburun")),
      finding("The cargo is Late Bronze Age.", cargo),
      finding("Rainfall rose.", rain),
      finding("A hoard.", hoard)
    )
    assert(check(nonSpatial).isRight, check(nonSpatial))
    // nothing named at all: nothing to place
    assert(check(List(finding("Rainfall rose.", rain))).isRight)
    // share 0 switches the guard off
    assert(
      check(
        List(finding("Rainfall rose.", rain, Some("nowhere"))),
        guards(share = FindingGuards.MinPlacedShare(0.0))
      ).isRight
    )

  test("placement: the kind hint and the finding's period reach the gazetteer"):
    var asked = List.empty[(String, KindHint, Option[Period])] // records the calls for the assertion
    val g = FindingGuards(
      FindingGuards.MinPlacedShare(0.5),
      FindingGuards.CenturyGapYears(500),
      Nil,
      (n, k, at) => { asked = asked :+ (n.value, k, at); true }
    )
    val f = finding(
      "A ship carried copper.",
      cargo,
      Some(" Uluburun "),
      Some(StatedYear(AgentVerify.ToSignedYearValue(1320), AgentVerify.YearUnit.Wire("BCE")))
    )
      .modify(_.placeKind)
      .setTo(Some(KindHint.Wire("site")))
    assert(check(List(f), g).isRight)
    assertEquals(asked, List(("Uluburun", KindHint.Site, Some(Period(Year(-1320), Year(-1320))))))

  // ── 2. centuries stored as years ──────────────────────────────────────────────────────────────

  test("century: a year below 100 for a claim or period name that mentions a century is sent back"):
    val claim = finding(
      "The town was walled in the 11th century.",
      walled,
      start = Some(StatedYear(AgentVerify.ToSignedYearValue(11), AgentVerify.YearUnit.Wire("CE")))
    )
    val ps = check(List(claim)).left.toOption.get
    assertEquals(ps.size, 1, ps)
    assert(ps.head.sentence.startsWith("finding 1 (The town was walled"), ps)
    assert(
      ps.head.sentence.contains(
        "the year 11 looks like a century stored as a year: '11th century' is the years 1001–1100 (BCE negative)"
      ),
      ps
    )
    // the period name alone is enough, and "15th c." is a century too
    val named = finding(
      "A hoard was buried.",
      hoard,
      start = Some(StatedYear(AgentVerify.ToSignedYearValue(15), AgentVerify.YearUnit.Wire("CE"))),
      periodName = Some("15th c.")
    )
    assert(
      check(List(named)).left.exists(_.head.sentence.contains("'15th century' is the years 1401–1500")),
      check(List(named))
    )
    // a BCE century stored as -5
    val bce = finding(
      "Copper reached the coast in the fifth century BCE.",
      copper,
      start = Some(StatedYear(AgentVerify.ToSignedYearValue(5), AgentVerify.YearUnit.Wire("BCE")))
    )
    assert(check(List(bce)).left.exists(_.head.sentence.contains("the year -5 looks like a century")), check(List(bce)))

  test("century: any year below 100 in a source whose own period is far later is sent back"):
    val f = finding(
      "Rainfall rose sharply.",
      rain,
      start = Some(StatedYear(AgentVerify.ToSignedYearValue(12), AgentVerify.YearUnit.Wire("CE")))
    )
    assert(
      check(List(f), within = Some(Period(Year(1000), Year(1500)))).left
        .exists(_.head.sentence.contains("'12th century' is the years 1101–1200"))
    )
    // not far enough: a source on 300–600 CE may well cite a year 79
    assert(check(List(f), within = Some(Period(Year(300), Year(600)))).isRight)
    assert(check(List(f)).isRight) // no source period, no century mentioned

  test("century: a real early-CE year the text states, a first century, and a century stored right are valid"):
    val pompeiiFinding = finding(
      "Vesuvius buried Pompeii in 79 CE.",
      pompeii,
      start = Some(StatedYear(AgentVerify.ToSignedYearValue(79), AgentVerify.YearUnit.Wire("CE")))
    )
    assert(check(List(pompeiiFinding), within = Some(Period(Year(1000), Year(1500)))).isRight)
    // stated in the quote only, as "AD 79", with a century mentioned beside it
    assert(statesYear(EnrichSource.StatesYearText("buried in AD 79, rediscovered in the 18th century"), Year(79)))
    assert(statesYear(EnrichSource.StatesYearText("in 79 B.C. the town"), Year(-79)))
    assert(
      !statesYear(EnrichSource.StatesYearText("in 179 CE"), Year(79)) && !statesYear(
        EnrichSource.StatesYearText("the 79th entry"),
        Year(79)
      )
    )
    val first = finding(
      "Copper came in the 1st century CE.",
      copper,
      start = Some(StatedYear(AgentVerify.ToSignedYearValue(1), AgentVerify.YearUnit.Wire("CE"))),
      end = Some(StatedYear(AgentVerify.ToSignedYearValue(100), AgentVerify.YearUnit.Wire("CE")))
    )
    assert(check(List(first)).isRight, check(List(first)))
    val right = finding(
      "The town was walled in the 11th century.",
      walled,
      start = Some(StatedYear(AgentVerify.ToSignedYearValue(1001), AgentVerify.YearUnit.Wire("CE"))),
      end = Some(StatedYear(AgentVerify.ToSignedYearValue(1100), AgentVerify.YearUnit.Wire("CE")))
    )
    assert(check(List(right)).isRight)
    assertEquals(centuriesMentioned(EnrichSource.Text("the eleventh and twelfth centuries")), List(11, 12))
    assertEquals(centuriesMentioned(EnrichSource.Text("11th–12th centuries")), List(11, 12))
    assertEquals(centuriesMentioned(EnrichSource.Text("the 3rd millennium")), Nil)

  // ── 3. claims that point at a table ───────────────────────────────────────────────────────────

  test("pointer: a claim that only makes sense next to the table is sent back"):
    val f = finding("Rainfall rose around this date.", rain)
    val ps = check(List(f)).left.toOption.get
    assertEquals(
      ps.map(_.sentence),
      List(
        "finding 1 (Rainfall rose around this date.): 'this date' only makes sense next to the table: " +
          "a finding must stand alone on the map: name the date/event instead of pointing at the table"
      )
    )
    assert(check(List(finding("Copper As Listed in the next column.", copper))).isLeft)
    // whole words only, and the quote (the source's own words) is not checked
    assert(check(List(finding("Rainfall rose in this dated layer.", rain))).isRight)
    assert(check(List(finding("Rainfall rose.", "Rainfall rose sharply in the next period."))).isRight)
    assertEquals(
      FindingProposal.pointerPhrase(
        FindingProposal.Claim("See THE NEXT COLUMN."),
        List(FindingGuards.PointerPhrases("the next column"))
      ),
      Some("the next column")
    )

  // ── the last attempt ──────────────────────────────────────────────────────────────────────────

  test("last attempt: the answer is kept, each guard's problem a warning"):
    val fs = List(
      finding(
        "The town was walled in the 11th century.",
        walled,
        Some("the citadel area"),
        Some(StatedYear(AgentVerify.ToSignedYearValue(11), AgentVerify.YearUnit.Wire("CE")))
      ),
      finding("Rainfall rose around this date.", rain, Some("the hinterland"))
    )
    val (kept, ws) = check(fs, last = true).toOption.get
    assertEquals(kept.findings.size, 2)
    assertEquals(kept.findings.head.start, Some(11L)) // kept as answered; a person sees the warning
    assert(
      ws.exists(w => w.value.startsWith("kept finding 1") && w.value.contains("'11th century' is the years 1001–1100")),
      ws
    )
    assert(ws.exists(w => w.value.startsWith("kept finding 2") && w.value.contains("'this date'")), ws)
    assert(ws.exists(_.value.startsWith("kept only 0 of 2 places resolve")), ws)
    // a hard failure is still dropped on the last attempt, beside a kept guard warning
    val mixed = fs :+ finding("A cedar ship.", "The ship was built from Lebanese cedar.")
    val (k2, w2) = check(mixed, last = true).toOption.get
    assertEquals(k2.findings.size, 2)
    assert(w2.exists(_.value.startsWith("dropped finding 3")), w2)
