package com.histmersion.domain

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given


import io.circe.parser.parse
import com.histmersion.values.EventLinkStatus

/** #1449: the deterministic matcher (time/space overlap, name matches, no attestation without a
  * passage), the leads, an article's catalogue leads, and the site types from each gazetteer. */
class EventLinksSuite extends munit.FunSuite:

  private val w = EventMatcher.Weights(km = 150, years = 100, name = 1.0, finding = 0.6, minScore = 0.3,
    minNameChars = 5, maxCandidates = 12)

  test("names worth searching: no bare kind words, no short names, longest first, distinct ignoring case"):
    assertEquals(EventMatcher.searchNames(List("Battle", "Thera", "Minoan eruption", "minoan eruption", "Ur", " Siege "), 5),
      List("Minoan eruption", "Thera"))

  test("a finding weighs 1 at the event's place and time, 0 at the edge of the window"):
    assertEquals(EventMatcher.findingWeight(EventMatcher.NearFinding("f", "a", 0, 0), w), 1.0)
    assertEquals(EventMatcher.findingWeight(EventMatcher.NearFinding("f", "a", 75, 0), w), 0.5)
    assertEquals(EventMatcher.findingWeight(EventMatcher.NearFinding("f", "a", 75, 50), w), 0.25)
    assertEquals(EventMatcher.findingWeight(EventMatcher.NearFinding("f", "a", 200, 0), w), 0.0)

  test("candidates: a name in the text outweighs nearby findings; findings alone are capped; weak ones dropped"):
    val near = List(
      EventMatcher.NearFinding("a#1", "near2020", 10, 0), EventMatcher.NearFinding("a#2", "near2020", 20, 0),
      EventMatcher.NearFinding("b#1", "far2020", 140, 90),
      EventMatcher.NearFinding("c#1", "named2020", 100, 20))
    val named = Map("named2020" -> List(EventPassage(120, 125, "Thera", "Thera"), EventPassage(900, 905, "Thera", "Thera")))
    val cs = EventMatcher.candidates("thera", near, named, w)
    assertEquals(cs.map(_.citekey), List("named2020", "near2020"))
    assertEquals(cs.head.status, EventLinkStatus.Proposed)
    assert(cs.head.reasons.exists(_.contains("names it 2 times")), cs.head.reasons.toString)
    assert(cs(1).score <= w.finding, "findings alone never pass a name")
    assertEquals(cs(1).findings, List("a#1", "a#2"))

  test("no passage, no attestation: only a passage naming the event becomes one, with its quote and offsets"):
    assertEquals(EventMatcher.attestationPassage(Nil), None)
    val ps = List(EventPassage(500, 505, "Thera", "Thera"), EventPassage(80, 95, "Minoan eruption", "Minoan eruption"))
    val p = EventMatcher.attestationPassage(ps).get
    assertEquals(p.start, 80)
    val a = EventMatcher.attestation("thera", "x2020", p)
    assertEquals((a.targetType, a.targetId, a.quote, a.charStart, a.charEnd),
      (AttestationTarget.Event, "thera", Some("Minoan eruption"), Some(80), Some(95)))

  test("a catalogue event raises a lead in counts and dates; an attested one none"):
    val f = LeadFacts("gvp_13879", "Eruption of Santorini", "eruption", TimeSpan(Some(-1624), None, None, Some(-1596)),
      EventTier.Catalogue, Some(7), None, findingsNear = 7, km = 300, years = 100, proposed = 0, attesting = 0)
    val l = EventLeads.of(f).get
    assertEquals(l.text, "No source in the corpus discusses this VEI 7 eruption (1624 BCE–1596 BCE) — " +
      "7 findings in the corpus within 300 km and ±100 years")
    assertEquals(l.actions.map(_.kind), List("discover", "acquire", "goal"))
    assertEquals(l.actions.head.command, "histmersion discover search \"Eruption of Santorini\" --not-in-db")
    val withProposed = EventLeads.of(f.copy(proposed = 2)).get
    assert(withProposed.text.contains("2 sources possibly discuss it (unconfirmed)"))
    assertEquals(withProposed.actions.last.command, "histmersion events links gvp_13879")
    assertEquals(EventLeads.of(f.copy(tier = EventTier.Attested)), None)
    assertEquals(EventLeads.of(f.copy(attesting = 1)), None)
    assertEquals(EventLeads.size(None, Some(7.25)), Some("M 7.3"))

  test("an article names an event by any of its names, ignoring case; short names do not count"):
    assert(ArticleEventLead.mentioned("The Minoan eruption of Thera…", List("Eruption of Santorini", "Minoan eruption")))
    assert(!ArticleEventLead.mentioned("Thera is mentioned", List("Eruption of Santorini", "Ur")))
    val md = ArticleEventLead.markdown(List(ArticleEventLead("gvp_1", "Eruption of X", "eruption", "1600 BCE", Some("VEI 6"), 3, "GVP")))
    assert(md.contains("never names") && md.contains("a lead, not evidence"), md)
    assertEquals(ArticleEventLead.markdown(Nil), "")

  test("markdown of links and leads says unconfirmed and lists the commands"):
    val l = EventLinkList("e", "E", List(EventLinkCandidate("e", "a2020", 1.1, reasons = List("names it 1 time"))))
    assert(EventLinksMarkdown.list(l).contains("unconfirmed"))
    val md = EventLeads.markdown(EventLeadList(1, List(EventLead("e", "E", "eruption", "catalogue", "1 CE", "No source…",
      EventLeads.actions("e", "E", "1 CE", 0)))))
    assert(md.contains("`histmersion discover search \"E\" --not-in-db`"), md)

  // ── site types ──────────────────────────────────────────────────────────────────────────────

  test("Pleiades: -2 variants and several types — the most specific wins; non-sites keep the dot"):
    assertEquals(SiteTypes.pleiades("settlement"), Some(SiteTypes.Settlement))
    assertEquals(SiteTypes.pleiades("settlement,temple-2"), Some(SiteTypes.Sanctuary))
    assertEquals(SiteTypes.pleiades("fort-2"), Some(SiteTypes.Fortification))
    assertEquals(SiteTypes.pleiades("fortified-settlement, settlement"), Some(SiteTypes.Fortification))
    assertEquals(SiteTypes.pleiades("tumulus"), Some(SiteTypes.Burial))
    assertEquals(SiteTypes.pleiades("mine-2,quarry"), Some(SiteTypes.Mine))
    assertEquals(SiteTypes.pleiades("road,bridge"), Some(SiteTypes.Bridge), "a bridge is more specific than a road")
    assertEquals(SiteTypes.pleiades("road"), Some(SiteTypes.Infrastructure))
    assertEquals(SiteTypes.pleiades("aqueduct-2"), Some(SiteTypes.Aqueduct))
    assertEquals(SiteTypes.pleiades("amphitheatre"), Some(SiteTypes.Theatre))
    assertEquals(SiteTypes.pleiades("archaeological-site"), Some(SiteTypes.Findspot))
    assertEquals(SiteTypes.pleiades("province-2"), None)
    assertEquals(SiteTypes.pleiades(""), None)

  test("GeoNames: populated places are settlements; obvious feature codes only"):
    assertEquals(SiteTypes.geonames("P.PPLA2"), Some(SiteTypes.Settlement))
    assertEquals(SiteTypes.geonames("s.ruin"), Some(SiteTypes.Findspot))
    assertEquals(SiteTypes.geonames("S.TMB"), Some(SiteTypes.Burial))
    assertEquals(SiteTypes.geonames("S.HTL"), None)
    assertEquals(SiteTypes.geonames("S.BDG"), Some(SiteTypes.Bridge))
    assertEquals(SiteTypes.geonames("S.AQDT"), Some(SiteTypes.Aqueduct))
    assertEquals(SiteTypes.geonames("S.TOWR"), Some(SiteTypes.Tower))

  test("Wikidata: own classes first, then the walked superclasses, then the import's class"):
    assertEquals(SiteTypes.wikidata(List("Q755017"), Nil, Some(SiteTypes.ArchaeologicalSite)), Some(SiteTypes.Settlement))
    assertEquals(SiteTypes.wikidata(List("Q999999"), List("Q34023"), Some(SiteTypes.ArchaeologicalSite)), Some(SiteTypes.Burial))
    assertEquals(SiteTypes.wikidata(Nil, Nil, Some(SiteTypes.HumanSettlement)), Some(SiteTypes.Settlement))
    assertEquals(SiteTypes.wikidata(List("Q44539", "Q486972"), Nil, None), Some(SiteTypes.Sanctuary), "a temple in a settlement")
    assertEquals(SiteTypes.wikidata(Nil, Nil, None), None)

  test("Wikidata heritage buildings (2026-10-28): castles, towers, churches, bridges and aqueducts"):
    assertEquals(SiteTypes.wikidataOwn(List("Q57831", "Q88291")), Some(SiteTypes.Fortification), "Akronauplia: fortress, citadel")
    assertEquals(SiteTypes.wikidataOwn(List("Q23413")), Some(SiteTypes.Fortification), "Larissa Castle")
    assertEquals(SiteTypes.wikidataOwn(List("Q44613")), Some(SiteTypes.Sanctuary), "Talantio monastery")
    assertEquals(SiteTypes.wikidataOwn(List("Q16970", "Q2031836")), Some(SiteTypes.Sanctuary), "Bermpaka church")
    assertEquals(SiteTypes.wikidataOwn(List("Q12518")), Some(SiteTypes.Tower))
    assertEquals(SiteTypes.wikidataOwn(List("Q947103")), Some(SiteTypes.Fortification), "a watchtower defends")
    assertEquals(SiteTypes.wikidataOwn(List("Q12518", "Q839954")), Some(SiteTypes.Tower), "a tower is more than a find-spot")
    assertEquals(SiteTypes.wikidataOwn(List("Q12280")), Some(SiteTypes.Bridge))
    assertEquals(SiteTypes.wikidataOwn(List("Q474")), Some(SiteTypes.Aqueduct))
    assertEquals(SiteTypes.wikidataOwn(List("Q1")), None)
    assertEquals(SiteTypes.p31Of(Some("""["Q16970","Q2031836"]""")), List("Q16970", "Q2031836"))
    assertEquals(SiteTypes.p31Of(None), Nil)

  test("a place's type: the stored refinement, else its gazetteer's own field; only sites and features"):
    assertEquals(SiteTypes.of("wikidata_q1", "site", Some("burial"), None, None, Some(SiteTypes.HumanSettlement)), Some("burial"))
    assertEquals(SiteTypes.of("wikidata_q1", "site", None, None, None, Some(SiteTypes.HumanSettlement)), Some("settlement"))
    assertEquals(SiteTypes.of("pleiades_1", "site", None, Some("temple-2"), None, None), Some("sanctuary"))
    assertEquals(SiteTypes.of("geonames_1", "site", None, None, Some("P.PPL"), None), Some("settlement"))
    assertEquals(SiteTypes.of("pleiades_1", "region", None, Some("settlement"), None, None), None)
    assertEquals(SiteTypes.of("x", "site", Some("not-a-type"), None, None, None), None)
    // a Wikidata item's own P31 before the class the import reached; a heritage-only item has no class
    assertEquals(SiteTypes.of("wikidata_q1", "site", None, None, None, Some(SiteTypes.ArchaeologicalSite), List("Q23413")),
      Some("fortification"))
    assertEquals(SiteTypes.of("wikidata_q1", "site", None, None, None, None, List("Q44613")), Some("sanctuary"))
    assertEquals(SiteTypes.of("wikidata_q1", "site", None, None, None, Some(SiteTypes.HumanSettlement), List("Q1")),
      Some("settlement"), "an unknown P31: the import's class")

  test("every type and kind has an icon name; the mapping tables only name known types"):
    assert(SiteTypes.all.forall(_.icon.nonEmpty) && EventTaxonomy.kinds.forall(_.icon.nonEmpty))
    val ids = SiteTypes.all.map(_.id).toSet
    assert((SiteTypes.pleiadesTypes.values ++ SiteTypes.geonamesCodes.values ++ SiteTypes.wikidataClasses.values).forall(ids))

  test("Wikidata claims: P31 values of each item, deprecated ones dropped"):
    val js = parse("""{"entities":{"Q1":{"claims":{"P31":[
      {"rank":"normal","mainsnak":{"datavalue":{"value":{"id":"Q755017"}}}},
      {"rank":"deprecated","mainsnak":{"datavalue":{"value":{"id":"Q515"}}}}]}},"Q2":{"claims":{}}}}""").toOption.get
    assertEquals(EventProviders.WikidataEvents.claims(js, "P31"), Right(Map("Q1" -> List("Q755017"), "Q2" -> Nil)))
    val names = parse("""{"entities":{"Q1":{"labels":{"en":{"value":"Siege of X"},"de":{"value":"Belagerung von X"}},
      "aliases":{"en":[{"value":"X siege"}]}}}}""").toOption.get
    assertEquals(EventProviders.WikidataEvents.names(names).map(_("Q1").toSet), Right(Set("Siege of X", "Belagerung von X", "X siege")))
