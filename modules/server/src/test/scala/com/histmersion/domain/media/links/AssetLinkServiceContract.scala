package com.histmersion.domain.media.links

import com.histmersion.api.Write
import com.histmersion.domain.media.{MediaStores, MediaStoreSuite}
import com.histmersion.domain.media.assets.NewFigure
import com.histmersion.api.media.AssetLinkPut

/** What the figures' links answer, the same on the database and in memory: a link needs its figure, follows its rename
  * and goes with it.
  */
abstract class AssetLinkServiceContract extends MediaStoreSuite:

  private val Ck = "zqal2001_x"
  private def fig(name: String, tag: String) =
    NewFigure(name, "image/png", Array[Byte](1, 2) ++ tag.getBytes("UTF-8"), None, None)

  stest("a link needs its figure; listed by source, figure and target; removed once") { s =>
    s.source(Ck)
    val _ = s.assets.addFigures(Ck, List(fig("a.png", "a"), fig("b.png", "b")))
    val l = s.links
    assertEquals(
      l.create(AssetLinkPut(Ck, "nope.png", "depicts", "place", "zq_x")).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    val first = l.create(AssetLinkPut(Ck, "a.png", "depicts", "place", "zq_x", Some("0,0,1,1"))).toOption.get
    val second = l.create(AssetLinkPut(Ck, "b.png", "illustrates", "event", "zq_e", notes = Some("n"))).toOption.get
    assertEquals((first.id, second.id), (1L, 2L))
    assertEquals(l.list(Some(Ck), None, None, None).links.map(_.id), List(1L, 2L))
    assertEquals(l.list(None, Some(" a.png "), None, None).links.map(_.id), List(1L))
    assertEquals(l.list(None, None, Some("event"), Some("zq_e")).links, List(second))
    assertEquals(l.list(Some(""), None, None, None).total, 2)
    assert(l.delete(1L))
    assert(!l.delete(1L))
    assertEquals(l.list(None, None, None, None).links.map(_.id), List(2L))
  }

  stest("a figure renamed by a new conversion takes its links along; a figure gone takes them with it") { s =>
    s.source(Ck)
    val _ = s.assets.addFigures(Ck, List(fig("a.png", "a"), fig("b.png", "b")))
    val _ = s.links.create(AssetLinkPut(Ck, "a.png", "depicts", "place", "zq_x"))
    val _ = s.links.create(AssetLinkPut(Ck, "b.png", "depicts", "place", "zq_y"))
    val _ = s.assets.replaceFigures(Ck, List(fig("a2.png", "a")))
    assertEquals(
      s.links.list(None, None, None, None).links.map(l => (l.assetName, l.targetId)),
      List("a2.png" -> "zq_x")
    )
  }

class AssetLinkServiceInMemorySuite extends AssetLinkServiceContract with MediaStoreSuite.InMemory
class AssetLinkServiceDbSuite extends AssetLinkServiceContract with MediaStoreSuite.OnDatabase
