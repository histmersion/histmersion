package com.histmersion.domain

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

/** #1449: the deterministic matcher (time/space overlap, name matches, no attestation without a passage), the leads, an
  * article's catalogue leads, and the site types from each gazetteer.
  */
class EventLinksSuite extends munit.FunSuite:

  private val w = EventMatcher.Weights(
    km = com.histmersion.domain.catalogue.evidence.EventMatcher.Weights.Km(150),
    years = com.histmersion.domain.catalogue.evidence.EventMatcher.Weights.Years(100),
    name = com.histmersion.domain.catalogue.evidence.EventMatcher.Weights.Name(1.0),
    finding = com.histmersion.domain.catalogue.evidence.EventMatcher.Weights.Finding(0.6),
    minScore = com.histmersion.domain.catalogue.evidence.EventMatcher.Weights.MinScore(0.3),
    minNameChars = com.histmersion.domain.catalogue.evidence.EventMatcher.Weights.MinNameChars(5),
    maxCandidates = com.histmersion.domain.catalogue.evidence.EventMatcher.Weights.MaxCandidates(12)
  )

  test("names worth searching: no bare kind words, no short names, longest first, distinct ignoring case"):
    assertEquals(
      EventMatcher.searchNames(List("Battle", "Thera", "Minoan eruption", "minoan eruption", "Ur", " Siege "), com.histmersion.domain.catalogue.evidence.EventMatcher.Weights.MinNameChars(5)),
      List("Minoan eruption", "Thera")
    )

  test("a finding weighs 1 at the event's place and time, 0 at the edge of the window"):
    assertEquals(EventMatcher.findingWeight(EventMatcher.NearFinding(com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.Id("f"), com.histmersion.domain.library.Citekey("a"), com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.Km(0), com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.YearGap(0)), w), 1.0)
    assertEquals(EventMatcher.findingWeight(EventMatcher.NearFinding(com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.Id("f"), com.histmersion.domain.library.Citekey("a"), com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.Km(75), com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.YearGap(0)), w), 0.5)
    assertEquals(EventMatcher.findingWeight(EventMatcher.NearFinding(com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.Id("f"), com.histmersion.domain.library.Citekey("a"), com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.Km(75), com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.YearGap(50)), w), 0.25)
    assertEquals(EventMatcher.findingWeight(EventMatcher.NearFinding(com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.Id("f"), com.histmersion.domain.library.Citekey("a"), com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.Km(200), com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.YearGap(0)), w), 0.0)

  test("candidates: a name in the text outweighs nearby findings; findings alone are capped; weak ones dropped"):
    val near = List(
      EventMatcher.NearFinding(com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.Id("a#1"), com.histmersion.domain.library.Citekey("near2020"), com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.Km(10), com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.YearGap(0)),
      EventMatcher.NearFinding(com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.Id("a#2"), com.histmersion.domain.library.Citekey("near2020"), com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.Km(20), com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.YearGap(0)),
      EventMatcher.NearFinding(com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.Id("b#1"), com.histmersion.domain.library.Citekey("far2020"), com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.Km(140), com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.YearGap(90)),
      EventMatcher.NearFinding(com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.Id("c#1"), com.histmersion.domain.library.Citekey("named2020"), com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.Km(100), com.histmersion.domain.catalogue.evidence.EventMatcher.NearFinding.YearGap(20))
    )
    val named =
      Map(com.histmersion.domain.library.Citekey("named2020") -> List(EventPassage(com.histmersion.api.catalogue.EventPassage.Start(120), com.histmersion.api.catalogue.EventPassage.End(125), com.histmersion.api.catalogue.EventPassage.Quote("Thera"), com.histmersion.api.catalogue.EventPassage.Name("Thera")), EventPassage(com.histmersion.api.catalogue.EventPassage.Start(900), com.histmersion.api.catalogue.EventPassage.End(905), com.histmersion.api.catalogue.EventPassage.Quote("Thera"), com.histmersion.api.catalogue.EventPassage.Name("Thera"))))
    val cs = EventMatcher.candidates(com.histmersion.domain.catalogue.Event.Id("thera"), near, named, w)
    assertEquals(cs.map(_.citekey), List("named2020", "near2020").map(com.histmersion.api.library.Citekey.apply))
    assertEquals(cs.head.status, Event.LinkStatus.Proposed)
    assert(cs.head.reasons.exists(_.value.contains("names it 2 times")), cs.head.reasons.toString)
    assert(cs(1).score.value <= w.finding.value, "findings alone never pass a name")
    assertEquals(cs(1).findings, List("a#1", "a#2"))

  test("no passage, no attestation: only a passage naming the event becomes one, with its quote and offsets"):
    assertEquals(EventMatcher.attestationPassage(Nil), None)
    val ps = List(EventPassage(com.histmersion.api.catalogue.EventPassage.Start(500), com.histmersion.api.catalogue.EventPassage.End(505), com.histmersion.api.catalogue.EventPassage.Quote("Thera"), com.histmersion.api.catalogue.EventPassage.Name("Thera")), EventPassage(com.histmersion.api.catalogue.EventPassage.Start(80), com.histmersion.api.catalogue.EventPassage.End(95), com.histmersion.api.catalogue.EventPassage.Quote("Minoan eruption"), com.histmersion.api.catalogue.EventPassage.Name("Minoan eruption")))
    val p = EventMatcher.attestationPassage(ps).get
    assertEquals(p.start, 80)
    val a = EventMatcher.attestation(com.histmersion.domain.catalogue.Event.Id("thera"), com.histmersion.domain.library.Citekey("x2020"), p)
    assertEquals(
      (a.targetType, a.targetId, a.quote, a.charStart, a.charEnd),
      (AttestationTarget.Event, "thera", Some("Minoan eruption"), Some(80), Some(95))
    )

  test("a catalogue event raises a lead in counts and dates; an attested one none"):
    val f = LeadFacts(
      com.histmersion.api.catalogue.Event.Id("gvp_13879"),
      com.histmersion.api.catalogue.LeadFacts.Label("Eruption of Santorini"),
      com.histmersion.api.catalogue.LeadFacts.Kind("eruption"),
      TimeSpan(Some(com.histmersion.api.catalogue.Year(-1624)), None, None, Some(com.histmersion.api.catalogue.Year(-1596))),
      com.histmersion.api.catalogue.LeadFacts.Tier(EventTier.Catalogue),
      Some(com.histmersion.api.catalogue.LeadFacts.Vei(7)),
      None,
      findingsNear = com.histmersion.api.catalogue.LeadFacts.FindingsNear(7),
      km = com.histmersion.api.catalogue.LeadFacts.Km(300),
      years = com.histmersion.api.catalogue.LeadFacts.Years(100),
      proposed = com.histmersion.api.catalogue.LeadFacts.Proposed(0),
      attesting = com.histmersion.api.catalogue.LeadFacts.Attesting(0)
    )
    val l = EventLeads.of(f).get
    assertEquals(
      l.text,
      "No source in the corpus discusses this VEI 7 eruption (1624 BCE–1596 BCE) — " +
        "7 findings in the corpus within 300 km and ±100 years"
    )
    assertEquals(l.actions.map(_.kind), List("discover", "acquire", "goal"))
    assertEquals(l.actions.head.command, "histmersion discover search \"Eruption of Santorini\" --not-in-db")
    val withProposed = EventLeads.of(f.modify(_.proposed).setTo(2)).get
    assert(withProposed.text.value.contains("2 sources possibly discuss it (unconfirmed)"))
    assertEquals(withProposed.actions.last.command, "histmersion events links gvp_13879")
    assertEquals(EventLeads.of(f.modify(_.tier).setTo(com.histmersion.api.catalogue.LeadFacts.Tier(EventTier.Attested))), None)
    assertEquals(EventLeads.of(f.modify(_.attesting).setTo(1)), None)
    assertEquals(EventLeads.size(None, Some(com.histmersion.api.catalogue.LeadFacts.Magnitude(7.25))), Some("M 7.3"))

  test("an article names an event by any of its names, ignoring case; short names do not count"):
    assert(
      ArticleEventLead.mentioned(com.histmersion.api.catalogue.ArticleEventLead.Body("The Minoan eruption of Thera…"), List(com.histmersion.api.catalogue.EventEntry.Names("Eruption of Santorini"), com.histmersion.api.catalogue.EventEntry.Names("Minoan eruption")))
    )
    assert(!ArticleEventLead.mentioned(com.histmersion.api.catalogue.ArticleEventLead.Body("Thera is mentioned"), List(com.histmersion.api.catalogue.EventEntry.Names("Eruption of Santorini"), com.histmersion.api.catalogue.EventEntry.Names("Ur"))))
    val md = ArticleEventLead.markdown(
      List(ArticleEventLead(com.histmersion.api.catalogue.Event.Id("gvp_1"), com.histmersion.api.catalogue.ArticleEventLead.Label("Eruption of X"), com.histmersion.api.catalogue.ArticleEventLead.Kind("eruption"), com.histmersion.api.catalogue.ArticleEventLead.Years("1600 BCE"), Some(com.histmersion.api.catalogue.ArticleEventLead.Size("VEI 6")), com.histmersion.api.catalogue.ArticleEventLead.FindingsNear(3), com.histmersion.api.catalogue.ArticleEventLead.Provider("GVP")))
    )
    assert(md.value.contains("never names") && md.value.contains("a lead, not evidence"), md)
    assertEquals(ArticleEventLead.markdown(Nil), "")

  test("markdown of links and leads says unconfirmed and lists the commands"):
    val l = EventLinkList(com.histmersion.api.catalogue.Event.Id("e"), com.histmersion.api.catalogue.EventLinkList.Label("E"), List(EventLinkCandidate(com.histmersion.api.catalogue.Event.Id("e"), com.histmersion.api.library.Citekey("a2020"), EventLinkCandidate.Score(1.1), reasons = List(EventLinkCandidate.Reasons("names it 1 time")))))
    assert(EventLinksMarkdown.list(l).contains("unconfirmed"))
    val md = EventLeads.markdown(
      EventLead.List(
        com.histmersion.api.catalogue.EventLead.List.Total(1),
        List(
          EventLead(com.histmersion.api.catalogue.Event.Id("e"), com.histmersion.api.catalogue.EventLead.Label("E"), com.histmersion.api.catalogue.EventLead.Kind("eruption"), com.histmersion.api.catalogue.EventLead.Tier("catalogue"), com.histmersion.api.catalogue.EventLead.Years("1 CE"), com.histmersion.api.catalogue.EventLead.Text("No source…"), EventLeads.actions(com.histmersion.api.catalogue.Event.Id("e"), com.histmersion.api.catalogue.EventLead.Label("E"), com.histmersion.api.catalogue.EventLead.Years("1 CE"), com.histmersion.api.catalogue.LeadFacts.Proposed(0)))
        )
      )
    )
    assert(md.value.contains("`histmersion discover search \"E\" --not-in-db`"), md)

  // ── site types ──────────────────────────────────────────────────────────────────────────────

  test("Pleiades: -2 variants and several types — the most specific wins; non-sites keep the dot"):
    assertEquals(SiteTypes.pleiades(com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType("settlement")), Some(SiteTypes.Settlement))
    assertEquals(SiteTypes.pleiades(com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType("settlement,temple-2")), Some(SiteTypes.Sanctuary))
    assertEquals(SiteTypes.pleiades(com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType("fort-2")), Some(SiteTypes.Fortification))
    assertEquals(SiteTypes.pleiades(com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType("fortified-settlement, settlement")), Some(SiteTypes.Fortification))
    assertEquals(SiteTypes.pleiades(com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType("tumulus")), Some(SiteTypes.Burial))
    assertEquals(SiteTypes.pleiades(com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType("mine-2,quarry")), Some(SiteTypes.Mine))
    assertEquals(SiteTypes.pleiades(com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType("road,bridge")), Some(SiteTypes.Bridge), "a bridge is more specific than a road")
    assertEquals(SiteTypes.pleiades(com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType("road")), Some(SiteTypes.Infrastructure))
    assertEquals(SiteTypes.pleiades(com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType("aqueduct-2")), Some(SiteTypes.Aqueduct))
    assertEquals(SiteTypes.pleiades(com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType("amphitheatre")), Some(SiteTypes.Theatre))
    assertEquals(SiteTypes.pleiades(com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType("archaeological-site")), Some(SiteTypes.Findspot))
    assertEquals(SiteTypes.pleiades(com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType("province-2")), None)
    assertEquals(SiteTypes.pleiades(com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType("")), None)

  test("GeoNames: populated places are settlements; obvious feature codes only"):
    assertEquals(SiteTypes.geonames(com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode("P.PPLA2")), Some(SiteTypes.Settlement))
    assertEquals(SiteTypes.geonames(com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode("s.ruin")), Some(SiteTypes.Findspot))
    assertEquals(SiteTypes.geonames(com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode("S.TMB")), Some(SiteTypes.Burial))
    assertEquals(SiteTypes.geonames(com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode("S.HTL")), None)
    assertEquals(SiteTypes.geonames(com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode("S.BDG")), Some(SiteTypes.Bridge))
    assertEquals(SiteTypes.geonames(com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode("S.AQDT")), Some(SiteTypes.Aqueduct))
    assertEquals(SiteTypes.geonames(com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode("S.TOWR")), Some(SiteTypes.Tower))

  test("Wikidata: own classes first, then the walked superclasses, then the import's class"):
    assertEquals(
      SiteTypes.wikidata(List(com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class("Q755017")), Nil, Some(com.histmersion.api.catalogue.CatalogRecord.Wikidata.ImportClass(SiteTypes.ArchaeologicalSite))),
      Some(SiteTypes.Settlement)
    )
    assertEquals(
      SiteTypes.wikidata(List(com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class("Q999999")), List(com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class("Q34023")), Some(com.histmersion.api.catalogue.CatalogRecord.Wikidata.ImportClass(SiteTypes.ArchaeologicalSite))),
      Some(SiteTypes.Burial)
    )
    assertEquals(SiteTypes.wikidata(Nil, Nil, Some(com.histmersion.api.catalogue.CatalogRecord.Wikidata.ImportClass(SiteTypes.HumanSettlement))), Some(SiteTypes.Settlement))
    assertEquals(
      SiteTypes.wikidata(List(com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class("Q44539"), com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class("Q486972")), Nil, None),
      Some(SiteTypes.Sanctuary),
      "a temple in a settlement"
    )
    assertEquals(SiteTypes.wikidata(Nil, Nil, None), None)

  test("Wikidata heritage buildings (2026-10-28): castles, towers, churches, bridges and aqueducts"):
    assertEquals(
      SiteTypes.wikidataOwn(List(com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class("Q57831"), com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class("Q88291"))),
      Some(SiteTypes.Fortification),
      "Akronauplia: fortress, citadel"
    )
    assertEquals(SiteTypes.wikidataOwn(List(com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class("Q23413"))), Some(SiteTypes.Fortification), "Larissa Castle")
    assertEquals(SiteTypes.wikidataOwn(List(com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class("Q44613"))), Some(SiteTypes.Sanctuary), "Talantio monastery")
    assertEquals(SiteTypes.wikidataOwn(List(com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class("Q16970"), com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class("Q2031836"))), Some(SiteTypes.Sanctuary), "Bermpaka church")
    assertEquals(SiteTypes.wikidataOwn(List(com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class("Q12518"))), Some(SiteTypes.Tower))
    assertEquals(SiteTypes.wikidataOwn(List(com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class("Q947103"))), Some(SiteTypes.Fortification), "a watchtower defends")
    assertEquals(
      SiteTypes.wikidataOwn(List(com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class("Q12518"), com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class("Q839954"))),
      Some(SiteTypes.Tower),
      "a tower is more than a find-spot"
    )
    assertEquals(SiteTypes.wikidataOwn(List(com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class("Q12280"))), Some(SiteTypes.Bridge))
    assertEquals(SiteTypes.wikidataOwn(List(com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class("Q474"))), Some(SiteTypes.Aqueduct))
    assertEquals(SiteTypes.wikidataOwn(List(com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class("Q1"))), None)
    assertEquals(SiteTypes.p31Of(Some(com.histmersion.api.catalogue.CatalogRecord.Wikidata.P31Json("""["Q16970","Q2031836"]"""))), List("Q16970", "Q2031836"))
    assertEquals(SiteTypes.p31Of(None), Nil)

  test("a place's type: the stored refinement, else its gazetteer's own field; only sites and features"):
    assertEquals(
      SiteTypes.of(com.histmersion.api.catalogue.Place.Id("wikidata_q1"), com.histmersion.api.catalogue.Place.Kind.Wire("site"), Some(com.histmersion.api.catalogue.SiteTypes.SiteType.Id("burial")), None, None, Some(com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(SiteTypes.HumanSettlement))),
      Some("burial")
    )
    assertEquals(
      SiteTypes.of(com.histmersion.api.catalogue.Place.Id("wikidata_q1"), com.histmersion.api.catalogue.Place.Kind.Wire("site"), None, None, None, Some(com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(SiteTypes.HumanSettlement))),
      Some("settlement")
    )
    assertEquals(SiteTypes.of(com.histmersion.api.catalogue.Place.Id("pleiades_1"), com.histmersion.api.catalogue.Place.Kind.Wire("site"), None, Some(com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType("temple-2")), None, None), Some("sanctuary"))
    assertEquals(SiteTypes.of(com.histmersion.api.catalogue.Place.Id("geonames_1"), com.histmersion.api.catalogue.Place.Kind.Wire("site"), None, None, Some(com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode("P.PPL")), None), Some("settlement"))
    assertEquals(SiteTypes.of(com.histmersion.api.catalogue.Place.Id("pleiades_1"), com.histmersion.api.catalogue.Place.Kind.Wire("region"), None, Some(com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType("settlement")), None, None), None)
    assertEquals(SiteTypes.of(com.histmersion.api.catalogue.Place.Id("x"), com.histmersion.api.catalogue.Place.Kind.Wire("site"), Some(com.histmersion.api.catalogue.SiteTypes.SiteType.Id("not-a-type")), None, None, None), None)
    // a Wikidata item's own P31 before the class the import reached; a heritage-only item has no class
    assertEquals(
      SiteTypes.of(com.histmersion.api.catalogue.Place.Id("wikidata_q1"), com.histmersion.api.catalogue.Place.Kind.Wire("site"), None, None, None, Some(com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(SiteTypes.ArchaeologicalSite)), List(com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class("Q23413"))),
      Some("fortification")
    )
    assertEquals(SiteTypes.of(com.histmersion.api.catalogue.Place.Id("wikidata_q1"), com.histmersion.api.catalogue.Place.Kind.Wire("site"), None, None, None, None, List(com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class("Q44613"))), Some("sanctuary"))
    assertEquals(
      SiteTypes.of(com.histmersion.api.catalogue.Place.Id("wikidata_q1"), com.histmersion.api.catalogue.Place.Kind.Wire("site"), None, None, None, Some(com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(SiteTypes.HumanSettlement)), List(com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class("Q1"))),
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
    assertEquals(EventProviders.WikidataEvents.claims(js, com.histmersion.domain.catalogue.events.EventProviders.WikidataProperty("P31")), Right(Map("Q1" -> List("Q755017"), "Q2" -> Nil)))
    val names = parse("""{"entities":{"Q1":{"labels":{"en":{"value":"Siege of X"},"de":{"value":"Belagerung von X"}},
      "aliases":{"en":[{"value":"X siege"}]}}}}""").toOption.get
    assertEquals(
      EventProviders.WikidataEvents.names(names).map(_("Q1").toSet),
      Right(Set("Siege of X", "Belagerung von X", "X siege"))
    )
