package com.histmersion
package domain.media.links

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.Write
import com.histmersion.domain.media.{MediaStores, MediaStoreSuite}
import com.histmersion.domain.media.assets.NewFigure
import com.histmersion.api.media.AssetLink
import com.histmersion.domain.library.Citekey
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.media.AssetRelation
import com.histmersion.api.media.MediaTarget
import com.histmersion.domain.media.assets.MediaItemRow
import com.histmersion.domain.media.imports.Target

/** What the figures' links answer, the same on the database and in memory: a link needs its figure, follows its rename
  * and goes with it.
  */
abstract class AssetLinkServiceContract extends MediaStoreSuite:

  private val Ck = Citekey("zqal2001_x")
  private val ApiCk = com.histmersion.api.library.Citekey("zqal2001_x")
  private def fig(name: NewFigure.Name, tag: String) =
    NewFigure(
      name,
      NewFigure.Mime("image/png"),
      NewFigure.Bytes(Array[Byte](1, 2) ++ tag.getBytes("UTF-8")),
      None,
      None
    )

  stest("a link needs its figure; listed by source, figure and target; removed once") { s =>
    s.source(Ck.value)
    val _ = s.assets.addFigures(Ck, List(fig(NewFigure.Name("a.png"), "a"), fig(NewFigure.Name("b.png"), "b")))
    val l = s.links
    assertEquals(
      l.create(
        AssetLink.Put(
          ApiCk,
          Bundle.Asset.FileName("nope.png"),
          AssetRelation.Wire("depicts"),
          MediaTarget.TargetType("place"),
          MediaTarget.TargetId("zq_x")
        )
      ).left
        .map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    val first = l
      .create(
        AssetLink.Put(
          ApiCk,
          Bundle.Asset.FileName("a.png"),
          AssetRelation.Wire("depicts"),
          MediaTarget.TargetType("place"),
          MediaTarget.TargetId("zq_x"),
          Some(AssetLink.Xywh("0,0,1,1"))
        )
      )
      .toOption
      .get
    val second = l
      .create(
        AssetLink.Put(
          ApiCk,
          Bundle.Asset.FileName("b.png"),
          AssetRelation.Wire("illustrates"),
          MediaTarget.TargetType("event"),
          MediaTarget.TargetId("zq_e"),
          notes = Some(AssetLink.Notes("n"))
        )
      )
      .toOption
      .get
    assertEquals((first.id, second.id), (1L, 2L))
    assertEquals(l.list(Some(Ck), None, None, None).links.map(_.id), List(1L, 2L))
    assertEquals(l.list(None, Some(MediaItemRow.Uid(" a.png ")), None, None).links.map(_.id), List(1L))
    assertEquals(l.list(None, None, Some(Target.TargetType("event")), Some(Target.Id("zq_e"))).links, List(second))
    assertEquals((l.list(Some(Citekey.assume("")), None, None, None).total.value: Int), 2)
    assert(l.delete(AssetLinkService.LinkId(1L)))
    assert(!l.delete(AssetLinkService.LinkId(1L)))
    assertEquals(l.list(None, None, None, None).links.map(_.id), List(2L))
  }

  stest("a figure renamed by a new conversion takes its links along; a figure gone takes them with it") { s =>
    s.source(Ck.value)
    val _ = s.assets.addFigures(Ck, List(fig(NewFigure.Name("a.png"), "a"), fig(NewFigure.Name("b.png"), "b")))
    val _ = s.links.create(
      AssetLink.Put(
        ApiCk,
        Bundle.Asset.FileName("a.png"),
        AssetRelation.Wire("depicts"),
        MediaTarget.TargetType("place"),
        MediaTarget.TargetId("zq_x")
      )
    )
    val _ = s.links.create(
      AssetLink.Put(
        ApiCk,
        Bundle.Asset.FileName("b.png"),
        AssetRelation.Wire("depicts"),
        MediaTarget.TargetType("place"),
        MediaTarget.TargetId("zq_y")
      )
    )
    val _ = s.assets.replaceFigures(Ck, List(fig(NewFigure.Name("a2.png"), "a")))
    assertEquals(
      s.links.list(None, None, None, None).links.map(l => (l.assetName, l.targetId)),
      List("a2.png" -> "zq_x")
    )
  }

class AssetLinkServiceInMemorySuite extends AssetLinkServiceContract with MediaStoreSuite.InMemory
class AssetLinkServiceDbSuite extends AssetLinkServiceContract with MediaStoreSuite.OnDatabase
