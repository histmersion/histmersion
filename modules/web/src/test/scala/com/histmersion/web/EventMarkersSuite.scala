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
    assert(h.contains("""data-id="zz_b"""") && h.contains("--c:rgb(1,2,3)"), h)
    assert(!h.contains("data-event"), "the hover is the point layer's, not the delegated link preview's")
    assert(h.contains(Glyphs.badge("swords")) && h.contains("hd-badge"), h)
    assertEquals(EventMarker.describe(battle), "Zz <battle> · battle · 1274 BCE · attested")
    val c = EventMarker.html(eruption, "rgb(1,2,3)")
    assert(c.contains("hd-ev--catalogue") && c.contains("hd-ev--natural"), c)
    assert(EventMarker.describe(eruption).contains("VEI 6") && EventMarker.describe(eruption).contains("catalogue only"))

  test("an event's place on the ramp is the middle of its span within the span shown"):
    assertEquals(EventMarker.rampAt(eruption, (-1700L, -1500L)), 0.425)
    assertEquals(EventMarker.rampAt(battle, (-1000L, -500L)), 0.0, "clamped")
    assertEquals(EventMarker.rampAt(battle, (-1274L, -1274L)), 0.5)

  test("the view as an area: wrapped and clamped like the in-view filter"):
    // Scala.js prints a whole Double without ".0"; the server's BBox.parse reads either
    assertEquals(EventMarker.area(10, 30, 40.12345, 45), "10,30,40.1235,45")
    assertEquals(EventMarker.area(-200, -95, 220, 95), "-180,-90,180,90")
    assertEquals(EventMarker.area(170, 0, 190, 10), "170,0,-170,10", "across the antimeridian")

  test("a site's marker is a circle badge: its type's icon and colour; the exclamation mark when untyped"):
    val fort = SitePoint(Some("zz_f"), "Zz fort", PlaceKind.Site, Some("fortification"), None, 37, 32, None)
    val h = PointMarker.html(PointKinds.Sites, fort, "rgb(9,9,9)")
    assert(h.contains("""data-site-type="fortification"""") && h.contains("--c:rgb(9,9,9)") && h.contains(Glyphs.badge("castle")), h)
    assert(h.contains("hd-badge hd-badge--circle"), h)
    val plain = fort.copy(siteType = None, label = "Zz plain")
    val u = PointMarker.html(PointKinds.Sites, plain, "rgb(9,9,9)")
    assert(u.contains(Glyphs.badge("alert-mark")) && !u.contains("data-site-type"), u)
    assertEquals(PointMarker.ariaLabel(PointKinds.Sites, plain), "Zz plain · site — show only this place and pin its card")

  test("a badge's icon is drawn twice: a dark halo under the white ink; the same string for every marker"):
    val b = Glyphs.badge("castle")
    assert(b.contains("""<g class="glyph-halo">""") && b.contains("""<g class="glyph-ink">"""), b)
    assertEquals(b.split(Glyphs.inner("castle"), -1).length - 1, 2)
    assert(Glyphs.badge("castle") eq b, "cached")
    assertEquals(Glyphs.badge("no-such-icon"), "")

  test("the key names every shape and source kind; every kind, both tiers and every site type — only for the layers on"):
    val k = MapKey.html(events = true, sites = true)
    EventTaxonomy.kinds.foreach(x => assert(k.contains(x.label), x.label))
    SiteTypes.all.foreach(x => assert(k.contains(Fmt.esc(x.label)), x.label))
    assert(k.contains("catalogue only (a lead)"))
    assert(!MapKey.html(events = false, sites = true).contains("natural events"))
    val bare = MapKey.html(events = false, sites = false)
    List("a finding (a source", "a site", "an event", "a picture", "a region or polity", "several nearby").foreach(w => assert(bare.contains(w), w))
    // the shapes are the toolbar's own layer icons
    (PointKinds.all.map(_.icon) :+ Icon.Region).foreach(i => assert(bare.contains(Icon.html(i)), i.toString))
    assert(bare.contains("hd-cluster") && bare.contains("hd-badge--diamond"))
    assert(bare.contains("source of unknown kind") && bare.contains(Glyphs.badge("alert-mark")))
    assert(!bare.contains("natural events") && !bare.contains("fortification"))

  test("the toolbar's layer icons are the descriptors' shapes"):
    assertEquals(MapLayer.Sites.icon, PointKinds.Sites.icon)
    assertEquals(MapLayer.Events.icon, PointKinds.Events.icon)
    assertEquals(MapLayer.Media.icon, PointKinds.Media.icon)
    assertEquals(MapLayer.Boundaries.icon, Icon.Region)

  test("the menus: all, the two groups, every kind; the tiers; every site type"):
    val kinds = LayerFilters.eventKinds
    assertEquals(kinds.head.value, "")
    assertEquals(kinds.filter(o => EventTaxonomy.groups.contains(o.value)).map(_.value), List("natural", "human"))
    assertEquals(kinds.size, 3 + EventTaxonomy.kinds.size)
    // every kind listed under its group, after that group's "all"
    EventTaxonomy.kinds.foreach { k =>
      val o = kinds.find(_.value == k.kind).get
      assertEquals(o.group, Some(s"${k.group} events"))
      assert(kinds.indexWhere(_.value == k.group) < kinds.indexOf(o))
      assert(o.icon.contains("hd-badge--square") && o.icon.contains(Glyphs.badge(k.icon)), o.icon)
    }
    assertEquals(LayerFilters.tiers.map(_.value), List("", "attested", "catalogue"))
    assert(LayerFilters.tiers(1).icon.contains("hd-ev--attested") && LayerFilters.tiers(2).icon.contains("hd-ev--catalogue"))
    assertEquals(LayerFilters.siteTypes.size, 1 + SiteTypes.all.size)
    assert(LayerFilters.siteTypes.tail.forall(_.icon.contains("hd-badge--circle")))
    assertEquals(LayerFilters.siteTypes.head.icon, Icon.html(PointKinds.Sites.icon))

  test("the icon menu's keys: arrows clamp, Home/End jump, typing finds the next label"):
    assertEquals(IconSelect.step("ArrowDown", 0, 3), Some(1))
    assertEquals(IconSelect.step("ArrowDown", 2, 3), Some(2))
    assertEquals(IconSelect.step("ArrowUp", 0, 3), Some(0))
    assertEquals(IconSelect.step("End", 0, 3), Some(2))
    assertEquals(IconSelect.step("Home", 2, 3), Some(0))
    assertEquals(IconSelect.step("x", 0, 3), None)
    val os = List("all", "battle", "burial", "cave").map(l => IconSelect.Opt(l, l, ""))
    assertEquals(IconSelect.typeAhead(os, 0, "b"), Some(1))
    assertEquals(IconSelect.typeAhead(os, 1, "b"), Some(2))
    assertEquals(IconSelect.typeAhead(os, 2, "b"), Some(1), "wraps")
    assertEquals(IconSelect.typeAhead(os, 0, "bu"), Some(2))
    assertEquals(IconSelect.typeAhead(os, 0, "zz"), None)

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
