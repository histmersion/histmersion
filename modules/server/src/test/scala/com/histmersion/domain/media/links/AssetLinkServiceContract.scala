package com.histmersion.domain.media.links

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.Write
import com.histmersion.domain.media.{MediaStores, MediaStoreSuite}
import com.histmersion.domain.media.assets.NewFigure
import com.histmersion.api.media.AssetLink

/** What the figures' links answer, the same on the database and in memory: a link needs its figure, follows its rename
  * and goes with it.
  */
abstract class AssetLinkServiceContract extends MediaStoreSuite:

  private val Ck = com.histmersion.domain.library.Citekey("zqal2001_x")
  private val ApiCk = com.histmersion.api.library.Citekey("zqal2001_x")
  private def fig(name: NewFigure.Name, tag: String) =
    NewFigure(name, com.histmersion.domain.media.assets.NewFigure.Mime("image/png"), NewFigure.Bytes(Array[Byte](1, 2) ++ tag.getBytes("UTF-8")), None, None)

  stest("a link needs its figure; listed by source, figure and target; removed once") { s =>
    s.source(Ck.value)
    val _ = s.assets.addFigures(Ck, List(fig(NewFigure.Name("a.png"), "a"), fig(NewFigure.Name("b.png"), "b")))
    val l = s.links
    assertEquals(
      l.create(AssetLink.Put(ApiCk, com.histmersion.api.exchange.Bundle.Asset.FileName("nope.png"), com.histmersion.api.media.AssetRelation.Wire("depicts"), com.histmersion.api.media.MediaTarget.TargetType("place"), com.histmersion.api.media.MediaTarget.TargetId("zq_x"))).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    val first = l.create(AssetLink.Put(ApiCk, com.histmersion.api.exchange.Bundle.Asset.FileName("a.png"), com.histmersion.api.media.AssetRelation.Wire("depicts"), com.histmersion.api.media.MediaTarget.TargetType("place"), com.histmersion.api.media.MediaTarget.TargetId("zq_x"), Some(com.histmersion.api.media.AssetLink.Xywh("0,0,1,1")))).toOption.get
    val second = l.create(AssetLink.Put(ApiCk, com.histmersion.api.exchange.Bundle.Asset.FileName("b.png"), com.histmersion.api.media.AssetRelation.Wire("illustrates"), com.histmersion.api.media.MediaTarget.TargetType("event"), com.histmersion.api.media.MediaTarget.TargetId("zq_e"), notes = Some("n"))).toOption.get
    assertEquals((first.id, second.id), (1L, 2L))
    assertEquals(l.list(Some(Ck), None, None, None).links.map(_.id), List(1L, 2L))
    assertEquals(l.list(None, Some(com.histmersion.domain.media.assets.MediaItemRow.Uid(" a.png ")), None, None).links.map(_.id), List(1L))
    assertEquals(l.list(None, None, Some(com.histmersion.domain.media.imports.Target.TargetType("event")), Some(com.histmersion.domain.media.imports.Target.Id("zq_e"))).links, List(second))
    assertEquals((l.list(Some(com.histmersion.domain.library.Citekey("")), None, None, None).total.value: Int), 2)
    assert(l.delete(com.histmersion.domain.media.links.AssetLinkService.LinkId(1L)))
    assert(!l.delete(com.histmersion.domain.media.links.AssetLinkService.LinkId(1L)))
    assertEquals(l.list(None, None, None, None).links.map(_.id), List(2L))
  }

  stest("a figure renamed by a new conversion takes its links along; a figure gone takes them with it") { s =>
    s.source(Ck.value)
    val _ = s.assets.addFigures(Ck, List(fig(NewFigure.Name("a.png"), "a"), fig(NewFigure.Name("b.png"), "b")))
    val _ = s.links.create(AssetLink.Put(ApiCk, com.histmersion.api.exchange.Bundle.Asset.FileName("a.png"), com.histmersion.api.media.AssetRelation.Wire("depicts"), com.histmersion.api.media.MediaTarget.TargetType("place"), com.histmersion.api.media.MediaTarget.TargetId("zq_x")))
    val _ = s.links.create(AssetLink.Put(ApiCk, com.histmersion.api.exchange.Bundle.Asset.FileName("b.png"), com.histmersion.api.media.AssetRelation.Wire("depicts"), com.histmersion.api.media.MediaTarget.TargetType("place"), com.histmersion.api.media.MediaTarget.TargetId("zq_y")))
    val _ = s.assets.replaceFigures(Ck, List(fig(NewFigure.Name("a2.png"), "a")))
    assertEquals(
      s.links.list(None, None, None, None).links.map(l => (l.assetName, l.targetId)),
      List("a2.png" -> "zq_x")
    )
  }

class AssetLinkServiceInMemorySuite extends AssetLinkServiceContract with MediaStoreSuite.InMemory
class AssetLinkServiceDbSuite extends AssetLinkServiceContract with MediaStoreSuite.OnDatabase
