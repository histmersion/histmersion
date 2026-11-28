package com.histmersion
package web

import hearth.kindlings.optics.syntax.*
import com.histmersion.testsupport.ComparedByValue.markupText

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.{EventGeo, EventScale, Year}
import com.histmersion.api.catalogue.Event
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Place
import com.histmersion.api.UiLabels
import com.histmersion.lint.{tolerated, LintRule}

/** The events in playback (#1440): when an event shows and how strongly — 0 before its span, 1 inside it, a linear fade
  * after it measured in seconds of PLAYBACK — and the label it is drawn with: the point-only region label of
  * [[PlaceGlyph]], local (with a dot) or regional.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class EventPlaybackSuite extends munit.FunSuite:

  private val kadesh = EventGeo(
    Event.Id("kadesh"),
    EventGeo.Label("Battle <of> Kadesh"),
    EventGeo.Kind("battle"),
    EventGeo.Scale(EventScale.Local),
    EventGeo.Historicity("attested"),
    TimeSpan(Some(Year(-1274)), None, None, Some(Year(-1274))),
    Latitude(34.55),
    Longitude(36.5),
    EventGeo.Anchor("place"),
    (Some("p")).map(Place.Id.apply),
    Some(EventGeo.PlaceLabel("Kadesh"))
  )
  private val migration = kadesh
    .modify(_.id)
    .setTo(Event.Id("m"))
    .modify(_.label)
    .setTo(EventGeo.Label("Yamnaya expansion"))
    .modify(_.kind)
    .setTo(EventGeo.Kind("migration"))
    .modify(_.scale)
    .setTo(EventGeo.Scale(EventScale.Regional))
    .modify(_.timeSpan)
    .setTo(TimeSpan(Some(Year(-3300)), None, None, Some(Year(-2200))))
    .modify(_.placeLabel)
    .setTo(None)

  test("alpha: nothing before the span, full inside it, a linear fade after it, then nothing"):
    val span = (Year(-1274L), Year(-1274L))
    assertEquals(EventPlayback.alpha(span, Year(-1275), EventPlayback.FadeYears(10)).value, 0.0)
    assertEquals(EventPlayback.alpha(span, Year(-1274), EventPlayback.FadeYears(10)).value, 1.0)
    assertEquals(EventPlayback.alpha(span, Year(-1269), EventPlayback.FadeYears(10)).value, 0.5)
    assertEquals(EventPlayback.alpha(span, Year(-1264), EventPlayback.FadeYears(10)).value, 0.0)
    assertEquals(EventPlayback.alpha(span, Year(-1200), EventPlayback.FadeYears(10)).value, 0.0)
    assertEquals(
      EventPlayback.alpha(span, Year(-1273), EventPlayback.FadeYears(0)).value,
      0.0,
      "no fade: gone the year after"
    )
    assertEquals(EventPlayback.alpha((Year(-3300L), Year(-2200L)), Year(-2800), EventPlayback.FadeYears(10)).value, 1.0)

  test("a fade of N seconds lasts N seconds whatever the speed: it is converted to years at the rate playing"):
    assertEquals(EventPlayback.fadeYears(EventPlayback.Seconds(2.5), EventPlayback.YearsPerSecond(100.0)).value, 250.0)
    assertEquals(EventPlayback.fadeYears(EventPlayback.Seconds(2.5), EventPlayback.YearsPerSecond(10.0)).value, 25.0)
    assertEquals(EventPlayback.fadeYears(EventPlayback.Seconds(-1), EventPlayback.YearsPerSecond(10.0)).value, 0.0)

  test("step: visibility moves in twentieths, so a slow fade does not rewrite the page every frame"):
    assertEquals(EventPlayback.step(EventPlayback.Alpha(0.51)).value, 0.5)
    assertEquals(EventPlayback.step(EventPlayback.Alpha(0.53)).value, 0.55)
    assertEquals(EventPlayback.step(EventPlayback.Alpha(1.0)).value, 1.0)
    assertEquals(EventPlayback.step(EventPlayback.Alpha(0.01)).value, 0.0)

  test("the label: PlaceGlyph's name and note, a dot for a local event, none for a regional one"):
    val local = EventPlayback.html(kadesh, EventPlayback.Alpha(1.0)).value
    // hidden until the map's collision pass places it
    assert(local.contains("class=\"hd-area-point is-culled\""), local)
    assert(local.contains("hd-area-point-name\">Battle &lt;of&gt; Kadesh<"), local)
    assert(local.contains("hd-event--local"), local)
    assert(local.contains("hd-event-dot"), local)
    assert(local.contains("--a:1"), local)
    assert(!local.contains("<of>"))
    val regional = EventPlayback.html(migration, EventPlayback.Alpha(0.5)).value
    assert(regional.contains("hd-event--regional"), regional)
    assert(!regional.contains("hd-event-dot"), regional)
    assert(regional.contains("--a:0.5"), regional)

  test("the note: kind and years — one year alone, or a range"):
    assertEquals(EventPlayback.note(kadesh).value, s"${Fmt.display(UiLabels.Text("battle"))} · ${Fmt.yr(Year(-1274))}")
    assertEquals(
      EventPlayback.note(migration).value,
      s"${Fmt.display(UiLabels.Text("migration"))} · ${Fmt.yr(Year(-3300))}–${Fmt.yr(Year(-2200))}"
    )

  test("events at one point stack: slots number the SHOWN ones in their order there"):
    assertEquals(EventPlayback.anchorKey(kadesh), EventPlayback.anchorKey(kadesh.modify(_.id).setTo(Event.Id("other"))))
    assertNotEquals(
      EventPlayback.anchorKey(kadesh),
      EventPlayback.anchorKey(kadesh.modify(_.lat).setTo(Latitude(34.56)))
    )
    val order = List("first", "second", "third")
    assertEquals(
      EventPlayback.slots(order, _ => true).map((k, v) => k -> v.value),
      Map("first" -> 0, "second" -> 1, "third" -> 2)
    )
    // the first gone, the others move up
    assertEquals(EventPlayback.slots(order, _ != "first").map((k, v) => k -> v.value), Map("second" -> 0, "third" -> 1))
    assertEquals(EventPlayback.slots(order, _ => false).map((k, v) => k -> v.value), Map.empty[String, Int])

  test("rank: every event name outranks every area and point name; local over regional; later first"):
    val world = 256.0 * 256.0 // an area name's rank is its zoom-0 area: at most the whole world
    val battle = EventPlayback.rank(kadesh, Year(-1274)).value
    val era = EventPlayback.rank(migration, Year(-3300)).value
    assert(era > world && era > -1.0, s"$era")
    assert(battle > era)
    assert(EventPlayback.rank(migration, Year(-1000)) > EventPlayback.rank(migration, Year(-3300)))
    assert(EventPlayback.rank(kadesh, Year(-1177)).value > battle, "the later of two local events wins")
    assert(EventPlayback.rank(kadesh, Year(2000)) < EventPlayback.rank(kadesh, Year(2001)))
    assert(
      EventPlayback.rank(migration, Year(3000)) < EventPlayback.rank(kadesh, Year(-4000)),
      "local over regional at any year"
    )

  test(
    "cull: an event name over a large area's name keeps the event, drops the area; two events at nearby sites do not overlap"
  ):
    import LabelPlacement.Box
    val area = Box("a|greece", 100, 100, 120, 16, 30000)
    val mycenae = Box("e|mycenae", 110, 102, 90, 24, EventPlayback.rank(kadesh, Year(-1190)).value)
    val tiryns = Box("e|tiryns", 115, 110, 80, 24, EventPlayback.rank(kadesh, Year(-1180)).value)
    val pylos = Box("e|pylos", 20, 200, 70, 24, EventPlayback.rank(kadesh, Year(-1180)).value)
    val kept = LabelPlacement.cull(Seq(area, mycenae, tiryns, pylos))
    assertEquals(kept, Set("e|tiryns", "e|pylos"))
