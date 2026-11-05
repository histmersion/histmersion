package com.histmersion.domain.catalogue.events

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.{TimeSpan, Write}
import com.histmersion.api.catalogue.{
  EventDating,
  EventEdge,
  EventParticipant,
  EventPatch,
  EventPrecision,
  EventPut,
  EventScale,
  EventTier,
  Period
}
import com.histmersion.api.library.Filter
import com.histmersion.domain.catalogue.CatalogueStoreSuite
import com.histmersion.api.Api

/** The contract the events keep: every field read back, with its datings, edges (both ends) and participants in their
  * order; the list narrowed by text, kind, historicity, period and tier, in time order and paged; the map drawing the
  * placed, well-dated ones and counting the rest; a patch changing only what it names; a rename leaving an alias, and
  * refused onto a taken or an aliased id; a delete once.
  */
abstract class EventServiceContract extends CatalogueStoreSuite:
  private def span(a: Long, b: Long) = TimeSpan(Some(a), None, None, Some(b))
  private val point = """{"type":"Point","coordinates":[23.5,37.7]}"""

  stest("put and read back, edges seen from both ends, the tier and the precision") { s =>
    val e = s.events
    s.source("zzev2020_src")
    e.put("zz_b", EventPut("Zz second", "battle", span(-1200, -1190)))
    e.put(
      "zz_a",
      EventPut(
        "Zz first",
        "siege",
        TimeSpan(Some(-1250), Some(-1248), Some(-1240), Some(-1238)),
        "inferred",
        Some(point),
        None,
        None,
        Some("a long siege"),
        Some("zzev2020_src"),
        List(EventDating("ignored", "zzev2020_src", span(-1251, -1239), Some("c14"), Some("n"))),
        List(EventEdge("zz_a", "zz_b", "before", "zzev2020_src", None)),
        List(EventParticipant("ignored", "Achaeans", "attacker", None)),
        Some("regional")
      )
    )
    val a = e.get("zz_a").get
    assertEquals(
      (a.event.label, a.event.kind, a.event.historicity, a.event.description, a.event.citekey),
      ("Zz first", "siege", "inferred", Some("a long siege"), Some("zzev2020_src"))
    )
    assertEquals((a.event.scale, a.event.scaleStated, a.event.tier), (EventScale.Regional, true, EventTier.Attested))
    assertEquals(a.event.precision, EventPrecision.Precise)
    assertEquals(a.datings.map(d => (d.eventId, d.citekey, d.method)), List(("zz_a", "zzev2020_src", Some("c14"))))
    assertEquals(a.participants.map(p => (p.eventId, p.actor, p.role)), List(("zz_a", "Achaeans", "attacker")))
    assertEquals(a.edges.map(g => (g.srcId, g.dstId, g.kind)), List(("zz_a", "zz_b", "before")))
    assertEquals(e.get("zz_b").get.edges.map(_.srcId), List("zz_a"), "the edge seen from its other end")
    assertEquals(e.get("zz_b").get.event.tier, EventTier.Catalogue, "no source names it")
    assert(a.geometry.isDefined)
    assertEquals(e.get("zz_none"), None)
  }

  stest("the list: its own text, kind, historicity, period and tier, in time order, paged") { s =>
    val e = s.events
    e.put("zz_late", EventPut("Zz late flood", "flood", span(-500, -490), description = Some("rains")))
    e.put("zz_early", EventPut("Zz early battle", "battle", span(-1500, -1490), "legendary"))
    e.put("zz_mid", EventPut("Zz middle eruption", "eruption", span(-1000, -999), description = Some("ash fall")))
    def ids(sel: EventSelection, limit: Int = 50, offset: Int = 0) =
      e.list(sel, limit, offset).events.map(_.id)
    val all = EventSelection(Filter())
    assertEquals(ids(all), List("zz_early", "zz_mid", "zz_late"))
    assertEquals((e.list(all, 1, 1).total.value: Int), 3)
    assertEquals(ids(all, 1, 1), List("zz_mid"))
    assertEquals(ids(all.modify(_.text).setTo(Some("ASH"))), List("zz_mid"), "the description, any case")
    assertEquals(ids(all.modify(_.text).setTo(Some("battle"))), List("zz_early"))
    assertEquals(ids(all.modify(_.kind).setTo(Some("natural"))), List("zz_mid", "zz_late"))
    assertEquals(ids(all.modify(_.kind).setTo(Some("human"))), List("zz_early"))
    assertEquals(ids(all.modify(_.historicity).setTo(Some("Legendary"))), List("zz_early"))
    assertEquals(ids(EventSelection(Filter(period = Some(Period(-1100, -600))))), List("zz_mid"))
    assertEquals(ids(EventSelection(Filter(title = Some("late")))), List("zz_late"))
    assertEquals(ids(all.modify(_.narrow).setTo(EventSql.Narrow(tier = Some(EventTier.Attested)))), Nil)
    assertEquals(ids(all.modify(_.text).setTo(Some("100%"))), Nil, "a % is literal")
  }

  stest("the map: placed and well dated drawn, the unplaced and the imprecise counted") { s =>
    val e = s.events
    e.put("zz_here", EventPut("Zz here", "battle", span(-1200, -1190), geometry = Some(point)))
    e.put("zz_nowhere", EventPut("Zz nowhere", "battle", span(-1200, -1190)))
    e.put("zz_vague", EventPut("Zz vague", "battle", span(-5000, -1000), geometry = Some(point)))
    val g = e.geo(EventSelection(Filter()), 100, 0.01)
    assertEquals(g.events.map(x => (x.id, x.lat, x.lon, x.anchor)), List(("zz_here", 37.7, 23.5, "event")))
    assertEquals((g.unplaced, g.imprecise), (1, 1))
    assertEquals(e.geoOf(List("zz_here", "zz_nowhere", "zz_vague")).keySet, Set("zz_here"))
  }

  stest("a patch changes what it names; a missing event is refused") { s =>
    val e = s.events
    e.put("zz_p", EventPut("Zz patched", "battle", span(-1200, -1190), description = Some("before")))
    assertEquals(e.patch("zz_p", EventPatch(label = Some("Zz renamed label"), wikidata = Some("Q123"))), Right(()))
    val d = e.get("zz_p").get
    assertEquals((d.event.label, d.event.description, d.wikidata), ("Zz renamed label", Some("before"), Some("Q123")))
    assertEquals(e.patch("zz_p", EventPatch(wikidata = Some(""), geometry = Some(point))), Right(()))
    assertEquals(e.get("zz_p").map(x => (x.wikidata, x.geometry.isDefined)), Some((None, true)))
    assertEquals(e.patch("zz_p", EventPatch(geometry = Some(""))), Right(()))
    assertEquals(e.get("zz_p").flatMap(_.geometry), None, "\"\" clears the geometry")
    assertEquals(e.patch("zz_p", EventPatch(scale = Some("auto"))), Right(()))
    assertEquals(e.get("zz_p").map(_.event.scaleStated), Some(false))
    assertEquals(
      e.patch("zz_missing", EventPatch(label = Some("x"))).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )
  }

  stest("a rename leaves an alias; refused onto a taken or aliased id; renaming back drops the alias") { s =>
    val e = s.events
    e.put("zz_old", EventPut("Zz event", "battle", span(-1200, -1190)))
    e.put("zz_other", EventPut("Zz other", "battle", span(-1100, -1090)))
    e.put("zz_child", EventPut("Zz child", "battle", span(-1200, -1195), parentId = Some("zz_old")))
    assertEquals(e.rename("zz_old", "zz_new"), Right(()))
    assertEquals(e.renamedTo("zz_old"), Some("zz_new"))
    assertEquals(e.renamedTo("zz_new"), None, "a live id")
    assertEquals(e.get("zz_child").flatMap(_.event.parentId), Some("zz_new"), "its parts follow")
    def refused(r: Either[Api.Error, Unit]) = r.left.map(_.code)
    assertEquals(refused(e.rename("zz_other", "zz_new")), Left(Some(Write.CodeInvalid)), "taken")
    assertEquals(refused(e.rename("zz_other", "zz_old")), Left(Some(Write.CodeInvalid)), "an old id of another")
    assertEquals(refused(e.rename("zz_none", "zz_x")), Left(Some(Write.CodeNotFound)))
    assertEquals(e.rename("zz_new", "zz_old"), Right(()))
    assertEquals((e.renamedTo("zz_old"), e.renamedTo("zz_new")), (None, Some("zz_old")))
  }

  stest("a geometry the store cannot read is named; a delete once") { s =>
    val e = s.events
    assertEquals(e.geometryError(point), None)
    assert(e.geometryError("""{"type":"Point"}""").isDefined)
    e.put("zz_gone", EventPut("Zz gone", "battle", span(-1200, -1190)))
    assert(e.delete("zz_gone"))
    assert(!e.delete("zz_gone"))
    assertEquals(e.get("zz_gone"), None)
  }
