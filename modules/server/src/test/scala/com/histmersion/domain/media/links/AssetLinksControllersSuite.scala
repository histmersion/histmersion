package com.histmersion
package domain.media.links

import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.aliases.AliasKinds

import com.histmersion.domain.media.{MediaState, MediaStoreSuite}
import com.histmersion.domain.media.assets.{AssetService, NewFigure}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.identity.AliasTable
import com.histmersion.api.aliases.AliasKind

import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.media.AssetLink
import com.histmersion.domain.library.Citekey
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.media.AssetRelation
import com.histmersion.api.media.MediaTarget
import com.histmersion.domain.media.imports.Target
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

/** The figures' links' controllers over the in-memory services: a source and a target named by an old identifier are
  * resolved first; a link that is not there is not found; a held lock refuses every write.
  */
class AssetLinksControllersSuite extends munit.FunSuite:

  private val renamed = Map(
    (AliasKinds.SourceRecords, AliasTable.Id("zqold")) -> AliasTable.Id("zqnew2001_x"),
    (AliasKinds.PlaceRecords, AliasTable.Id("zq_oldplace")) -> AliasTable.Id("zq_place")
  )
  private def current(k: AliasKind, id: AliasTable.Id) = renamed.getOrElse((k, id), id)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def controllers(gate: WriteGate = WriteGate()) =
    val state = AtomicReference(MediaState.empty)
    val _ = AssetService
      .InMemory(state, MediaStoreSuite.Ticking())
      .addFigures(
        Citekey("zqnew2001_x"),
        List(
          NewFigure(NewFigure.Name("a.png"), NewFigure.Mime("image/png"), NewFigure.Bytes(Array[Byte](1)), None, None)
        )
      )
    AssetLinksControllers(AssetLinkService.InMemory(state), current, gate)

  test("old identifiers resolved on a write and a read; a missing link not found"):
    val c = controllers()
    val made = c.create(
      AssetLink.Put(
        api.library.Citekey("zqold"),
        Bundle.Asset.FileName("a.png"),
        AssetRelation.Wire("depicts"),
        MediaTarget.TargetType("place"),
        MediaTarget.TargetId("zq_oldplace")
      )
    )
    assertEquals(made.map(l => (l.citekey, l.targetId)), Right(("zqnew2001_x", "zq_place")))
    assertEquals(
      c.list(Some(Citekey("zqold")), None, Some(Target.TargetType("place")), Some(Target.Id("zq_oldplace")))
        .links
        .map(_.id),
      List(1L)
    )
    assertEquals(
      c.delete(AssetLinkService.LinkId(9L)).left.map(_.problems.head).left.map(_.sentence),
      Left("No asset link #9")
    )
    assertEquals(
      c.delete(AssetLinkService.LinkId(1L)),
      Right(AssetLink.Deleted(AssetLink.Id(1L), AssetLink.Deleted.Value(true)))
    )

  test("a held lock refuses every write"):
    val gate = WriteGate()
    val c = controllers(gate)
    val held = gate.exclusively(WriteGate.OperationName("restore"))(
      Right(
        (
          c.create(
            AssetLink.Put(
              api.library.Citekey("zqold"),
              Bundle.Asset.FileName("a.png"),
              AssetRelation.Wire("depicts"),
              MediaTarget.TargetType("place"),
              MediaTarget.TargetId("zq_x")
            )
          ),
          c.delete(AssetLinkService.LinkId(1L))
        )
      )
    )
    val (a, b) = held.fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assert(a.isLeft && b.isLeft, (a, b))
