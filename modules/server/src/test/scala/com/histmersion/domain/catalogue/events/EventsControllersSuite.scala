package com.histmersion.domain.catalogue.events

import com.histmersion.util.identity.AliasKinds

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.{Api, ApiError, EventPatch, EventPut, Filter, TimeSpan, Write}
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.identity.AliasKind

import java.util.concurrent.atomic.AtomicReference

/** The events' controllers over the in-memory service: an old id resolved before a read or a write, the scale, the
  * geometry and the Wikidata item checked before a write, the card added to what is read, the rename's rule and a held
  * lock.
  */
class EventsControllersSuite extends munit.FunSuite:
  private val renamed = Map[(AliasKind, String), String]((AliasKinds.EventRecords, "zz_old") -> "zz_ev")
  private def current(k: AliasKind, id: String) = renamed.getOrElse((k, id), id)
  private val span = TimeSpan(Some(-1200), None, None, Some(-1190))

  private def controllers(gate: WriteGate = WriteGate()) =
    EventsControllers(
      EventService.InMemory(AtomicReference(CatalogueState.empty)),
      d => d.copy(names = List("from the card")),
      current,
      f => f.copy(limit = f.limit.min(2)),
      _ => PlaceScope.empty,
      () => (10, 0.01),
      gate
    )

  private def code(r: Either[ApiError, ?]) = r.left.toOption.flatMap(_.code)

  test("an old id reads and writes the event it was renamed to; the card is added"):
    val c = controllers()
    val put = c.put(Caller.Local, "zz_old", EventPut("Zz event", "battle", span))
    assertEquals(put.map(d => (d.event.id, d.names)), Right(("zz_ev", List("from the card"))))
    assertEquals(c.get("zz_old").map(_.event.id), Right("zz_ev"))
    assertEquals(
      c.patch(Caller.Local, "zz_old", EventPatch(label = Some("Zz again"))).map(_.event.label),
      Right("Zz again")
    )
    assertEquals(code(c.get("zz_missing")), Some(Write.CodeNotFound))
    assertEquals(c.delete(Caller.Local, "zz_old").map(_.id), Right("zz_ev"))
    assertEquals(code(c.delete(Caller.Local, "zz_old")), Some(Write.CodeNotFound))

  test("a scale, a geometry or a Wikidata item the store cannot take is refused before the write"):
    val c = controllers()
    assertEquals(
      code(c.put(Caller.Local, "zz_s", EventPut("Zz", "battle", span, scale = Some("huge")))),
      Some(Write.CodeInvalid)
    )
    assertEquals(
      code(c.put(Caller.Local, "zz_g", EventPut("Zz", "battle", span, geometry = Some("POINT(1 2)")))),
      Some(Write.CodeInvalid)
    )
    assertEquals(code(c.get("zz_s")), Some(Write.CodeNotFound), "nothing written")
    val _ = c.put(Caller.Local, "zz_w", EventPut("Zz", "battle", span))
    assertEquals(code(c.patch(Caller.Local, "zz_w", EventPatch(wikidata = Some("x1")))), Some(Write.CodeInvalid))
    assertEquals(c.patch(Caller.Local, "zz_w", EventPatch(scale = Some("auto"))).map(_.event.scaleStated), Right(false))

  test("the list's page from its filter; the map's limit"):
    val c = controllers()
    List("zz_1", "zz_2", "zz_3").foreach(id =>
      c.put(
        Caller.Local,
        id,
        EventPut(s"Zz $id", "battle", span, geometry = Some("""{"type":"Point","coordinates":[1,2]}"""))
      )
    )
    val l = c.list(Api.EventQuery(Filter(limit = 50)))
    assertEquals((l.total, l.events.size), (3, 2))
    assertEquals(c.geo(Api.EventQuery(Filter())).events.size, 3)

  test("a rename checks the new id against the current one; a held lock refuses every write"):
    val c = controllers()
    val _ = c.put(Caller.Local, "zz_ev", EventPut("Zz event", "battle", span))
    def bad(from: String) = Left(ApiError(s"'$from' is already that event id", Some(Write.CodeInvalid)))
    assertEquals(code(c.rename(Caller.Local, "zz_old", bad)), Some(Write.CodeInvalid))
    assertEquals(c.rename(Caller.Local, "zz_old", _ => Right("zz_new")), Right(("zz_ev", "zz_new")))
    val gate = WriteGate()
    val locked = controllers(gate)
    val held = gate.exclusively("restore")(
      Right(
        List(
          locked.put(Caller.Local, "zz_x", EventPut("Zz", "battle", span)).isLeft,
          locked.patch(Caller.Local, "zz_x", EventPatch()).isLeft,
          locked.delete(Caller.Local, "zz_x").isLeft,
          locked.rename(Caller.Local, "zz_x", _ => Right("zz_y")).isLeft
        )
      )
    )
    assertEquals(held, Right(List(true, true, true, true)))
