package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.{
  Correction,
  EventCatalogueRef,
  EventGeo,
  EventLead,
  EventLeads,
  EventLinkCandidate,
  EventPrecision,
  EventSummary,
  EventTaxonomy,
  EventTier,
  SiteTypes
}
import com.histmersion.api.views.EventDetail
import com.histmersion.api.catalogue.Place
import com.histmersion.api.library.Source

/** #1449, the web's pure half: the one icon registry covers every kind and site type, an event marker carries its
  * kind's icon, its tier and its card's attribute, a typed site's icon its colour, the key names every icon, the menus
  * list the vocabularies, and an event card's sections.
  */
class EventMarkersSuite extends munit.FunSuite:

  test("the registry has an icon for every event kind and every site type, each a currentColor SVG"):
    val missing = (EventTaxonomy.kinds.map(_.icon.value) ++ SiteTypes.all.map(_.icon.value)).filterNot(Glyphs.inner.contains)
    assertEquals(missing, Nil)
    val svg = Glyphs.svg(com.histmersion.web.Glyphs.Name("swords")).value
    assert(svg.startsWith("""<svg class="glyph" viewBox="0 0 24 24"""") && svg.contains("<polyline"), svg)
    assertEquals(Glyphs.svg(com.histmersion.web.Glyphs.Name("no-such-icon")).value, "")
    assert(!Glyphs.inner.values.exists(v => v.contains("fill=\"#") || v.contains("stroke=\"#")), "no colour in an icon")

  // legibility at marker size: within a shape, no two things share a glyph — every event kind, every site
  // type (and the unknown one), every source kind tells itself apart from its siblings
  test("within each shape every glyph is its own: event kinds, site types, source kinds"):
    def dupes[A](xs: List[A]) = xs.groupBy(identity).collect { case (k, v) if v.size > 1 => k }.toList
    assertEquals(dupes(EventTaxonomy.kinds.map(_.icon.value)), Nil)
    assertEquals(dupes(SiteTypes.all.map(_.icon.value) :+ CardIcons.Unknown), Nil)
    assertEquals(dupes(Source.Kinds.all.map(CardIcons.sourceKind(_, marker = true))), Nil)
    // the glyphs that blurred at ~15 px are gone from the markers: they are no longer vendored at all
    List("biohazard", "wheat-off", "handshake", "theater", "newspaper", "cog").foreach(n =>
      assert(!Glyphs.inner.contains(n), n)
    )

  private val battle = EventGeo(
    com.histmersion.api.catalogue.Event.Id("zz_b"),
    com.histmersion.api.catalogue.EventGeo.Label("Zz <battle>"),
    com.histmersion.api.catalogue.EventGeo.Kind("battle"),
    com.histmersion.api.catalogue.EventGeo.Scale("local"),
    com.histmersion.api.catalogue.EventGeo.Historicity("attested"),
    TimeSpan(Some(com.histmersion.api.catalogue.Year(-1274)), None, None, Some(com.histmersion.api.catalogue.Year(-1274))),
    com.histmersion.api.catalogue.Latitude(37.5),
    com.histmersion.api.catalogue.Longitude(32),
    com.histmersion.api.catalogue.EventGeo.Anchor("event"),
    tier = EventGeo.Tier(EventTier.Attested)
  )
  private val eruption = EventGeo(
    com.histmersion.api.catalogue.Event.Id("zz_e"),
    com.histmersion.api.catalogue.EventGeo.Label("Eruption of Zz"),
    com.histmersion.api.catalogue.EventGeo.Kind("eruption"),
    com.histmersion.api.catalogue.EventGeo.Scale("local"),
    com.histmersion.api.catalogue.EventGeo.Historicity("attested"),
    TimeSpan(Some(com.histmersion.api.catalogue.Year(-1630)), None, None, Some(com.histmersion.api.catalogue.Year(-1600))),
    com.histmersion.api.catalogue.Latitude(37),
    com.histmersion.api.catalogue.Longitude(33),
    com.histmersion.api.catalogue.EventGeo.Anchor("event"),
    tier = EventGeo.Tier(EventTier.Catalogue),
    size = Some(com.histmersion.api.catalogue.EventGeo.Size("VEI 6"))
  )

  test("an event marker: its kind's icon, its tier and group as classes, its card's attribute, the colour as --c"):
    val h = EventMarker.html(battle, com.histmersion.web.EventMarker.Color("rgb(1,2,3)")).value
    assert(h.contains("hd-ev--attested") && h.contains("hd-ev--human"), h)
    assert(h.contains("""data-id="zz_b"""") && h.contains("--c:rgb(1,2,3)"), h)
    assert(!h.contains("data-event"), "the hover is the point layer's, not the delegated link preview's")
    assert(h.contains(Glyphs.badge(com.histmersion.web.Glyphs.BadgeName("swords")).value) && h.contains("hd-badge"), h)
    assertEquals(EventMarker.describe(battle).value, "Zz <battle> · battle · 1274 BCE · attested")
    val c = EventMarker.html(eruption, com.histmersion.web.EventMarker.Color("rgb(1,2,3)")).value
    assert(c.contains("hd-ev--catalogue") && c.contains("hd-ev--natural"), c)
    assert(
      EventMarker.describe(eruption).value.contains("VEI 6") && EventMarker.describe(eruption).value.contains("catalogue only")
    )

  test("an event's place on the ramp is the middle of its span within the span shown"):
    assertEquals(EventMarker.rampAt(eruption, (com.histmersion.api.catalogue.Year(-1700L), com.histmersion.api.catalogue.Year(-1500L))).value, 0.425)
    assertEquals(EventMarker.rampAt(battle, (com.histmersion.api.catalogue.Year(-1000L), com.histmersion.api.catalogue.Year(-500L))).value, 0.0, "clamped")
    assertEquals(EventMarker.rampAt(battle, (com.histmersion.api.catalogue.Year(-1274L), com.histmersion.api.catalogue.Year(-1274L))).value, 0.5)

  test("a site's marker is a circle badge: its type's icon and colour; the exclamation mark when untyped"):
    val fort = SitePoint(Some(com.histmersion.web.SitePoint.Id("zz_f")), com.histmersion.web.SitePoint.Label("Zz fort"), SitePoint.Kind(Place.Kind.Site), Some(com.histmersion.web.SitePoint.SiteType("fortification")), None, com.histmersion.web.map.LatLon.Lat(37), com.histmersion.web.map.LatLon.Lon(32), None)
    val h = PointMarker.html(PointKinds.Sites, fort, "rgb(9,9,9)")
    assert(
      h.contains("""data-site-type="fortification"""") && h.contains("--c:rgb(9,9,9)") && h.contains(
        Glyphs.badge(com.histmersion.web.Glyphs.BadgeName("castle")).value
      ),
      h
    )
    assert(h.contains("hd-badge hd-badge--circle"), h)
    val plain = fort.modify(_.siteType).setTo(None).modify(_.label).setTo(SitePoint.Label("Zz plain"))
    val u = PointMarker.html(PointKinds.Sites, plain, "rgb(9,9,9)")
    assert(u.contains(Glyphs.badge(com.histmersion.web.Glyphs.BadgeName("alert-mark")).value) && !u.contains("data-site-type"), u)
    assertEquals(
      PointMarker.ariaLabel(PointKinds.Sites, plain).value,
      "Zz plain · site — show only this place and pin its card"
    )

  test("a badge's icon is drawn twice: a dark halo under the white ink; the same string for every marker"):
    val b = Glyphs.badge(com.histmersion.web.Glyphs.BadgeName("castle")).value
    assert(b.contains("""<g class="glyph-halo">""") && b.contains("""<g class="glyph-ink">"""), b)
    assertEquals(b.split(Glyphs.inner("castle"), -1).length - 1, 2)
    assert(Glyphs.badge(com.histmersion.web.Glyphs.BadgeName("castle")).value eq b, "cached")
    assertEquals(Glyphs.badge(com.histmersion.web.Glyphs.BadgeName("no-such-icon")).value, "")

  test(
    "the key names every shape and source kind; every kind, both tiers and every site type — only for the layers on"
  ):
    val k = MapKey.html(events = com.histmersion.web.MapKey.Events(true), sites = com.histmersion.web.MapKey.Sites(true)).value
    EventTaxonomy.kinds.foreach(x => assert(k.contains(x.label.value), x.label))
    SiteTypes.all.foreach(x => assert(k.contains(Fmt.esc(x.label.value)), x.label))
    assert(k.contains("catalogue only (a lead)"))
    assert(!MapKey.html(events = com.histmersion.web.MapKey.Events(false), sites = com.histmersion.web.MapKey.Sites(true)).value.contains("natural events"))
    val bare = MapKey.html(events = com.histmersion.web.MapKey.Events(false), sites = com.histmersion.web.MapKey.Sites(false)).value
    List("a finding (a source", "a site", "an event", "a picture", "a region or polity", "several nearby").foreach(w =>
      assert(bare.contains(w), w)
    )
    // the shapes are the toolbar's own layer icons
    (PointKinds.all.map(_.icon) :+ Icon.Region).foreach(i => assert(bare.contains(Icon.html(i).value), i.toString))
    assert(bare.contains("hd-cluster") && bare.contains("hd-badge--diamond"))
    assert(bare.contains("source of unknown kind") && bare.contains(Glyphs.badge(com.histmersion.web.Glyphs.BadgeName("alert-mark")).value))
    assert(!bare.contains("natural events") && !bare.contains("fortification"))

  test("the toolbar's layer icons are the descriptors' shapes"):
    assertEquals(MapLayer.Sites.icon, PointKinds.Sites.icon)
    assertEquals(MapLayer.Events.icon, PointKinds.Events.icon)
    assertEquals(MapLayer.Media.icon, PointKinds.Media.icon)
    assertEquals(MapLayer.Boundaries.icon, Icon.Region)

  test("the menus: all, the two groups, every kind; the tiers; every site type"):
    val kinds = LayerFilters.eventKinds
    assertEquals(kinds.head.value.value, "")
    assertEquals(kinds.filter(o => EventTaxonomy.groups.map(_.value).contains(o.value.value)).map(_.value.value), List("natural", "human"))
    assertEquals(kinds.size, 3 + EventTaxonomy.kinds.size)
    // every kind listed under its group, after that group's "all"
    EventTaxonomy.kinds.foreach { k =>
      val o = kinds.find(_.value.value == k.kind.value).get
      assertEquals(o.group.map(_.value), Some(s"${k.group} events"))
      assert(kinds.indexWhere(_.value.value == k.group.value) < kinds.indexOf(o))
      assert(o.icon.value.contains("hd-badge--square") && o.icon.value.contains(Glyphs.badge(Glyphs.BadgeName(k.icon.value)).value), o.icon)
    }
    assertEquals(LayerFilters.tiers.map(_.value.value), List("", "attested", "catalogue"))
    assert(
      LayerFilters.tiers(1).icon.value.contains("hd-ev--attested") && LayerFilters.tiers(2).icon.value.contains("hd-ev--catalogue")
    )
    assertEquals(LayerFilters.siteTypes.size, 1 + SiteTypes.all.size)
    assert(LayerFilters.siteTypes.tail.forall(_.icon.value.contains("hd-badge--circle")))
    assertEquals(LayerFilters.siteTypes.head.icon, Icon.html(PointKinds.Sites.icon))
    // each menu's "all" says which menu it is: the events' square, the tier's solid-and-dashed pair
    assertEquals(LayerFilters.eventKinds.head.icon, Icon.html(PointKinds.Events.icon))
    assertEquals(LayerFilters.tiers.head.icon, Icon.html(Icon.TierPair))
    assertNotEquals(LayerFilters.eventKinds.head.icon, LayerFilters.tiers.head.icon)

  test("the icon menu's keys: arrows clamp, Home/End jump, typing finds the next label"):
    assertEquals(IconSelect.step("ArrowDown", 0, 3), Some(1))
    assertEquals(IconSelect.step("ArrowDown", 2, 3), Some(2))
    assertEquals(IconSelect.step("ArrowUp", 0, 3), Some(0))
    assertEquals(IconSelect.step("End", 0, 3), Some(2))
    assertEquals(IconSelect.step("Home", 2, 3), Some(0))
    assertEquals(IconSelect.step("x", 0, 3), None)
    val os = List("all", "battle", "burial", "cave").map(l => IconSelect.Opt(IconSelect.Opt.Value(l), IconSelect.Opt.Label(l), com.histmersion.web.Icon.Html("")))
    assertEquals(IconSelect.typeAhead(os, 0, "b"), Some(1))
    assertEquals(IconSelect.typeAhead(os, 1, "b"), Some(2))
    assertEquals(IconSelect.typeAhead(os, 2, "b"), Some(1), "wraps")
    assertEquals(IconSelect.typeAhead(os, 0, "bu"), Some(2))
    assertEquals(IconSelect.typeAhead(os, 0, "zz"), None)

  test("an open-ended event's card dates it at the end it states and says the other is not recorded (#1768)"):
    val start = Preview.card(EventDetail(EventSummary(com.histmersion.api.catalogue.Event.Id("e"), com.histmersion.api.catalogue.EventSummary.Label("E"), com.histmersion.api.catalogue.EventSummary.Kind("war"), TimeSpan(notBefore = Some(com.histmersion.api.catalogue.Year(-1200))), com.histmersion.api.catalogue.EventSummary.Historicity("attested"))))
    assert(start.byline.value.contains("1200 BCE (end not recorded)"), start.byline)
    val both =
      Preview.card(EventDetail(EventSummary(com.histmersion.api.catalogue.Event.Id("e"), com.histmersion.api.catalogue.EventSummary.Label("E"), com.histmersion.api.catalogue.EventSummary.Kind("war"), TimeSpan(Some(com.histmersion.api.catalogue.Year(-431)), None, None, Some(com.histmersion.api.catalogue.Year(-404))), com.histmersion.api.catalogue.EventSummary.Historicity("attested"))))
    assert(!both.byline.value.contains("not recorded"), both.byline)
    assertEquals(
      EventPlayback
        .playable(
          List(EventGeo(com.histmersion.api.catalogue.Event.Id("e"), com.histmersion.api.catalogue.EventGeo.Label("E"), com.histmersion.api.catalogue.EventGeo.Kind("war"), com.histmersion.api.catalogue.EventGeo.Scale("regional"), com.histmersion.api.catalogue.EventGeo.Historicity("attested"), TimeSpan(notBefore = Some(com.histmersion.api.catalogue.Year(-1200))), com.histmersion.api.catalogue.Latitude(0), com.histmersion.api.catalogue.Longitude(0), com.histmersion.api.catalogue.EventGeo.Anchor("event")))
        )
        .map(_._2)
        .map((a, b) => (a.value, b.value)),
      Vector((-1200L, -1200L)),
      "playback holds it at its start, then fades"
    )

  test("an event card: attesting sources as source links, the catalogue as provenance, leads with their actions"):
    val d = EventDetail(
      EventSummary(com.histmersion.api.catalogue.Event.Id("e"), com.histmersion.api.catalogue.EventSummary.Label("E"), com.histmersion.api.catalogue.EventSummary.Kind("eruption"), TimeSpan(Some(com.histmersion.api.catalogue.Year(-1600)), None, None, Some(com.histmersion.api.catalogue.Year(-1600))), com.histmersion.api.catalogue.EventSummary.Historicity("attested")),
      tier = EventGeo.Tier(EventTier.Catalogue),
      sources = Nil,
      catalogue = List(
        EventCatalogueRef(
          com.histmersion.api.catalogue.EventCatalogueRef.Provider("gvp"),
          com.histmersion.api.catalogue.EventCatalogueRef.ProviderId("1"),
          com.histmersion.api.catalogue.EventCatalogueRef.Url("https://volcano.si.edu/volcano.cfm?vn=1"),
          com.histmersion.api.catalogue.EventCatalogueRef.Title("GVP"),
          com.histmersion.api.catalogue.EventCatalogueRef.Licence("Smithsonian Terms of Use"),
          com.histmersion.api.catalogue.EventCatalogueRef.Citation("cite GVP"),
          vei = Some(com.histmersion.api.catalogue.EventCatalogueRef.Vei(6))
        )
      ),
      candidates = List(EventLinkCandidate(com.histmersion.api.catalogue.Event.Id("e"), com.histmersion.api.library.Citekey("x2020"), EventLinkCandidate.Score(0.9), title = Some(com.histmersion.api.library.Title("X")), reasons = List(EventLinkCandidate.Reasons("names it 1 time")))),
      leads = List(
        EventLead(
          com.histmersion.api.catalogue.Event.Id("e"),
          com.histmersion.api.catalogue.EventLead.Label("E"),
          com.histmersion.api.catalogue.EventLead.Kind("eruption"),
          com.histmersion.api.catalogue.EventLead.Tier("catalogue"),
          com.histmersion.api.catalogue.EventLead.Years("1600 BCE"),
          com.histmersion.api.catalogue.EventLead.Text("No source…"),
          EventLeads.actions(com.histmersion.api.catalogue.Event.Id("e"), com.histmersion.api.catalogue.EventLead.Label("E"), com.histmersion.api.catalogue.EventLead.Years("1600 BCE"), com.histmersion.api.catalogue.LeadFacts.Proposed(1))
        )
      )
    )
    val secs = Preview.eventSections(d)
    assertEquals(secs.map(_.kind.value), List("candidates", "catalogue", "leads"))
    assertEquals(secs.head.note.map(_.value), Some("unconfirmed — proposed from findings near it and passages naming it"))
    assertEquals(secs.head.items.head.citekey.map(_.value), Some("x2020"))
    assert(secs(1).items.head.text.value.contains("VEI 6") && secs(1).items.head.href.isDefined)
    assert(secs(1).note.exists(_.value.contains("research lead, not evidence")))
    val leads = secs(2).items
    assertEquals(leads.head.text.value, "No source…")
    assertEquals(
      leads.find(_.query.isDefined).flatMap(_.query).map(_.value),
      Some("E"),
      "the discover action asks the literature search"
    )
    assert(leads.exists(_.command.exists(_.value.startsWith("histmersion queue acquire add"))))
    val card = Preview.card(d)
    assertEquals((card.icon.map(_.value), card.tier.map(_.value)), (Some("mountain"), Some("catalogue")))

  // ── how precisely an event is dated (the data-source assessment's P0) ─────────────────────────

  private val hekla = eruption.modify(_.id).setTo(com.histmersion.api.catalogue.Event.Id("gvp_12724")).modify(_.label).setTo(EventGeo.Label("Eruption of Hekla")).modify(_.timeSpan).setTo(TimeSpan(Some(Year(-2251)), None, None, Some(Year(2026)))).modify(_.precision).setTo(EventGeo.Precision(EventPrecision.Imprecise)).modify(_.dating).setTo(Some(EventGeo.Dating("250 CE ± 2500 (tephrochronology)"))
  )
  private val approx = eruption.modify(_.id).setTo(com.histmersion.api.catalogue.Event.Id("zz_a")).modify(_.precision).setTo(EventGeo.Precision(EventPrecision.Approximate))

  test("an imprecisely dated event is not drawn at a year — neither as a marker nor in playback"):
    assert(!PointKinds.Events.keep(hekla))
    assert(PointKinds.Events.keep(eruption) && PointKinds.Events.keep(approx))
    assertEquals(EventPlayback.playable(List(hekla, eruption, approx)).map(_._1.id), Vector("zz_e", "zz_a").map(com.histmersion.api.catalogue.Event.Id.apply))

  test("an approximately dated event is drawn faded: its marker and its playback label carry the class"):
    assert(EventMarker.html(approx, com.histmersion.web.EventMarker.Color("rgb(1,2,3)")).value.contains("hd-ev--approximate"))
    assert(!EventMarker.html(eruption, com.histmersion.web.EventMarker.Color("rgb(1,2,3)")).value.contains("hd-ev--approximate"))
    assert(EventPlayback.html(approx, EventPlayback.Alpha(1.0)).value.contains("hd-event--approximate"))
    assert(EventMarker.describe(approx).value.contains("approximate date"))

  test("the card of an imprecisely dated event: its stated year, ± and method, the window, and why it is off the map"):
    val d = EventDetail(
      EventSummary(
        com.histmersion.api.catalogue.Event.Id("gvp_12724"),
        com.histmersion.api.catalogue.EventSummary.Label("Eruption of Hekla"),
        com.histmersion.api.catalogue.EventSummary.Kind("eruption"),
        hekla.timeSpan,
        com.histmersion.api.catalogue.EventSummary.Historicity("attested"),
        precision = EventSummary.Precision(EventPrecision.Imprecise),
        central = Some(com.histmersion.api.catalogue.EventSummary.Central(250)),
        plusMinus = Some(com.histmersion.api.catalogue.EventSummary.PlusMinus(2500)),
        datingMethod = Some(com.histmersion.api.catalogue.EventSummary.DatingMethod("Tephrochronology"))
      ),
      tier = EventGeo.Tier(EventTier.Catalogue),
      corrections = List(
        Correction(
          Correction.Id(1),
          Correction.TargetKind("event"),
          Correction.TargetId("gvp_12724"),
          Correction.Field("label"),
          Correction.Value("\"Hekla 3\""),
          Correction.UpstreamValue("\"Eruption of Hekla\""),
          Correction.StatusValue(Correction.Status.Conflict),
          Some(Correction.Upstream("gvp")),
          Some(Correction.UpstreamNow("\"Eruption of Hekla volcano\""))
        )
      )
    )
    val card = Preview.card(d)
    assertEquals(card.byline.value, "Eruption · 250 CE ± 2500 (tephrochronology) · attested")
    assert(card.facts.map(_.value).contains("window: 2251 BCE–2026 CE"), card.facts.toString)
    assert(card.facts.map(_.value).contains("imprecise date — listed, not drawn on the map at a year"), card.facts.toString)
    assert(
      card.facts.exists(f => f.value.startsWith("corrected by hand — label: “Hekla 3”") && f.value.contains("CONFLICT")),
      card.facts.toString
    )
    // the events list shows the stated dating and marks it
    assertEquals(EventsTab.when(d.event).value, "250 CE ± 2500 (tephrochronology)")
