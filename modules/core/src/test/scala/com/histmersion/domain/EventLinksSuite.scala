package com.histmersion
package domain

import com.histmersion.api.catalogue.SiteType
import com.histmersion.util.json.RawJson

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.catalogue.AttestationTarget

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.api.TimeSpan
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.api.catalogue.{
  ArticleEventLead,
  EventLead,
  EventLinkCandidate,
  EventLinkList,
  EventLinksMarkdown,
  EventPassage,
  EventTaxonomy,
  EventTier,
  LeadFacts
}
import com.histmersion.domain.catalogue.Event
import com.histmersion.domain.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.EventEntry
import com.histmersion.api.catalogue.CatalogRecord
import com.histmersion.api.catalogue.Place
import com.histmersion.domain.catalogue.events.EventProviders
import com.histmersion.lint.{tolerated, LintRule}

/** #1449: the deterministic matcher (time/space overlap, name matches, no attestation without a passage), the leads, an
  * article's catalogue leads, and the site types from each gazetteer.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class EventLinksSuite extends munit.FunSuite:

  private val w = NearFinding.Weights(
    km = NearFinding.Weights.Km(150),
    years = NearFinding.Weights.Years(100),
    name = NearFinding.Weights.Name(1.0),
    finding = NearFinding.Weights.Finding(0.6),
    minScore = NearFinding.Weights.MinScore(0.3),
    minNameChars = NearFinding.Weights.MinNameChars(5),
    maxCandidates = NearFinding.Weights.MaxCandidates(12)
  )

  test("names worth searching: no bare kind words, no short names, longest first, distinct ignoring case"):
    assertEquals(
      NearFinding.searchNames(
        List("Battle", "Thera", "Minoan eruption", "minoan eruption", "Ur", " Siege "),
        NearFinding.Weights.MinNameChars(5)
      ),
      List("Minoan eruption", "Thera")
    )

  test("a finding weighs 1 at the event's place and time, 0 at the edge of the window"):
    assertEquals(
      NearFinding(
        NearFinding.Id("f"),
        Citekey.assume("a"),
        NearFinding.Km(0),
        NearFinding.YearGap(0)
      ).findingWeight(w),
      1.0
    )
    assertEquals(
      NearFinding(
        NearFinding.Id("f"),
        Citekey.assume("a"),
        NearFinding.Km(75),
        NearFinding.YearGap(0)
      ).findingWeight(w),
      0.5
    )
    assertEquals(
      NearFinding(
        NearFinding.Id("f"),
        Citekey.assume("a"),
        NearFinding.Km(75),
        NearFinding.YearGap(50)
      ).findingWeight(w),
      0.25
    )
    assertEquals(
      NearFinding(
        NearFinding.Id("f"),
        Citekey.assume("a"),
        NearFinding.Km(200),
        NearFinding.YearGap(0)
      ).findingWeight(w),
      0.0
    )

  test("candidates: a name in the text outweighs nearby findings; findings alone are capped; weak ones dropped"):
    val near = List(
      NearFinding(
        NearFinding.Id("a#1"),
        Citekey("near2020"),
        NearFinding.Km(10),
        NearFinding.YearGap(0)
      ),
      NearFinding(
        NearFinding.Id("a#2"),
        Citekey("near2020"),
        NearFinding.Km(20),
        NearFinding.YearGap(0)
      ),
      NearFinding(
        NearFinding.Id("b#1"),
        Citekey("far2020"),
        NearFinding.Km(140),
        NearFinding.YearGap(90)
      ),
      NearFinding(
        NearFinding.Id("c#1"),
        Citekey("named2020"),
        NearFinding.Km(100),
        NearFinding.YearGap(20)
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
    val cs = NearFinding.candidates(Event.Id("thera"), near, named, w)
    assertEquals(cs.map(_.citekey), List("named2020", "near2020").map(api.library.Citekey.apply))
    assertEquals(cs.head.status, Event.LinkStatus.Proposed)
    assert(cs.head.reasons.exists(_.value.contains("names it 2 times")), cs.head.reasons.toString)
    assert(cs(1).score.value <= w.finding.value, "findings alone never pass a name")
    assertEquals(cs(1).findings, List("a#1", "a#2"))

  test("no passage, no attestation: only a passage naming the event becomes one, with its quote and offsets"):
    assertEquals(NearFinding.attestationPassage(Nil), None)
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
    val p = NearFinding.attestationPassage(ps).get
    assertEquals(p.start, 80)
    val a = NearFinding.attestation(Event.Id("thera"), Citekey("x2020"), p)
    assertEquals(
      (a.targetType, a.targetId, a.quote, a.charStart, a.charEnd),
      (AttestationTarget.Event, "thera", Some("Minoan eruption"), Some(80), Some(95))
    )

  test("a catalogue event raises a lead in counts and dates; an attested one none"):
    val f = LeadFacts(
      api.catalogue.Event.Id("gvp_13879"),
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
    val l = EventLead.of(f).get
    assertEquals(
      l.text,
      "No source in the corpus discusses this VEI 7 eruption (1624 BCE–1596 BCE) — " +
        "7 findings in the corpus within 300 km and ±100 years"
    )
    assertEquals(l.actions.map(_.kind), List("discover", "acquire", "goal"))
    assertEquals(l.actions.head.command, "histmersion discover search \"Eruption of Santorini\" --not-in-db")
    val withProposed = EventLead.of(f.modify(_.proposed).setTo(LeadFacts.Proposed(2))).get
    assert(withProposed.text.value.contains("2 sources possibly discuss it (unconfirmed)"))
    assertEquals(withProposed.actions.last.command, "histmersion events links gvp_13879")
    assertEquals(EventLead.of(f.modify(_.tier).setTo(LeadFacts.Tier(EventTier.Attested))), None)
    assertEquals(EventLead.of(f.modify(_.attesting).setTo(LeadFacts.Attesting(1))), None)
    assertEquals(EventLead.size(None, Some(LeadFacts.Magnitude(7.25))), Some("M 7.3"))

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
          api.catalogue.Event.Id("gvp_1"),
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
      api.catalogue.Event.Id("e"),
      EventLinkList.Label("E"),
      List(
        EventLinkCandidate(
          api.catalogue.Event.Id("e"),
          api.library.Citekey("a2020"),
          EventLinkCandidate.Score(1.1),
          reasons = List(EventLinkCandidate.Reasons("names it 1 time"))
        )
      )
    )
    assert(EventLinksMarkdown.list(l).contains("unconfirmed"))
    val md = EventLead.markdown(
      EventLead.List(
        EventLead.List.Total(1),
        List(
          EventLead(
            api.catalogue.Event.Id("e"),
            EventLead.Label("E"),
            EventLead.Kind("eruption"),
            EventLead.Tier("catalogue"),
            EventLead.Years("1 CE"),
            EventLead.Text("No source…"),
            EventLead.actionsFor(
              api.catalogue.Event.Id("e"),
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
    assertEquals(SiteType.pleiades(SiteType.Pleiades.FeatureType("settlement")), Some(SiteType.Settlement))
    assertEquals(SiteType.pleiades(SiteType.Pleiades.FeatureType("settlement,temple-2")), Some(SiteType.Sanctuary))
    assertEquals(SiteType.pleiades(SiteType.Pleiades.FeatureType("fort-2")), Some(SiteType.Fortification))
    assertEquals(
      SiteType.pleiades(SiteType.Pleiades.FeatureType("fortified-settlement, settlement")),
      Some(SiteType.Fortification)
    )
    assertEquals(SiteType.pleiades(SiteType.Pleiades.FeatureType("tumulus")), Some(SiteType.Burial))
    assertEquals(SiteType.pleiades(SiteType.Pleiades.FeatureType("mine-2,quarry")), Some(SiteType.Mine))
    assertEquals(
      SiteType.pleiades(SiteType.Pleiades.FeatureType("road,bridge")),
      Some(SiteType.Bridge),
      "a bridge is more specific than a road"
    )
    assertEquals(SiteType.pleiades(SiteType.Pleiades.FeatureType("road")), Some(SiteType.Infrastructure))
    assertEquals(SiteType.pleiades(SiteType.Pleiades.FeatureType("aqueduct-2")), Some(SiteType.Aqueduct))
    assertEquals(SiteType.pleiades(SiteType.Pleiades.FeatureType("amphitheatre")), Some(SiteType.Theatre))
    assertEquals(SiteType.pleiades(SiteType.Pleiades.FeatureType("archaeological-site")), Some(SiteType.Findspot))
    assertEquals(SiteType.pleiades(SiteType.Pleiades.FeatureType("province-2")), None)
    assertEquals(SiteType.pleiades(SiteType.Pleiades.FeatureType("")), None)

  test("GeoNames: populated places are settlements; obvious feature codes only"):
    assertEquals(SiteType.geonames(SiteType.Geonames.Fcode("P.PPLA2")), Some(SiteType.Settlement))
    assertEquals(SiteType.geonames(SiteType.Geonames.Fcode("s.ruin")), Some(SiteType.Findspot))
    assertEquals(SiteType.geonames(SiteType.Geonames.Fcode("S.TMB")), Some(SiteType.Burial))
    assertEquals(SiteType.geonames(SiteType.Geonames.Fcode("S.HTL")), None)
    assertEquals(SiteType.geonames(SiteType.Geonames.Fcode("S.BDG")), Some(SiteType.Bridge))
    assertEquals(SiteType.geonames(SiteType.Geonames.Fcode("S.AQDT")), Some(SiteType.Aqueduct))
    assertEquals(SiteType.geonames(SiteType.Geonames.Fcode("S.TOWR")), Some(SiteType.Tower))

  test("Wikidata: own classes first, then the walked superclasses, then the import's class"):
    assertEquals(
      SiteType.wikidata(
        List(CatalogRecord.Wikidata.Class("Q755017")),
        Nil,
        Some(CatalogRecord.Wikidata.ImportClass(SiteType.ArchaeologicalSite))
      ),
      Some(SiteType.Settlement)
    )
    assertEquals(
      SiteType.wikidata(
        List(CatalogRecord.Wikidata.Class("Q999999")),
        List(CatalogRecord.Wikidata.Class("Q34023")),
        Some(CatalogRecord.Wikidata.ImportClass(SiteType.ArchaeologicalSite))
      ),
      Some(SiteType.Burial)
    )
    assertEquals(
      SiteType.wikidata(Nil, Nil, Some(CatalogRecord.Wikidata.ImportClass(SiteType.HumanSettlement))),
      Some(SiteType.Settlement)
    )
    assertEquals(
      SiteType
        .wikidata(List(CatalogRecord.Wikidata.Class("Q44539"), CatalogRecord.Wikidata.Class("Q486972")), Nil, None),
      Some(SiteType.Sanctuary),
      "a temple in a settlement"
    )
    assertEquals(SiteType.wikidata(Nil, Nil, None), None)

  test("Wikidata heritage buildings (2026-10-28): castles, towers, churches, bridges and aqueducts"):
    assertEquals(
      SiteType.wikidataOwn(List(CatalogRecord.Wikidata.Class("Q57831"), CatalogRecord.Wikidata.Class("Q88291"))),
      Some(SiteType.Fortification),
      "Akronauplia: fortress, citadel"
    )
    assertEquals(
      SiteType.wikidataOwn(List(CatalogRecord.Wikidata.Class("Q23413"))),
      Some(SiteType.Fortification),
      "Larissa Castle"
    )
    assertEquals(
      SiteType.wikidataOwn(List(CatalogRecord.Wikidata.Class("Q44613"))),
      Some(SiteType.Sanctuary),
      "Talantio monastery"
    )
    assertEquals(
      SiteType.wikidataOwn(List(CatalogRecord.Wikidata.Class("Q16970"), CatalogRecord.Wikidata.Class("Q2031836"))),
      Some(SiteType.Sanctuary),
      "Bermpaka church"
    )
    assertEquals(SiteType.wikidataOwn(List(CatalogRecord.Wikidata.Class("Q12518"))), Some(SiteType.Tower))
    assertEquals(
      SiteType.wikidataOwn(List(CatalogRecord.Wikidata.Class("Q947103"))),
      Some(SiteType.Fortification),
      "a watchtower defends"
    )
    assertEquals(
      SiteType.wikidataOwn(List(CatalogRecord.Wikidata.Class("Q12518"), CatalogRecord.Wikidata.Class("Q839954"))),
      Some(SiteType.Tower),
      "a tower is more than a find-spot"
    )
    assertEquals(SiteType.wikidataOwn(List(CatalogRecord.Wikidata.Class("Q12280"))), Some(SiteType.Bridge))
    assertEquals(SiteType.wikidataOwn(List(CatalogRecord.Wikidata.Class("Q474"))), Some(SiteType.Aqueduct))
    assertEquals(SiteType.wikidataOwn(List(CatalogRecord.Wikidata.Class("Q1"))), None)
    assertEquals(
      SiteType.p31Of(Some(CatalogRecord.Wikidata.P31Json("""["Q16970","Q2031836"]"""))),
      List("Q16970", "Q2031836")
    )
    assertEquals(SiteType.p31Of(None), Nil)

  test("a place's type: the stored refinement, else its gazetteer's own field; only sites and features"):
    assertEquals(
      SiteType.of(
        Place.Id("wikidata_q1"),
        Place.Kind.Wire("site"),
        Some(SiteType.Id("burial")),
        None,
        None,
        Some(CatalogRecord.Wikidata.Class(SiteType.HumanSettlement))
      ),
      Some("burial")
    )
    assertEquals(
      SiteType.of(
        Place.Id("wikidata_q1"),
        Place.Kind.Wire("site"),
        None,
        None,
        None,
        Some(CatalogRecord.Wikidata.Class(SiteType.HumanSettlement))
      ),
      Some("settlement")
    )
    assertEquals(
      SiteType.of(
        Place.Id("pleiades_1"),
        Place.Kind.Wire("site"),
        None,
        Some(SiteType.Pleiades.FeatureType("temple-2")),
        None,
        None
      ),
      Some("sanctuary")
    )
    assertEquals(
      SiteType
        .of(Place.Id("geonames_1"), Place.Kind.Wire("site"), None, None, Some(SiteType.Geonames.Fcode("P.PPL")), None),
      Some("settlement")
    )
    assertEquals(
      SiteType.of(
        Place.Id("pleiades_1"),
        Place.Kind.Wire("region"),
        None,
        Some(SiteType.Pleiades.FeatureType("settlement")),
        None,
        None
      ),
      None
    )
    assertEquals(
      SiteType.of(Place.Id("x"), Place.Kind.Wire("site"), Some(SiteType.Id("not-a-type")), None, None, None),
      None
    )
    // a Wikidata item's own P31 before the class the import reached; a heritage-only item has no class
    assertEquals(
      SiteType.of(
        Place.Id("wikidata_q1"),
        Place.Kind.Wire("site"),
        None,
        None,
        None,
        Some(CatalogRecord.Wikidata.Class(SiteType.ArchaeologicalSite)),
        List(CatalogRecord.Wikidata.Class("Q23413"))
      ),
      Some("fortification")
    )
    assertEquals(
      SiteType.of(
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
      SiteType.of(
        Place.Id("wikidata_q1"),
        Place.Kind.Wire("site"),
        None,
        None,
        None,
        Some(CatalogRecord.Wikidata.Class(SiteType.HumanSettlement)),
        List(CatalogRecord.Wikidata.Class("Q1"))
      ),
      Some("settlement"),
      "an unknown P31: the import's class"
    )

  test("every type and kind has an icon name; the mapping tables only name known types"):
    assert(SiteType.all.forall(_.icon.value.nonEmpty) && EventTaxonomy.kinds.forall(_.icon.value.nonEmpty))
    val ids = SiteType.all.map(_.id).toSet
    assert(
      (SiteType.pleiadesTypes.values ++ SiteType.geonamesCodes.values ++ SiteType.wikidataClasses.values).forall(ids)
    )

  test("Wikidata claims: P31 values of each item, deprecated ones dropped"):
    val js = RawJson
      .parse("""{"entities":{"Q1":{"claims":{"P31":[
      {"rank":"normal","mainsnak":{"datavalue":{"value":{"id":"Q755017"}}}},
      {"rank":"deprecated","mainsnak":{"datavalue":{"value":{"id":"Q515"}}}}]}},"Q2":{"claims":{}}}}""")
      .toOption
      .get
    assertEquals(
      EventProviders.WikidataEvents.claims(js, EventProviders.WikidataProperty("P31")),
      Some(Map("Q1" -> List("Q755017"), "Q2" -> Nil))
    )
    val names = RawJson
      .parse("""{"entities":{"Q1":{"labels":{"en":{"value":"Siege of X"},"de":{"value":"Belagerung von X"}},
      "aliases":{"en":[{"value":"X siege"}]}}}}""")
      .toOption
      .get
    assertEquals(
      EventProviders.WikidataEvents.names(names).map(_("Q1").toSet),
      Some(Set("Siege of X", "Belagerung von X", "X siege"))
    )
