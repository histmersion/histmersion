package com.histmersion.domain.media.assets

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.{Api, Write}
import com.histmersion.domain.media.{MediaStores, MediaStoreSuite}
import com.histmersion.domain.media.MediaRole
import com.histmersion.api.media.{AssetKind, AssetPatch, MediaProvider}

/** What the pictures' service answers, the same on the database and in memory. */
abstract class AssetServiceContract extends MediaStoreSuite:

  private val Ck = "zqas2001_x"
  private def bytes(tag: String): Array[Byte] = Array[Byte](-1, -40, -1) ++ tag.getBytes("UTF-8")
  private def fig(name: String, tag: String) = NewFigure(name, com.histmersion.domain.media.assets.NewFigure.Mime("image/jpeg"), bytes(tag), Some(com.histmersion.domain.media.assets.NewFigure.Width(10)), Some(com.histmersion.domain.media.assets.NewFigure.Height(8)))

  private def picture(key: String, license: String = "CC0-1.0", kind: Option[String] = Some(AssetKind.Photo)) =
    ProviderAsset(
      com.histmersion.domain.media.assets.ProviderAsset.Provider("zq-provider"),
      key,
      s"zq-$key.jpg",
      com.histmersion.domain.media.assets.ProviderAsset.Mime("image/jpeg"),
      bytes(key),
      Some(com.histmersion.domain.media.assets.ProviderAsset.Width(40)),
      Some(com.histmersion.domain.media.assets.ProviderAsset.Height(30)),
      Some(bytes(s"thumb $key")),
      Some(com.histmersion.domain.media.assets.ProviderAsset.ThumbMime("image/jpeg")),
      Some(com.histmersion.domain.media.assets.ProviderAsset.ThumbWidth(4)),
      Some(com.histmersion.domain.media.assets.ProviderAsset.ThumbHeight(3)),
      license,
      Some(com.histmersion.domain.media.assets.ProviderAsset.LicenseUrl("https://creativecommons.org/publicdomain/zero/1.0/")),
      Some(com.histmersion.domain.media.assets.ProviderAsset.Author("Zq")),
      s"Zq picture $key",
      s"https://zq.example/$key",
      None,
      kind
    )

  stest("a source's figures: one per name and bytes, listed by name, read, counted; replaced, a moved one renamed") {
    s =>
      s.source(Ck)
      val a = s.assets
      assertEquals(a.addFigures(Ck, List(fig("b.jpg", "2"), fig("a.jpg", "1"), fig("again.jpg", "1"))), 2)
      assertEquals(a.addFigures(Ck, List(fig("a.jpg", "3"))), 1)
      assertEquals(
        a.figures(Ck).map(f => (f.name, f.sha256)),
        List("a.jpg" -> NewFigure.sha256(bytes("1")), "b.jpg" -> NewFigure.sha256(bytes("2")))
      )
      assertEquals(a.figureCount(Ck), 2)
      assertEquals(a.figure(Ck, com.histmersion.domain.media.assets.AssetService.AssetName("a.jpg")).map((m, b) => (m, b.toList)), Some(("image/jpeg", bytes("1").toList)))
      assertEquals(a.figure(Ck, com.histmersion.domain.media.assets.AssetService.AssetName("nope.jpg")), None)
      assertEquals(a.replaceFigures(Ck, List(fig("c.jpg", "2"), fig("a.jpg", "4"))), 2)
      assertEquals(
        a.figures(Ck).map(f => (f.name, f.sha256)),
        List("a.jpg" -> NewFigure.sha256(bytes("4")), "c.jpg" -> NewFigure.sha256(bytes("2")))
      )
      assertEquals(
        a.sourceFigures(Ck).map(f => (f.name, f.width, f.hasThumb)),
        List(("a.jpg", Some(10), false), ("c.jpg", Some(10), false))
      )
  }

  stest(
    "a figure described and patched: kind, caption, licence, point, a period bound by bound; an inverted one refused"
  ) { s =>
    s.source(Ck)
    val a = s.assets
    val _ = a.addFigures(Ck, List(fig("a.jpg", "1")))
    assertEquals(
      a.patch(Ck, com.histmersion.domain.media.assets.AssetService.AssetName("nope.jpg"), AssetPatch(caption = Some(com.histmersion.api.media.MediaItem.Caption("x")))).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    val first = a.patch(
      Ck,
      com.histmersion.domain.media.assets.AssetService.AssetName("a.jpg"),
      AssetPatch(
        kind = Some(com.histmersion.api.media.MediaItem.Kind("plan")),
        caption = Some(com.histmersion.api.media.MediaItem.Caption("A plan")),
        lat = Some(40.0),
        lon = Some(34.5),
        periodStart = Some(com.histmersion.api.catalogue.Year(-1600L))
      )
    )
    assertEquals(
      first.map(d => (d.kind, d.caption, d.lat, d.lon, d.periodStart, d.periodEnd)),
      Right((Some("plan"), Some("A plan"), Some(40.0), Some(34.5), Some(-1600L), None))
    )
    assertEquals(
      a.patch(Ck, com.histmersion.domain.media.assets.AssetService.AssetName("a.jpg"), AssetPatch(periodEnd = Some(com.histmersion.api.catalogue.Year(-1200L)))).map(d => (d.periodStart, d.periodEnd)),
      Right((Some(-1600L), Some(-1200L)))
    )
    assert(a.patch(Ck, com.histmersion.domain.media.assets.AssetService.AssetName("a.jpg"), AssetPatch(license = Some(com.histmersion.api.media.MediaItem.License("CC0-1.0")), periodEnd = Some(com.histmersion.api.catalogue.Year(-1700L)))).isLeft)
    assertEquals(a.detail(Ck, com.histmersion.domain.media.assets.AssetService.AssetName("a.jpg")).map(d => (d.license, d.periodEnd)), Some((Some("CC0-1.0"), Some(-1200L))))
    assertEquals(a.detail(Ck, com.histmersion.domain.media.assets.AssetService.AssetName("nope.jpg")), None)
  }

  stest("a provider's picture: stored once under its key, its kind kept, its rights re-read, a thumbnail set") { s =>
    val a = s.assets
    val (uid, fresh) = a.save(picture("k1", license = ""), (com.histmersion.domain.media.imports.Target.TargetType("place"), com.histmersion.domain.media.imports.Target.Id("zq_nowhere")))
    assert(fresh)
    assertEquals(a.known(com.histmersion.domain.media.assets.AssetService.Provider("zq-provider"), List(com.histmersion.domain.media.assets.MediaItemRow.ProviderKey("k1"), com.histmersion.domain.media.assets.MediaItemRow.ProviderKey("k2"))), Map("k1" -> Known(uid, None)))
    val (again, inserted) = a.save(picture("k1", kind = Some(AssetKind.Map)), (com.histmersion.domain.media.imports.Target.TargetType("place"), com.histmersion.domain.media.imports.Target.Id("zq_nowhere")))
    assertEquals((again, inserted), (uid, false))
    assertEquals(
      a.show(uid).map(i => (i.kind, i.license, i.provider, i.providerKey)),
      Some((Some(AssetKind.Photo), Some("CC0-1.0"), Some("zq-provider"), Some("k1")))
    )
    a.refreshRights(uid, Rights(com.histmersion.domain.media.assets.Rights.License("CC-BY-4.0"), None, Some(com.histmersion.domain.media.assets.Rights.Author("Ann")), com.histmersion.domain.media.assets.Rights.Credit("Ann, CC BY")))
    assertEquals(
      a.show(uid).map(i => (i.license, i.licenseUrl, i.author, i.credit)),
      Some((Some("CC-BY-4.0"), None, Some("Ann"), Some("Ann, CC BY")))
    )
    assertEquals(a.thumb(uid).map((m, b) => (m, b.toList)), Some(("image/jpeg", bytes("thumb k1").toList)))
    a.setThumb(uid, bytes("t2"), com.histmersion.domain.media.assets.ProviderAsset.ThumbMime("image/png"), Some(com.histmersion.domain.media.assets.ProviderAsset.ThumbWidth(2)), None)
    assertEquals(a.thumb(uid).map((m, b) => (m, b.toList)), Some(("image/png", bytes("t2").toList)))
    assertEquals(a.file(uid).map(_._2.toList), Some(bytes("k1").toList))
    assert(a.show(uid).flatMap(_.retrievedAt).exists(_.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")))
    assertEquals(a.show(com.histmersion.domain.media.assets.AssetService.AssetUid("00000000-0000-0000-0000-000000000000")), None)
  }

  stest("where pictures are shown: a link made once and updated, a source's picture replaced in its role, the cards") {
    s =>
      s.source(Ck)
      val a = s.assets
      val (p1, _) = a.save(picture("p1"), (com.histmersion.domain.media.imports.Target.TargetType("place"), com.histmersion.domain.media.imports.Target.Id("zq_x")))
      val (p2, _) = a.save(picture("p2"), (com.histmersion.domain.media.imports.Target.TargetType("place"), com.histmersion.domain.media.imports.Target.Id("zq_x")))
      assert(a.link(p2, com.histmersion.domain.media.assets.AssetService.TargetType("place"), com.histmersion.domain.media.assets.AssetService.TargetId("zq_x"), MediaRole.LocatorMap, None, None, com.histmersion.domain.media.assets.AssetService.LinkRank(0), None))
      assert(a.link(p1, com.histmersion.domain.media.assets.AssetService.TargetType("place"), com.histmersion.domain.media.assets.AssetService.TargetId("zq_x"), MediaRole.Image, Some(com.histmersion.domain.media.assets.AssetService.LinkProperty("P18")), Some(com.histmersion.domain.media.assets.AssetService.LinkItem("Q1")), com.histmersion.domain.media.assets.AssetService.LinkRank(1), None))
      assert(!a.link(p1, com.histmersion.domain.media.assets.AssetService.TargetType("place"), com.histmersion.domain.media.assets.AssetService.TargetId("zq_x"), MediaRole.Image, Some(com.histmersion.domain.media.assets.AssetService.LinkProperty("P18")), Some(com.histmersion.domain.media.assets.AssetService.LinkItem("Q1")), com.histmersion.domain.media.assets.AssetService.LinkRank(0), Some(com.histmersion.domain.media.imports.MediaMatch.Basis("its own picture"))))
      assertEquals(
        a.thumbs(com.histmersion.domain.media.assets.AssetService.TargetType("place"), com.histmersion.domain.media.assets.AssetService.TargetId("zq_x"), com.histmersion.domain.media.assets.AssetService.Limit(4)).map(t => (t.uid, t.role)),
        List(p1 -> MediaRole.Image, p2 -> MediaRole.LocatorMap)
      )
      assertEquals(a.thumbs(com.histmersion.domain.media.assets.AssetService.TargetType("place"), com.histmersion.domain.media.assets.AssetService.TargetId("zq_x"), com.histmersion.domain.media.assets.AssetService.Limit(1)).map(_.uid), List(p1))
      assertEquals(
        a.show(p1).map(_.targets.map(t => (t.targetId, t.role, t.basis))),
        Some(List(("zq_x", MediaRole.Image, Some("its own picture"))))
      )
      a.setSourcePicture(Ck, p1, MediaRole.Cover, Some(com.histmersion.domain.media.assets.AssetService.LinkProperty("calibre")))
      a.setSourcePicture(Ck, p2, MediaRole.Cover, Some(com.histmersion.domain.media.assets.AssetService.LinkProperty("calibre")))
      assertEquals(a.sourcePicture(Ck, MediaRole.Cover), Some((p2, "p2")))
      assertEquals(a.thumbs(com.histmersion.domain.media.assets.AssetService.TargetType("source"), Ck, com.histmersion.domain.media.assets.AssetService.Limit(4)).map(_.uid), List(p2))
      assertEquals(a.placeThumbs(com.histmersion.domain.catalogue.Place.Id("zq_nowhere"), 3.0, com.histmersion.domain.media.assets.AssetService.Limit(4)), Nil)
  }

  stest("the list, its facets and the map's pictures: by provider, licence and text; in the span") { s =>
    s.source(Ck)
    val a = s.assets
    val _ = a.addFigures(Ck, List(fig("f1.jpg", "1"), fig("f2.jpg", "2")))
    val (p1, _) = a.save(picture("p1"), (com.histmersion.domain.media.imports.Target.TargetType("place"), com.histmersion.domain.media.imports.Target.Id("zq_x")))
    val _ = a.patch(
      Ck,
      com.histmersion.domain.media.assets.AssetService.AssetName("f1.jpg"),
      AssetPatch(lat = Some(40.0), lon = Some(34.0), periodStart = Some(com.histmersion.api.catalogue.Year(-1600L)), periodEnd = Some(com.histmersion.api.catalogue.Year(-1500L)))
    )
    val _ = a.patch(Ck, com.histmersion.domain.media.assets.AssetService.AssetName("f2.jpg"), AssetPatch(license = Some(com.histmersion.api.media.MediaItem.License("CC-BY-4.0"))))
    assertEquals(
      a.list(Api.MediaQuery(provider = Some(MediaProvider.Source)), com.histmersion.domain.media.assets.AssetService.Limit(50)).items.map(_.name),
      List("f1.jpg", "f2.jpg")
    )
    assertEquals(a.list(Api.MediaQuery(provider = Some(com.histmersion.api.media.MediaProvider.Id("zq-provider"))), com.histmersion.domain.media.assets.AssetService.Limit(50)).items.map(_.uid), List(p1))
    assertEquals(a.list(Api.MediaQuery(license = Some(com.histmersion.api.Api.MediaQuery.License("none"))), com.histmersion.domain.media.assets.AssetService.Limit(50)).items.map(_.name), List("f1.jpg"))
    assertEquals(a.list(Api.MediaQuery(q = Some(com.histmersion.api.Api.MediaQuery.Q("F2"))), com.histmersion.domain.media.assets.AssetService.Limit(50)).items.map(_.name), List("f2.jpg"))
    val all = a.list(Api.MediaQuery(), com.histmersion.domain.media.assets.AssetService.Limit(2))
    assertEquals(((all.total.value: Int), all.items.size, all.items.head.uid), (3, 2, p1))
    assertEquals(a.facets.providers.map(c => (c.key, c.n)), List((MediaProvider.Source, 2), ("zq-provider", 1)))
    assertEquals(a.facets.licences.map(c => (c.key, c.n)), List(("CC-BY-4.0", 1), ("CC0-1.0", 1), ("none", 1)))
    assertEquals(
      a.geo(Api.MediaGeoQuery(), com.histmersion.domain.media.assets.AssetService.Limit(200)).map(h => (h.assetName, h.lat, h.periodStart, h.periodEnd)),
      List(("f1.jpg", 40.0, Some(-1600L), Some(-1500L)))
    )
    assertEquals(a.geo(Api.MediaGeoQuery(from = Some(com.histmersion.api.Api.MediaGeoQuery.From(-1550L)), to = Some(com.histmersion.api.Api.MediaGeoQuery.To(-1540L))), com.histmersion.domain.media.assets.AssetService.Limit(200)).size, 1)
    assertEquals(a.geo(Api.MediaGeoQuery(from = Some(com.histmersion.api.Api.MediaGeoQuery.From(-1400L)), to = Some(com.histmersion.api.Api.MediaGeoQuery.To(-1300L))), com.histmersion.domain.media.assets.AssetService.Limit(200)), Nil)
    assertEquals(a.geo(Api.MediaGeoQuery(kind = Some(com.histmersion.api.media.MediaItem.Kind("map"))), com.histmersion.domain.media.assets.AssetService.Limit(200)), Nil)
    assertEquals(a.geoOf(List(p1)), Map.empty)
  }

class AssetServiceInMemorySuite extends AssetServiceContract with MediaStoreSuite.InMemory
class AssetServiceDbSuite extends AssetServiceContract with MediaStoreSuite.OnDatabase
