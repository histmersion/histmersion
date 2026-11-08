package com.histmersion
package domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.catalogue.AttestationTarget

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.api.TimeSpan
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import io.circe.parser.parse
import com.histmersion.api.catalogue.{
  ArticleEventLead,
  EventLead,
  EventLeads,
  EventLinkCandidate,
  EventLinkList,
  EventLinksMarkdown,
  EventPassage,
  EventTaxonomy,
  EventTier,
  LeadFacts,
  SiteTypes
}
import com.histmersion.domain.catalogue.Event
import com.histmersion.domain.catalogue.evidence.EventMatcher
import com.histmersion.domain.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.EventEntry
import com.histmersion.api.catalogue.CatalogRecord
import com.histmersion.api.catalogue.Place
import com.histmersion.domain.catalogue.events.EventProviders

/** #1449: the deterministic matcher (time/space overlap, name matches, no attestation without a passage), the leads, an
  * article's catalogue leads, and the site types from each gazetteer.
  */
class EventLinksSuite extends munit.FunSuite:

  private val w = EventMatcher.Weights(
    km = EventMatcher.Weights.Km(150),
    years = EventMatcher.Weights.Years(100),
    name = EventMatcher.Weights.Name(1.0),
    finding = EventMatcher.Weights.Finding(0.6),
    minScore = EventMatcher.Weights.MinScore(0.3),
    minNameChars = EventMatcher.Weights.MinNameChars(5),
    maxCandidates = EventMatcher.Weights.MaxCandidates(12)
  )

  test("names worth searching: no bare kind words, no short names, longest first, distinct ignoring case"):
    assertEquals(
      EventMatcher.searchNames(
        List("Battle", "Thera", "Minoan eruption", "minoan eruption", "Ur", " Siege "),
        EventMatcher.Weights.MinNameChars(5)
      ),
      List("Minoan eruption", "Thera")
    )

  test("a finding weighs 1 at the event's place and time, 0 at the edge of the window"):
    assertEquals(
      EventMatcher.findingWeight(
        EventMatcher.NearFinding(
          EventMatcher.NearFinding.Id("f"),
          Citekey.assume("a"),
          EventMatcher.NearFinding.Km(0),
          EventMatcher.NearFinding.YearGap(0)
        ),
        w
      ),
      1.0
    )
    assertEquals(
      EventMatcher.findingWeight(
        EventMatcher.NearFinding(
          EventMatcher.NearFinding.Id("f"),
          Citekey.assume("a"),
          EventMatcher.NearFinding.Km(75),
          EventMatcher.NearFinding.YearGap(0)
        ),
        w
      ),
      0.5
    )
    assertEquals(
      EventMatcher.findingWeight(
        EventMatcher.NearFinding(
          EventMatcher.NearFinding.Id("f"),
          Citekey.assume("a"),
          EventMatcher.NearFinding.Km(75),
          EventMatcher.NearFinding.YearGap(50)
        ),
        w
      ),
      0.25
    )
    assertEquals(
      EventMatcher.findingWeight(
        EventMatcher.NearFinding(
          EventMatcher.NearFinding.Id("f"),
          Citekey.assume("a"),
          EventMatcher.NearFinding.Km(200),
          EventMatcher.NearFinding.YearGap(0)
        ),
        w
      ),
      0.0
    )

  test("candidates: a name in the text outweighs nearby findings; findings alone are capped; weak ones dropped"):
    val near = List(
      EventMatcher.NearFinding(
        EventMatcher.NearFinding.Id("a#1"),
        Citekey("near2020"),
        EventMatcher.NearFinding.Km(10),
        EventMatcher.NearFinding.YearGap(0)
      ),
      EventMatcher.NearFinding(
        EventMatcher.NearFinding.Id("a#2"),
        Citekey("near2020"),
        EventMatcher.NearFinding.Km(20),
        EventMatcher.NearFinding.YearGap(0)
      ),
      EventMatcher.NearFinding(
        EventMatcher.NearFinding.Id("b#1"),
        Citekey("far2020"),
        EventMatcher.NearFinding.Km(140),
        EventMatcher.NearFinding.YearGap(90)
      ),
      EventMatcher.NearFinding(
        EventMatcher.NearFinding.Id("c#1"),
        Citekey("named2020"),
        EventMatcher.NearFinding.Km(100),
        EventMatcher.NearFinding.YearGap(20)
      )
    )
    val named =
      Map(
        Citekey("named2020") -> List(
          EventPassage(
            EventPassage.Start(120),
            EventPassage.End(125),
            EventPassage.Quote("Thera"),
            EventPassage.Name("Thera")
          ),
          EventPassage(
            EventPassage.Start(900),
            EventPassage.End(905),
            EventPassage.Quote("Thera"),
            EventPassage.Name("Thera")
          )
        )
      )
    val cs = EventMatcher.candidates(Event.Id("thera"), near, named, w)
    assertEquals(cs.map(_.citekey), List("named2020", "near2020").map(com.histmersion.api.library.Citekey.apply))
    assertEquals(cs.head.status, Event.LinkStatus.Proposed)
    assert(cs.head.reasons.exists(_.value.contains("names it 2 times")), cs.head.reasons.toString)
    assert(cs(1).score.value <= w.finding.value, "findings alone never pass a name")
    assertEquals(cs(1).findings, List("a#1", "a#2"))

  test("no passage, no attestation: only a passage naming the event becomes one, with its quote and offsets"):
    assertEquals(EventMatcher.attestationPassage(Nil), None)
    val ps = List(
      EventPassage(
        EventPassage.Start(500),
        EventPassage.End(505),
        EventPassage.Quote("Thera"),
        EventPassage.Name("Thera")
      ),
      EventPassage(
        EventPassage.Start(80),
        EventPassage.End(95),
        EventPassage.Quote("Minoan eruption"),
        EventPassage.Name("Minoan eruption")
      )
    )
    val p = EventMatcher.attestationPassage(ps).get
    assertEquals(p.start, 80)
    val a = EventMatcher.attestation(Event.Id("thera"), Citekey("x2020"), p)
    assertEquals(
      (a.targetType, a.targetId, a.quote, a.charStart, a.charEnd),
      (AttestationTarget.Event, "thera", Some("Minoan eruption"), Some(80), Some(95))
    )

  test("a catalogue event raises a lead in counts and dates; an attested one none"):
    val f = LeadFacts(
      com.histmersion.api.catalogue.Event.Id("gvp_13879"),
      LeadFacts.Label("Eruption of Santorini"),
      LeadFacts.Kind("eruption"),
      TimeSpan(Some(Year(-1624)), None, None, Some(Year(-1596))),
      LeadFacts.Tier(EventTier.Catalogue),
      Some(LeadFacts.Vei(7)),
      None,
      findingsNear = LeadFacts.FindingsNear(7),
      km = LeadFacts.Km(300),
      years = LeadFacts.Years(100),
      proposed = LeadFacts.Proposed(0),
      attesting = LeadFacts.Attesting(0)
    )
    val l = EventLeads.of(f).get
    assertEquals(
      l.text,
      "No source in the corpus discusses this VEI 7 eruption (1624 BCE–1596 BCE) — " +
        "7 findings in the corpus within 300 km and ±100 years"
    )
    assertEquals(l.actions.map(_.kind), List("discover", "acquire", "goal"))
    assertEquals(l.actions.head.command, "histmersion discover search \"Eruption of Santorini\" --not-in-db")
    val withProposed = EventLeads.of(f.modify(_.proposed).setTo(LeadFacts.Proposed(2))).get
    assert(withProposed.text.value.contains("2 sources possibly discuss it (unconfirmed)"))
    assertEquals(withProposed.actions.last.command, "histmersion events links gvp_13879")
    assertEquals(EventLeads.of(f.modify(_.tier).setTo(LeadFacts.Tier(EventTier.Attested))), None)
    assertEquals(EventLeads.of(f.modify(_.attesting).setTo(LeadFacts.Attesting(1))), None)
    assertEquals(EventLeads.size(None, Some(LeadFacts.Magnitude(7.25))), Some("M 7.3"))

  test("an article names an event by any of its names, ignoring case; short names do not count"):
    assert(
      ArticleEventLead.mentioned(
        ArticleEventLead.Body("The Minoan eruption of Thera…"),
        List(EventEntry.Names("Eruption of Santorini"), EventEntry.Names("Minoan eruption"))
      )
    )
    assert(
      !ArticleEventLead.mentioned(
        ArticleEventLead.Body("Thera is mentioned"),
        List(EventEntry.Names("Eruption of Santorini"), EventEntry.Names("Ur"))
      )
    )
    val md = ArticleEventLead.markdown(
      List(
        ArticleEventLead(
          com.histmersion.api.catalogue.Event.Id("gvp_1"),
          ArticleEventLead.Label("Eruption of X"),
          ArticleEventLead.Kind("eruption"),
          ArticleEventLead.Years("1600 BCE"),
          Some(ArticleEventLead.Size("VEI 6")),
          ArticleEventLead.FindingsNear(3),
          ArticleEventLead.Provider("GVP")
        )
      )
    )
    assert(md.value.contains("never names") && md.value.contains("a lead, not evidence"), md)
    assertEquals(ArticleEventLead.markdown(Nil), "")

  test("markdown of links and leads says unconfirmed and lists the commands"):
    val l = EventLinkList(
      com.histmersion.api.catalogue.Event.Id("e"),
      EventLinkList.Label("E"),
      List(
        EventLinkCandidate(
          com.histmersion.api.catalogue.Event.Id("e"),
          com.histmersion.api.library.Citekey("a2020"),
          EventLinkCandidate.Score(1.1),
          reasons = List(EventLinkCandidate.Reasons("names it 1 time"))
        )
      )
    )
    assert(EventLinksMarkdown.list(l).contains("unconfirmed"))
    val md = EventLeads.markdown(
      EventLead.List(
        EventLead.List.Total(1),
        List(
          EventLead(
            com.histmersion.api.catalogue.Event.Id("e"),
            EventLead.Label("E"),
            EventLead.Kind("eruption"),
            EventLead.Tier("catalogue"),
            EventLead.Years("1 CE"),
            EventLead.Text("No source…"),
            EventLeads.actions(
              com.histmersion.api.catalogue.Event.Id("e"),
              EventLead.Label("E"),
              EventLead.Years("1 CE"),
              LeadFacts.Proposed(0)
            )
          )
        )
      )
    )
    assert(md.value.contains("`histmersion discover search \"E\" --not-in-db`"), md)

  // ── site types ──────────────────────────────────────────────────────────────────────────────

  test("Pleiades: -2 variants and several types — the most specific wins; non-sites keep the dot"):
    assertEquals(SiteTypes.pleiades(SiteTypes.Pleiades.FeatureType("settlement")), Some(SiteTypes.Settlement))
    assertEquals(SiteTypes.pleiades(SiteTypes.Pleiades.FeatureType("settlement,temple-2")), Some(SiteTypes.Sanctuary))
    assertEquals(SiteTypes.pleiades(SiteTypes.Pleiades.FeatureType("fort-2")), Some(SiteTypes.Fortification))
    assertEquals(
      SiteTypes.pleiades(SiteTypes.Pleiades.FeatureType("fortified-settlement, settlement")),
      Some(SiteTypes.Fortification)
    )
    assertEquals(SiteTypes.pleiades(SiteTypes.Pleiades.FeatureType("tumulus")), Some(SiteTypes.Burial))
    assertEquals(SiteTypes.pleiades(SiteTypes.Pleiades.FeatureType("mine-2,quarry")), Some(SiteTypes.Mine))
    assertEquals(
      SiteTypes.pleiades(SiteTypes.Pleiades.FeatureType("road,bridge")),
      Some(SiteTypes.Bridge),
      "a bridge is more specific than a road"
    )
    assertEquals(SiteTypes.pleiades(SiteTypes.Pleiades.FeatureType("road")), Some(SiteTypes.Infrastructure))
    assertEquals(SiteTypes.pleiades(SiteTypes.Pleiades.FeatureType("aqueduct-2")), Some(SiteTypes.Aqueduct))
    assertEquals(SiteTypes.pleiades(SiteTypes.Pleiades.FeatureType("amphitheatre")), Some(SiteTypes.Theatre))
    assertEquals(SiteTypes.pleiades(SiteTypes.Pleiades.FeatureType("archaeological-site")), Some(SiteTypes.Findspot))
    assertEquals(SiteTypes.pleiades(SiteTypes.Pleiades.FeatureType("province-2")), None)
    assertEquals(SiteTypes.pleiades(SiteTypes.Pleiades.FeatureType("")), None)

  test("GeoNames: populated places are settlements; obvious feature codes only"):
    assertEquals(SiteTypes.geonames(SiteTypes.Geonames.Fcode("P.PPLA2")), Some(SiteTypes.Settlement))
    assertEquals(SiteTypes.geonames(SiteTypes.Geonames.Fcode("s.ruin")), Some(SiteTypes.Findspot))
    assertEquals(SiteTypes.geonames(SiteTypes.Geonames.Fcode("S.TMB")), Some(SiteTypes.Burial))
    assertEquals(SiteTypes.geonames(SiteTypes.Geonames.Fcode("S.HTL")), None)
    assertEquals(SiteTypes.geonames(SiteTypes.Geonames.Fcode("S.BDG")), Some(SiteTypes.Bridge))
    assertEquals(SiteTypes.geonames(SiteTypes.Geonames.Fcode("S.AQDT")), Some(SiteTypes.Aqueduct))
    assertEquals(SiteTypes.geonames(SiteTypes.Geonames.Fcode("S.TOWR")), Some(SiteTypes.Tower))

  test("Wikidata: own classes first, then the walked superclasses, then the import's class"):
    assertEquals(
      SiteTypes.wikidata(
        List(CatalogRecord.Wikidata.Class("Q755017")),
        Nil,
        Some(CatalogRecord.Wikidata.ImportClass(SiteTypes.ArchaeologicalSite))
      ),
      Some(SiteTypes.Settlement)
    )
    assertEquals(
      SiteTypes.wikidata(
        List(CatalogRecord.Wikidata.Class("Q999999")),
        List(CatalogRecord.Wikidata.Class("Q34023")),
        Some(CatalogRecord.Wikidata.ImportClass(SiteTypes.ArchaeologicalSite))
      ),
      Some(SiteTypes.Burial)
    )
    assertEquals(
      SiteTypes.wikidata(Nil, Nil, Some(CatalogRecord.Wikidata.ImportClass(SiteTypes.HumanSettlement))),
      Some(SiteTypes.Settlement)
    )
    assertEquals(
      SiteTypes
        .wikidata(List(CatalogRecord.Wikidata.Class("Q44539"), CatalogRecord.Wikidata.Class("Q486972")), Nil, None),
      Some(SiteTypes.Sanctuary),
      "a temple in a settlement"
    )
    assertEquals(SiteTypes.wikidata(Nil, Nil, None), None)

  test("Wikidata heritage buildings (2026-10-28): castles, towers, churches, bridges and aqueducts"):
    assertEquals(
      SiteTypes.wikidataOwn(List(CatalogRecord.Wikidata.Class("Q57831"), CatalogRecord.Wikidata.Class("Q88291"))),
      Some(SiteTypes.Fortification),
      "Akronauplia: fortress, citadel"
    )
    assertEquals(
      SiteTypes.wikidataOwn(List(CatalogRecord.Wikidata.Class("Q23413"))),
      Some(SiteTypes.Fortification),
      "Larissa Castle"
    )
    assertEquals(
      SiteTypes.wikidataOwn(List(CatalogRecord.Wikidata.Class("Q44613"))),
      Some(SiteTypes.Sanctuary),
      "Talantio monastery"
    )
    assertEquals(
      SiteTypes.wikidataOwn(List(CatalogRecord.Wikidata.Class("Q16970"), CatalogRecord.Wikidata.Class("Q2031836"))),
      Some(SiteTypes.Sanctuary),
      "Bermpaka church"
    )
    assertEquals(SiteTypes.wikidataOwn(List(CatalogRecord.Wikidata.Class("Q12518"))), Some(SiteTypes.Tower))
    assertEquals(
      SiteTypes.wikidataOwn(List(CatalogRecord.Wikidata.Class("Q947103"))),
      Some(SiteTypes.Fortification),
      "a watchtower defends"
    )
    assertEquals(
      SiteTypes.wikidataOwn(List(CatalogRecord.Wikidata.Class("Q12518"), CatalogRecord.Wikidata.Class("Q839954"))),
      Some(SiteTypes.Tower),
      "a tower is more than a find-spot"
    )
    assertEquals(SiteTypes.wikidataOwn(List(CatalogRecord.Wikidata.Class("Q12280"))), Some(SiteTypes.Bridge))
    assertEquals(SiteTypes.wikidataOwn(List(CatalogRecord.Wikidata.Class("Q474"))), Some(SiteTypes.Aqueduct))
    assertEquals(SiteTypes.wikidataOwn(List(CatalogRecord.Wikidata.Class("Q1"))), None)
    assertEquals(
      SiteTypes.p31Of(Some(CatalogRecord.Wikidata.P31Json("""["Q16970","Q2031836"]"""))),
      List("Q16970", "Q2031836")
    )
    assertEquals(SiteTypes.p31Of(None), Nil)

  test("a place's type: the stored refinement, else its gazetteer's own field; only sites and features"):
    assertEquals(
      SiteTypes.of(
        Place.Id("wikidata_q1"),
        Place.Kind.Wire("site"),
        Some(SiteTypes.SiteType.Id("burial")),
        None,
        None,
        Some(CatalogRecord.Wikidata.Class(SiteTypes.HumanSettlement))
      ),
      Some("burial")
    )
    assertEquals(
      SiteTypes.of(
        Place.Id("wikidata_q1"),
        Place.Kind.Wire("site"),
        None,
        None,
        None,
        Some(CatalogRecord.Wikidata.Class(SiteTypes.HumanSettlement))
      ),
      Some("settlement")
    )
    assertEquals(
      SiteTypes.of(
        Place.Id("pleiades_1"),
        Place.Kind.Wire("site"),
        None,
        Some(SiteTypes.Pleiades.FeatureType("temple-2")),
        None,
        None
      ),
      Some("sanctuary")
    )
    assertEquals(
      SiteTypes
        .of(Place.Id("geonames_1"), Place.Kind.Wire("site"), None, None, Some(SiteTypes.Geonames.Fcode("P.PPL")), None),
      Some("settlement")
    )
    assertEquals(
      SiteTypes.of(
        Place.Id("pleiades_1"),
        Place.Kind.Wire("region"),
        None,
        Some(SiteTypes.Pleiades.FeatureType("settlement")),
        None,
        None
      ),
      None
    )
    assertEquals(
      SiteTypes.of(Place.Id("x"), Place.Kind.Wire("site"), Some(SiteTypes.SiteType.Id("not-a-type")), None, None, None),
      None
    )
    // a Wikidata item's own P31 before the class the import reached; a heritage-only item has no class
    assertEquals(
      SiteTypes.of(
        Place.Id("wikidata_q1"),
        Place.Kind.Wire("site"),
        None,
        None,
        None,
        Some(CatalogRecord.Wikidata.Class(SiteTypes.ArchaeologicalSite)),
        List(CatalogRecord.Wikidata.Class("Q23413"))
      ),
      Some("fortification")
    )
    assertEquals(
      SiteTypes.of(
        Place.Id("wikidata_q1"),
        Place.Kind.Wire("site"),
        None,
        None,
        None,
        None,
        List(CatalogRecord.Wikidata.Class("Q44613"))
      ),
      Some("sanctuary")
    )
    assertEquals(
      SiteTypes.of(
        Place.Id("wikidata_q1"),
        Place.Kind.Wire("site"),
        None,
        None,
        None,
        Some(CatalogRecord.Wikidata.Class(SiteTypes.HumanSettlement)),
        List(CatalogRecord.Wikidata.Class("Q1"))
      ),
      Some("settlement"),
      "an unknown P31: the import's class"
    )

  test("every type and kind has an icon name; the mapping tables only name known types"):
    assert(SiteTypes.all.forall(_.icon.value.nonEmpty) && EventTaxonomy.kinds.forall(_.icon.value.nonEmpty))
    val ids = SiteTypes.all.map(_.id).toSet
    assert(
      (SiteTypes.pleiadesTypes.values ++ SiteTypes.geonamesCodes.values ++ SiteTypes.wikidataClasses.values).forall(ids)
    )

  test("Wikidata claims: P31 values of each item, deprecated ones dropped"):
    val js = parse("""{"entities":{"Q1":{"claims":{"P31":[
      {"rank":"normal","mainsnak":{"datavalue":{"value":{"id":"Q755017"}}}},
      {"rank":"deprecated","mainsnak":{"datavalue":{"value":{"id":"Q515"}}}}]}},"Q2":{"claims":{}}}}""").toOption.get
    assertEquals(
      EventProviders.WikidataEvents.claims(js, EventProviders.WikidataProperty("P31")),
      Right(Map("Q1" -> List("Q755017"), "Q2" -> Nil))
    )
    val names = parse("""{"entities":{"Q1":{"labels":{"en":{"value":"Siege of X"},"de":{"value":"Belagerung von X"}},
      "aliases":{"en":[{"value":"X siege"}]}}}}""").toOption.get
    assertEquals(
      EventProviders.WikidataEvents.names(names).map(_("Q1").toSet),
      Right(Set("Siege of X", "Belagerung von X", "X siege"))
    )
