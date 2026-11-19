package com.histmersion
package domain.catalogue.events

import com.histmersion.testsupport.ComparedByValue.given

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
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.catalogue.Event
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Event as ApiEvent

/** The contract the events keep: every field read back, with its datings, edges (both ends) and participants in their
  * order; the list narrowed by text, kind, historicity, period and tier, in time order and paged; the map drawing the
  * placed, well-dated ones and counting the rest; a patch changing only what it names; a rename leaving an alias, and
  * refused onto a taken or an aliased id; a delete once.
  */
abstract class EventServiceContract extends CatalogueStoreSuite:
  private def span(a: Year, b: Year) = TimeSpan(Some(a), None, None, Some(b))
  private val point = EventPatch.Geometry("""{"type":"Point","coordinates":[23.5,37.7]}""")

  stest("put and read back, edges seen from both ends, the tier and the precision") { s =>
    val e = s.events
    s.source("zzev2020_src")
    e.put(
      Event.Id("zz_b"),
      EventPut(EventSummary.Label("Zz second"), EventSummary.Kind("battle"), span(Year(-1200), Year(-1190)))
    )
    e.put(
      Event.Id("zz_a"),
      EventPut(
        EventSummary.Label("Zz first"),
        EventSummary.Kind("siege"),
        TimeSpan(Some(Year(-1250)), Some(Year(-1248)), Some(Year(-1240)), Some(Year(-1238))),
        EventSummary.Historicity("inferred"),
        Some(point),
        None,
        None,
        Some(EventSummary.Description("a long siege")),
        Some(Citekey("zzev2020_src")),
        List(
          EventDating(
            api.catalogue.Event.Id("ignored"),
            Citekey("zzev2020_src"),
            span(Year(-1251), Year(-1239)),
            Some(EventDating.Method("c14")),
            Some(EventDating.Note("n"))
          )
        ),
        List(
          EventEdge(
            api.catalogue.Event.Id("zz_a"),
            api.catalogue.Event.Id("zz_b"),
            EventEdge.Kind("before"),
            Citekey("zzev2020_src"),
            None
          )
        ),
        List(
          EventParticipant(
            api.catalogue.Event.Id("ignored"),
            EventParticipant.Actor("Achaeans"),
            EventParticipant.Role("attacker"),
            None
          )
        ),
        Some(EventSummary.Scale("regional"))
      )
    )
    val a = e.get(Event.Id("zz_a")).get
    assertEquals(
      (a.event.label, a.event.kind, a.event.historicity, a.event.description, a.event.citekey),
      ("Zz first", "siege", "inferred", Some("a long siege"), Some("zzev2020_src"))
    )
    assertEquals((a.event.scale, a.event.scaleStated, a.event.tier), (EventScale.Regional, true, EventTier.Attested))
    assertEquals(a.event.precision, EventPrecision.Precise)
    assertEquals(a.datings.map(d => (d.eventId, d.citekey, d.method)), List(("zz_a", "zzev2020_src", Some("c14"))))
    assertEquals(a.participants.map(p => (p.eventId, p.actor, p.role)), List(("zz_a", "Achaeans", "attacker")))
    assertEquals(a.edges.map(g => (g.srcId, g.dstId, g.kind)), List(("zz_a", "zz_b", "before")))
    assertEquals(e.get(Event.Id("zz_b")).get.edges.map(_.srcId), List("zz_a"), "the edge seen from its other end")
    assertEquals(e.get(Event.Id("zz_b")).get.event.tier, EventTier.Catalogue, "no source names it")
    assert(a.geometry.isDefined)
    assertEquals(e.get(Event.Id("zz_none")), None)
  }

  stest("the list: its own text, kind, historicity, period and tier, in time order, paged") { s =>
    val e = s.events
    e.put(
      Event.Id("zz_late"),
      EventPut(
        EventSummary.Label("Zz late flood"),
        EventSummary.Kind("flood"),
        span(Year(-500), Year(-490)),
        description = Some(EventSummary.Description("rains"))
      )
    )
    e.put(
      Event.Id("zz_early"),
      EventPut(
        EventSummary.Label("Zz early battle"),
        EventSummary.Kind("battle"),
        span(Year(-1500), Year(-1490)),
        EventSummary.Historicity("legendary")
      )
    )
    e.put(
      Event.Id("zz_mid"),
      EventPut(
        EventSummary.Label("Zz middle eruption"),
        EventSummary.Kind("eruption"),
        span(Year(-1000), Year(-999)),
        description = Some(EventSummary.Description("ash fall"))
      )
    )
    def ids(sel: EventSelection, limit: PageSize = PageSize(50), offset: Offset = Offset(0)) =
      e.list(sel, limit, offset).events.map(_.id)
    val all = EventSelection(Filter())
    assertEquals(ids(all), List("zz_early", "zz_mid", "zz_late"))
    assertEquals((e.list(all, PageSize(1), Offset(1)).total.value: Int), 3)
    assertEquals(ids(all, PageSize(1), Offset(1)), List("zz_mid"))
    assertEquals(
      ids(all.modify(_.text).setTo(Some(EventSelection.Text("ASH")))),
      List("zz_mid"),
      "the description, any case"
    )
    assertEquals(ids(all.modify(_.text).setTo(Some(EventSelection.Text("battle")))), List("zz_early"))
    assertEquals(ids(all.modify(_.kind).setTo(Some(EventSelection.Kind("natural")))), List("zz_mid", "zz_late"))
    assertEquals(ids(all.modify(_.kind).setTo(Some(EventSelection.Kind("human")))), List("zz_early"))
    assertEquals(ids(all.modify(_.historicity).setTo(Some(EventSelection.Historicity("Legendary")))), List("zz_early"))
    assertEquals(ids(EventSelection(Filter(period = Some(Period(Year(-1100), Year(-600)))))), List("zz_mid"))
    assertEquals(ids(EventSelection(Filter(title = Some(Filter.Title("late"))))), List("zz_late"))
    assertEquals(
      ids(all.modify(_.narrow).setTo(EventSql.Narrow(tier = Some(EventSql.Narrow.Tier(EventTier.Attested))))),
      Nil
    )
    assertEquals(ids(all.modify(_.text).setTo(Some(EventSelection.Text("100%")))), Nil, "a % is literal")
  }

  stest("events alike in start and label come by id, whatever order they were written in") { s =>
    val e = s.events
    def tie(id: Event.Id, label: EventSummary.Label, parent: Option[ApiEvent.Id]) =
      e.put(
        id,
        EventPut(
          label,
          EventSummary.Kind("battle"),
          span(Year(-1200), Year(-1190)),
          geometry = Some(point),
          parentId = parent
        )
      )
    // the first pair written against the order it is read in, the second along it
    tie(Event.Id("zzt_b"), EventSummary.Label("Zzt battle"), None)
    tie(Event.Id("zzt_a"), EventSummary.Label("Zzt battle"), None)
    tie(Event.Id("zzt_c"), EventSummary.Label("Zzt battle 2"), None)
    tie(Event.Id("zzt_d"), EventSummary.Label("Zzt battle 2"), None)
    val zzt = EventSelection(Filter()).modify(_.text).setTo(Some(EventSelection.Text("Zzt")))
    def listed = e.list(zzt, PageSize(50), Offset(0)).events.map(_.id)
    assertEquals(listed, List("zzt_a", "zzt_b", "zzt_c", "zzt_d"))
    assertEquals(listed, listed, "the same order on every read")
    assertEquals(e.list(zzt, PageSize(1), Offset(1)).events.map(_.id), List("zzt_b"), "a page cuts the same order")
    assertEquals(
      e.geo(zzt, PageSize(100), GeoTolerance(0.01)).events.map(_.id),
      List("zzt_a", "zzt_b", "zzt_c", "zzt_d")
    )
    tie(Event.Id("zzt_root"), EventSummary.Label("Zzt root"), None)
    tie(Event.Id("zzt_y"), EventSummary.Label("Zzt part"), Some(ApiEvent.Id("zzt_root")))
    tie(Event.Id("zzt_x"), EventSummary.Label("Zzt part"), Some(ApiEvent.Id("zzt_root")))
    assertEquals(e.get(Event.Id("zzt_root")).toList.flatMap(_.children.map(_.id)), List("zzt_x", "zzt_y"))
  }

  stest("the map: placed and well dated drawn, the unplaced and the imprecise counted") { s =>
    val e = s.events
    e.put(
      Event.Id("zz_here"),
      EventPut(
        EventSummary.Label("Zz here"),
        EventSummary.Kind("battle"),
        span(Year(-1200), Year(-1190)),
        geometry = Some(point)
      )
    )
    e.put(
      Event.Id("zz_nowhere"),
      EventPut(EventSummary.Label("Zz nowhere"), EventSummary.Kind("battle"), span(Year(-1200), Year(-1190)))
    )
    e.put(
      Event.Id("zz_vague"),
      EventPut(
        EventSummary.Label("Zz vague"),
        EventSummary.Kind("battle"),
        span(Year(-5000), Year(-1000)),
        geometry = Some(point)
      )
    )
    val g = e.geo(EventSelection(Filter()), PageSize(100), GeoTolerance(0.01))
    assertEquals(g.events.map(x => (x.id, x.lat, x.lon, x.anchor)), List(("zz_here", 37.7, 23.5, "event")))
    assertEquals((g.unplaced, g.imprecise), (1, 1))
    assertEquals(
      e.geoOf(List(Event.Id("zz_here"), Event.Id("zz_nowhere"), Event.Id("zz_vague"))).keySet,
      Set("zz_here")
    )
  }

  stest("a patch changes what it names; a missing event is refused") { s =>
    val e = s.events
    e.put(
      Event.Id("zz_p"),
      EventPut(
        EventSummary.Label("Zz patched"),
        EventSummary.Kind("battle"),
        span(Year(-1200), Year(-1190)),
        description = Some(EventSummary.Description("before"))
      )
    )
    assertEquals(
      e.patch(
        Event.Id("zz_p"),
        EventPatch(label = Some(EventSummary.Label("Zz renamed label")), wikidata = Some(EventPatch.Wikidata("Q123")))
      ),
      Right(())
    )
    val d = e.get(Event.Id("zz_p")).get
    assertEquals((d.event.label, d.event.description, d.wikidata), ("Zz renamed label", Some("before"), Some("Q123")))
    assertEquals(
      e.patch(Event.Id("zz_p"), EventPatch(wikidata = Some(EventPatch.Wikidata("")), geometry = Some(point))),
      Right(())
    )
    assertEquals(e.get(Event.Id("zz_p")).map(x => (x.wikidata, x.geometry.isDefined)), Some((None, true)))
    assertEquals(e.patch(Event.Id("zz_p"), EventPatch(geometry = Some(EventPatch.Geometry("")))), Right(()))
    assertEquals(e.get(Event.Id("zz_p")).flatMap(_.geometry), None, "\"\" clears the geometry")
    assertEquals(e.patch(Event.Id("zz_p"), EventPatch(scale = Some(EventSummary.Scale("auto")))), Right(()))
    assertEquals(e.get(Event.Id("zz_p")).map(_.event.scaleStated), Some(false))
    assertEquals(
      e.patch(Event.Id("zz_missing"), EventPatch(label = Some(EventSummary.Label("x")))).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )
  }

  stest("a rename leaves an alias; refused onto a taken or aliased id; renaming back drops the alias") { s =>
    val e = s.events
    e.put(
      Event.Id("zz_old"),
      EventPut(EventSummary.Label("Zz event"), EventSummary.Kind("battle"), span(Year(-1200), Year(-1190)))
    )
    e.put(
      Event.Id("zz_other"),
      EventPut(EventSummary.Label("Zz other"), EventSummary.Kind("battle"), span(Year(-1100), Year(-1090)))
    )
    e.put(
      Event.Id("zz_child"),
      EventPut(
        EventSummary.Label("Zz child"),
        EventSummary.Kind("battle"),
        span(Year(-1200), Year(-1195)),
        parentId = Some(api.catalogue.Event.Id("zz_old"))
      )
    )
    assertEquals(e.rename(Event.Id("zz_old"), Event.Id("zz_new")), Right(()))
    assertEquals(e.renamedTo(Event.Id("zz_old")), Some("zz_new"))
    assertEquals(e.renamedTo(Event.Id("zz_new")), None, "a live id")
    assertEquals(e.get(Event.Id("zz_child")).flatMap(_.event.parentId), Some("zz_new"), "its parts follow")
    def refused(r: Either[Api.Error, Unit]) = r.left.map(_.code)
    assertEquals(refused(e.rename(Event.Id("zz_other"), Event.Id("zz_new"))), Left(Some(Write.CodeInvalid)), "taken")
    assertEquals(
      refused(e.rename(Event.Id("zz_other"), Event.Id("zz_old"))),
      Left(Some(Write.CodeInvalid)),
      "an old id of another"
    )
    assertEquals(refused(e.rename(Event.Id("zz_none"), Event.Id("zz_x"))), Left(Some(Write.CodeNotFound)))
    assertEquals(e.rename(Event.Id("zz_new"), Event.Id("zz_old")), Right(()))
    assertEquals((e.renamedTo(Event.Id("zz_old")), e.renamedTo(Event.Id("zz_new"))), (None, Some("zz_old")))
  }

  stest("a geometry the store cannot read is named; a delete once") { s =>
    val e = s.events
    assertEquals(e.geometryError(EventDetailRow.Geometry(point.value)), None)
    assert(e.geometryError(EventDetailRow.Geometry("""{"type":"Point"}""")).isDefined)
    e.put(
      Event.Id("zz_gone"),
      EventPut(EventSummary.Label("Zz gone"), EventSummary.Kind("battle"), span(Year(-1200), Year(-1190)))
    )
    assert(e.delete(Event.Id("zz_gone")))
    assert(!e.delete(Event.Id("zz_gone")))
    assertEquals(e.get(Event.Id("zz_gone")), None)
  }
