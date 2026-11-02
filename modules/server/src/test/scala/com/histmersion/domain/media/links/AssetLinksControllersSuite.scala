package com.histmersion.domain.media.links

import com.histmersion.util.identity.AliasKinds

import com.histmersion.api.*
import com.histmersion.domain.media.{MediaState, MediaStoreSuite}
import com.histmersion.domain.media.assets.{AssetService, NewFigure}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.identity.AliasKind

import java.util.concurrent.atomic.AtomicReference

/** The figures' links' controllers over the in-memory services: a source and a target named by an old identifier are
  * resolved first; a link that is not there is not found; a held lock refuses every write.
  */
class AssetLinksControllersSuite extends munit.FunSuite:

  private val renamed = Map(
    (AliasKinds.SourceRecords, "zqold") -> "zqnew2001_x",
    (AliasKinds.PlaceRecords, "zq_oldplace") -> "zq_place"
  )
  private def current(k: AliasKind, id: String) = renamed.getOrElse((k, id), id)

  private def controllers(gate: WriteGate = WriteGate()) =
    val state = AtomicReference(MediaState.empty)
    val _ = AssetService
      .InMemory(state, MediaStoreSuite.Ticking())
      .addFigures("zqnew2001_x", List(NewFigure("a.png", "image/png", Array[Byte](1), None, None)))
    AssetLinksControllers(AssetLinkService.InMemory(state), current, gate)

  test("old identifiers resolved on a write and a read; a missing link not found"):
    val c = controllers()
    val made = c.create(AssetLinkPut("zqold", "a.png", "depicts", "place", "zq_oldplace"))
    assertEquals(made.map(l => (l.citekey, l.targetId)), Right(("zqnew2001_x", "zq_place")))
    assertEquals(c.list(Some("zqold"), None, Some("place"), Some("zq_oldplace")).links.map(_.id), List(1L))
    assertEquals(c.delete(9L).left.map(_.message), Left("No asset link #9"))
    assertEquals(c.delete(1L), Right(AssetLinkDeleted(1L, true)))

  test("a held lock refuses every write"):
    val gate = WriteGate()
    val c = controllers(gate)
    val held = gate.exclusively("restore")(
      Right((c.create(AssetLinkPut("zqold", "a.png", "depicts", "place", "zq_x")), c.delete(1L)))
    )
    val (a, b) = held.fold(e => fail(e.message), identity)
    assert(a.isLeft && b.isLeft, (a, b))
