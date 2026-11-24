package com.histmersion
package domain.catalogue.events
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.aliases.AliasKinds

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.{Api, TimeSpan, Write}
import com.histmersion.api.catalogue.{EventPatch, EventPut}
import com.histmersion.api.library.Filter
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.{CatalogueState, Event}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.aliases.AliasKind

import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.EventEntry
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{LintRule, tolerated}

/** The events' controllers over the in-memory service: an old id resolved before a read or a write, the scale, the
  * geometry and the Wikidata item checked before a write, the card added to what is read, the rename's rule and a held
  * lock.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
class EventsControllersSuite extends munit.FunSuite:
  private val renamed =
    Map[(AliasKind, Event.Id), Event.Id]((AliasKinds.EventRecords, Event.Id("zz_old")) -> Event.Id("zz_ev"))
  private def current(k: AliasKind, id: Event.Id) = renamed.getOrElse((k, id), id)
  private val span = TimeSpan(Some(Year(-1200)), None, None, Some(Year(-1190)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def controllers(gate: WriteGate = WriteGate()) =
    EventsControllers(
      EventService.InMemory(AtomicReference(CatalogueState.empty)),
      d => d.modify(_.names).setTo(List(EventEntry.Names("from the card"))),
      current,
      f => f.modify(_.limit).setTo(Filter.Limit(f.limit.value.min(2))),
      _ => PlaceScope.empty,
      () => (PageSize(10), GeoTolerance(0.01)),
      gate
    )

  private def code(r: Either[Api.Error, ?]) = r.left.toOption.flatMap(_.code)

  test("an old id reads and writes the event it was renamed to; the card is added"):
    val c = controllers()
    val put = c.put(
      Caller.Local,
      Event.Id("zz_old"),
      EventPut(EventSummary.Label("Zz event"), EventSummary.Kind("battle"), span)
    )
    assertEquals(put.map(d => (d.event.id, d.names)), Right(("zz_ev", List("from the card"))))
    assertEquals(c.get(Event.Id("zz_old")).map(_.event.id), Right("zz_ev"))
    assertEquals(
      c.patch(Caller.Local, Event.Id("zz_old"), EventPatch(label = Some(EventSummary.Label("Zz again"))))
        .map(_.event.label),
      Right("Zz again")
    )
    assertEquals(code(c.get(Event.Id("zz_missing"))), Some(Write.CodeNotFound))
    assertEquals(c.delete(Caller.Local, Event.Id("zz_old")).map(_.id), Right("zz_ev"))
    assertEquals(code(c.delete(Caller.Local, Event.Id("zz_old"))), Some(Write.CodeNotFound))

  test("a scale, a geometry or a Wikidata item the store cannot take is refused before the write"):
    val c = controllers()
    assertEquals(
      code(
        c.put(
          Caller.Local,
          Event.Id("zz_s"),
          EventPut(
            EventSummary.Label("Zz"),
            EventSummary.Kind("battle"),
            span,
            scale = Some(EventSummary.Scale("huge"))
          )
        )
      ),
      Some(Write.CodeInvalid)
    )
    assertEquals(
      code(
        c.put(
          Caller.Local,
          Event.Id("zz_g"),
          EventPut(
            EventSummary.Label("Zz"),
            EventSummary.Kind("battle"),
            span,
            geometry = Some(EventPatch.Geometry("POINT(1 2)"))
          )
        )
      ),
      Some(Write.CodeInvalid)
    )
    assertEquals(code(c.get(Event.Id("zz_s"))), Some(Write.CodeNotFound), "nothing written")
    val _ = c.put(Caller.Local, Event.Id("zz_w"), EventPut(EventSummary.Label("Zz"), EventSummary.Kind("battle"), span))
    assertEquals(
      code(c.patch(Caller.Local, Event.Id("zz_w"), EventPatch(wikidata = Some(EventPatch.Wikidata("x1"))))),
      Some(Write.CodeInvalid)
    )
    assertEquals(
      c.patch(Caller.Local, Event.Id("zz_w"), EventPatch(scale = Some(EventSummary.Scale("auto"))))
        .map(_.event.scaleStated),
      Right(false)
    )

  test("the list's page from its filter; the map's limit"):
    val c = controllers()
    List("zz_1", "zz_2", "zz_3").foreach(id =>
      c.put(
        Caller.Local,
        Event.Id.applyUnsafe(id),
        EventPut(
          EventSummary.Label(s"Zz $id"),
          EventSummary.Kind("battle"),
          span,
          geometry = Some(EventPut.Geometry("""{"type":"Point","coordinates":[1,2]}"""))
        )
      )
    )
    val l = c.list(Api.EventQuery(Filter(limit = Filter.Limit(50))))
    assertEquals(((l.total.value: Int), l.events.size), (3, 2))
    assertEquals(c.geo(Api.EventQuery(Filter())).events.size, 3)

  test("a rename checks the new id against the current one; a held lock refuses every write"):
    val c = controllers()
    val _ = c.put(
      Caller.Local,
      Event.Id("zz_ev"),
      EventPut(EventSummary.Label("Zz event"), EventSummary.Kind("battle"), span)
    )
    def bad(from: Event.Id) =
      Left(
        Api.Error(
          Problem.Unclassified(Legacy.Text(s"'$from' is already that event id")),
          Some(Api.Error.Code(Write.CodeInvalid))
        )
      )
    assertEquals(code(c.rename(Caller.Local, Event.Id("zz_old"), bad)), Some(Write.CodeInvalid))
    assertEquals(c.rename(Caller.Local, Event.Id("zz_old"), _ => Right(Event.Id("zz_new"))), Right(("zz_ev", "zz_new")))
    val gate = WriteGate()
    val locked = controllers(gate)
    val held = gate.exclusively(WriteGate.OperationName("restore"))(
      Right(
        List(
          locked
            .put(Caller.Local, Event.Id("zz_x"), EventPut(EventSummary.Label("Zz"), EventSummary.Kind("battle"), span))
            .isLeft,
          locked.patch(Caller.Local, Event.Id("zz_x"), EventPatch()).isLeft,
          locked.delete(Caller.Local, Event.Id("zz_x")).isLeft,
          locked.rename(Caller.Local, Event.Id("zz_x"), _ => Right(Event.Id("zz_y"))).isLeft
        )
      )
    )
    assertEquals(held, Right(List(true, true, true, true)))
