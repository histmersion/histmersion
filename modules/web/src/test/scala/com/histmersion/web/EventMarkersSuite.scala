package com.histmersion.web

import com.histmersion.core.*

/** #1449, the web's pure half: the one icon registry covers every kind and site type, an event marker
  * carries its kind's icon, its tier and its card's attribute, a typed site's icon its colour, the key
  * names every icon, the menus list the vocabularies, and an event card's sections. */
class EventMarkersSuite extends munit.FunSuite:

  test("the registry has an icon for every event kind and every site type, each a currentColor SVG"):
    val missing = (EventTaxonomy.kinds.map(_.icon) ++ SiteTypes.all.map(_.icon)).filterNot(Glyphs.inner.contains)
    assertEquals(missing, Nil)
    val svg = Glyphs.svg("swords")
    assert(svg.startsWith("""<svg class="glyph" viewBox="0 0 24 24"""") && svg.contains("<polyline"), svg)
    assertEquals(Glyphs.svg("no-such-icon"), "")
    assert(!Glyphs.inner.values.exists(v => v.contains("fill=\"#") || v.contains("stroke=\"#")), "no colour in an icon")

  private val battle = EventGeo("zz_b", "Zz <battle>", "battle", "local", "attested",
    TimeSpan(Some(-1274), None, None, Some(-1274)), 37.5, 32, "event", tier = EventTier.Attested)
  private val eruption = EventGeo("zz_e", "Eruption of Zz", "eruption", "local", "attested",
    TimeSpan(Some(-1630), None, None, Some(-1600)), 37, 33, "event", tier = EventTier.Catalogue, size = Some("VEI 6"))

  test("an event marker: its kind's icon, its tier and group as classes, its card's attribute, the colour as --c"):
    val h = EventMarker.html(battle, "rgb(1,2,3)")
    assert(h.contains("hd-ev--attested") && h.contains("hd-ev--human"), h)
    assert(h.contains("""data-event="zz_b"""") && h.contains("--c:rgb(1,2,3)"), h)
    assert(h.contains(Glyphs.svg("swords")), h)
    assert(h.contains("Zz &lt;battle&gt;"), "the label is escaped")
    val c = EventMarker.html(eruption, "rgb(1,2,3)")
    assert(c.contains("hd-ev--catalogue") && c.contains("hd-ev--natural") && c.contains("VEI 6") && c.contains("catalogue only"), c)

  test("an event's place on the ramp is the middle of its span within the span shown"):
    assertEquals(EventMarker.rampAt(eruption, (-1700L, -1500L)), 0.425)
    assertEquals(EventMarker.rampAt(battle, (-1000L, -500L)), 0.0, "clamped")
    assertEquals(EventMarker.rampAt(battle, (-1274L, -1274L)), 0.5)

  test("the view as an area: wrapped and clamped like the in-view filter"):
    // Scala.js prints a whole Double without ".0"; the server's BBox.parse reads either
    assertEquals(EventMarker.area(10, 30, 40.12345, 45), "10,30,40.1235,45")
    assertEquals(EventMarker.area(-200, -95, 220, 95), "-180,-90,180,90")
    assertEquals(EventMarker.area(170, 0, 190, 10), "170,0,-170,10", "across the antimeridian")

  test("a typed site's icon carries its type, colour and title"):
    val t = SiteTypes.get("fortification").get
    val h = SiteIcon.html(t, "Zz fort", "rgb(9,9,9)", "hd-site-icon")
    assert(h.contains("""data-site-type="fortification"""") && h.contains("--c:rgb(9,9,9)") && h.contains(Glyphs.svg("castle")), h)

  test("the key names every kind, both tiers and every site type — only for the layers on"):
    val k = MapKey.html(events = true, sites = true)
    EventTaxonomy.kinds.foreach(x => assert(k.contains(x.label), x.label))
    SiteTypes.all.foreach(x => assert(k.contains(Fmt.esc(x.label)), x.label))
    assert(k.contains("catalogue only (a lead)"))
    assert(!MapKey.html(events = false, sites = true).contains("natural events"))
    assertEquals(MapKey.html(events = false, sites = false), "")

  test("the menus: all, the two groups, every kind; the tiers; every site type"):
    assertEquals(LayerFilters.eventKinds.take(3).map(_._1), List("", "natural", "human"))
    assertEquals(LayerFilters.eventKinds.size, 3 + EventTaxonomy.kinds.size)
    assertEquals(LayerFilters.tiers.map(_._1), List("", "attested", "catalogue"))
    assertEquals(LayerFilters.siteTypes.size, 1 + SiteTypes.all.size)

  test("an event card: attesting sources as source links, the catalogue as provenance, leads with their actions"):
    val d = EventDetail(EventSummary("e", "E", "eruption", TimeSpan(Some(-1600), None, None, Some(-1600)), "attested"),
      tier = EventTier.Catalogue,
      sources = Nil,
      catalogue = List(EventCatalogueRef("gvp", "1", "https://volcano.si.edu/volcano.cfm?vn=1", "GVP", "Smithsonian Terms of Use", "cite GVP", vei = Some(6))),
      candidates = List(EventLinkCandidate("e", "x2020", 0.9, title = Some("X"), reasons = List("names it 1 time"))),
      leads = List(EventLead("e", "E", "eruption", "catalogue", "1600 BCE", "No source…", EventLeads.actions("e", "E", "1600 BCE", 1))))
    val secs = Preview.eventSections(d)
    assertEquals(secs.map(_.kind), List("candidates", "catalogue", "leads"))
    assertEquals(secs.head.note, Some("unconfirmed — proposed from findings near it and passages naming it"))
    assertEquals(secs.head.items.head.citekey, Some("x2020"))
    assert(secs(1).items.head.text.contains("VEI 6") && secs(1).items.head.href.isDefined)
    assert(secs(1).note.exists(_.contains("research lead, not evidence")))
    val leads = secs(2).items
    assertEquals(leads.head.text, "No source…")
    assertEquals(leads.find(_.query.isDefined).flatMap(_.query), Some("E"), "the discover action asks the literature search")
    assert(leads.exists(_.command.exists(_.startsWith("histmersion queue acquire add"))))
    val card = Preview.card(d)
    assertEquals((card.icon, card.tier), (Some("mountain"), Some("catalogue")))
