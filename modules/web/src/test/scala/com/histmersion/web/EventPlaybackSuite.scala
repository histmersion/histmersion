package com.histmersion.web

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.{EventGeo, EventScale}

/** The events in playback (#1440): when an event shows and how strongly — 0 before its span, 1 inside it, a linear fade
  * after it measured in seconds of PLAYBACK — and the label it is drawn with: the point-only region label of
  * [[PlaceGlyph]], local (with a dot) or regional.
  */
class EventPlaybackSuite extends munit.FunSuite:

  private val kadesh = EventGeo(
    com.histmersion.api.catalogue.Event.Id("kadesh"),
    "Battle <of> Kadesh",
    "battle",
    EventScale.Local,
    "attested",
    TimeSpan(Some(-1274), None, None, Some(-1274)),
    com.histmersion.api.catalogue.Latitude(34.55),
    com.histmersion.api.catalogue.Longitude(36.5),
    "place",
    (Some("p")).map(com.histmersion.api.catalogue.Place.Id.apply),
    Some("Kadesh")
  )
  private val migration = kadesh.modify(_.id).setTo(com.histmersion.api.catalogue.Event.Id("m")).modify(_.label).setTo("Yamnaya expansion").modify(_.kind).setTo("migration").modify(_.scale).setTo(EventScale.Regional).modify(_.timeSpan).setTo(TimeSpan(Some(-3300), None, None, Some(-2200))).modify(_.placeLabel).setTo(None
  )

  test("alpha: nothing before the span, full inside it, a linear fade after it, then nothing"):
    val span = (-1274L, -1274L)
    assertEquals(EventPlayback.alpha(span, -1275, 10), 0.0)
    assertEquals(EventPlayback.alpha(span, -1274, 10), 1.0)
    assertEquals(EventPlayback.alpha(span, -1269, 10), 0.5)
    assertEquals(EventPlayback.alpha(span, -1264, 10), 0.0)
    assertEquals(EventPlayback.alpha(span, -1200, 10), 0.0)
    assertEquals(EventPlayback.alpha(span, -1273, 0), 0.0, "no fade: gone the year after")
    assertEquals(EventPlayback.alpha((-3300L, -2200L), -2800, 10), 1.0)

  test("a fade of N seconds lasts N seconds whatever the speed: it is converted to years at the rate playing"):
    assertEquals(EventPlayback.fadeYears(2.5, 100.0), 250.0)
    assertEquals(EventPlayback.fadeYears(2.5, 10.0), 25.0)
    assertEquals(EventPlayback.fadeYears(-1, 10.0), 0.0)

  test("step: visibility moves in twentieths, so a slow fade does not rewrite the page every frame"):
    assertEquals(EventPlayback.step(0.51), 0.5)
    assertEquals(EventPlayback.step(0.53), 0.55)
    assertEquals(EventPlayback.step(1.0), 1.0)
    assertEquals(EventPlayback.step(0.01), 0.0)

  test("the label: PlaceGlyph's name and note, a dot for a local event, none for a regional one"):
    val local = EventPlayback.html(kadesh, 1.0)
    // hidden until the map's collision pass places it
    assert(local.contains("class=\"hd-area-point is-culled\""), local)
    assert(local.contains("hd-area-point-name\">Battle &lt;of&gt; Kadesh<"), local)
    assert(local.contains("hd-event--local"), local)
    assert(local.contains("hd-event-dot"), local)
    assert(local.contains("--a:1"), local)
    assert(!local.contains("<of>"))
    val regional = EventPlayback.html(migration, 0.5)
    assert(regional.contains("hd-event--regional"), regional)
    assert(!regional.contains("hd-event-dot"), regional)
    assert(regional.contains("--a:0.5"), regional)

  test("the note: kind and years — one year alone, or a range"):
    assertEquals(EventPlayback.note(kadesh), s"${Fmt.display("battle")} · ${Fmt.yr(-1274)}")
    assertEquals(EventPlayback.note(migration), s"${Fmt.display("migration")} · ${Fmt.yr(-3300)}–${Fmt.yr(-2200)}")

  test("events at one point stack: slots number the SHOWN ones in their order there"):
    assertEquals(EventPlayback.anchorKey(kadesh), EventPlayback.anchorKey(kadesh.modify(_.id).setTo(com.histmersion.api.catalogue.Event.Id("other"))))
    assertNotEquals(EventPlayback.anchorKey(kadesh), EventPlayback.anchorKey(kadesh.modify(_.lat).setTo(com.histmersion.api.catalogue.Latitude(34.56))))
    val order = List("first", "second", "third")
    assertEquals(EventPlayback.slots(order, _ => true), Map("first" -> 0, "second" -> 1, "third" -> 2))
    // the first gone, the others move up
    assertEquals(EventPlayback.slots(order, _ != "first"), Map("second" -> 0, "third" -> 1))
    assertEquals(EventPlayback.slots(order, _ => false), Map.empty[String, Int])

  test("rank: every event name outranks every area and point name; local over regional; later first"):
    val world = 256.0 * 256.0 // an area name's rank is its zoom-0 area: at most the whole world
    val battle = EventPlayback.rank(kadesh, -1274)
    val era = EventPlayback.rank(migration, -3300)
    assert(era > world && era > -1.0, s"$era")
    assert(battle > era)
    assert(EventPlayback.rank(migration, -1000) > EventPlayback.rank(migration, -3300))
    assert(EventPlayback.rank(kadesh, -1177) > battle, "the later of two local events wins")
    assert(EventPlayback.rank(kadesh, 2000) < EventPlayback.rank(kadesh, 2001))
    assert(EventPlayback.rank(migration, 3000) < EventPlayback.rank(kadesh, -4000), "local over regional at any year")

  test(
    "cull: an event name over a large area's name keeps the event, drops the area; two events at nearby sites do not overlap"
  ):
    import LabelPlacement.Box
    val area = Box("a|greece", 100, 100, 120, 16, 30000)
    val mycenae = Box("e|mycenae", 110, 102, 90, 24, EventPlayback.rank(kadesh, -1190))
    val tiryns = Box("e|tiryns", 115, 110, 80, 24, EventPlayback.rank(kadesh, -1180))
    val pylos = Box("e|pylos", 20, 200, 70, 24, EventPlayback.rank(kadesh, -1180))
    val kept = LabelPlacement.cull(Seq(area, mycenae, tiryns, pylos))
    assertEquals(kept, Set("e|tiryns", "e|pylos"))
