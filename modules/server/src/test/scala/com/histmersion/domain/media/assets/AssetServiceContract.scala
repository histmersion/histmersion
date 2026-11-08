package com.histmersion
package domain.media.assets

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.{Api, Write}
import com.histmersion.domain.media.{MediaStores, MediaStoreSuite}
import com.histmersion.domain.media.MediaRole
import com.histmersion.api.catalogue.{Latitude, Longitude}
import com.histmersion.api.media.{AssetKind, AssetPatch, MediaProvider}
import com.histmersion.domain.catalogue.evidence.EventMatcher
import com.histmersion.domain.library.Citekey
import com.histmersion.api.media.MediaItem
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.media.imports.Target
import com.histmersion.domain.media.imports.MediaMatch
import com.histmersion.domain.catalogue.Place

/** What the pictures' service answers, the same on the database and in memory. */
abstract class AssetServiceContract extends MediaStoreSuite:

  private val Ck = Citekey("zqas2001_x")
  private val ImageRole = MediaRole.Wire(MediaRole.Image)
  private val LocatorMapRole = MediaRole.Wire(MediaRole.LocatorMap)
  private val CoverRole = MediaRole.Wire(MediaRole.Cover)
  private def bytes(tag: String): Array[Byte] = Array[Byte](-1, -40, -1) ++ tag.getBytes("UTF-8")
  private def figureBytes(tag: String): NewFigure.Bytes = NewFigure.Bytes(bytes(tag))
  private def fig(name: NewFigure.Name, tag: String) = NewFigure(
    name,
    NewFigure.Mime("image/jpeg"),
    figureBytes(tag),
    Some(NewFigure.Width(10)),
    Some(NewFigure.Height(8))
  )

  private def picture(
      key: ProviderAsset.Key,
      license: ProviderAsset.License = ProviderAsset.License("CC0-1.0"),
      kind: Option[ProviderAsset.Kind] = Some(ProviderAsset.Kind(AssetKind.Photo))
  ) =
    ProviderAsset(
      ProviderAsset.Provider("zq-provider"),
      key,
      ProviderAsset.Name(s"zq-$key.jpg"),
      ProviderAsset.Mime("image/jpeg"),
      ProviderAsset.Bytes(bytes(key.value)),
      Some(ProviderAsset.Width(40)),
      Some(ProviderAsset.Height(30)),
      Some(ProviderAsset.Thumb(bytes(s"thumb $key"))),
      Some(ProviderAsset.ThumbMime("image/jpeg")),
      Some(ProviderAsset.ThumbWidth(4)),
      Some(ProviderAsset.ThumbHeight(3)),
      license,
      Some(ProviderAsset.LicenseUrl("https://creativecommons.org/publicdomain/zero/1.0/")),
      Some(ProviderAsset.Author("Zq")),
      ProviderAsset.Credit(s"Zq picture $key"),
      ProviderAsset.SourceUrl(s"https://zq.example/$key"),
      None,
      kind
    )

  stest("a source's figures: one per name and bytes, listed by name, read, counted; replaced, a moved one renamed") {
    s =>
      s.source(Ck.value)
      val a = s.assets
      assertEquals(
        a.addFigures(
          Ck,
          List(
            fig(NewFigure.Name("b.jpg"), "2"),
            fig(NewFigure.Name("a.jpg"), "1"),
            fig(NewFigure.Name("again.jpg"), "1")
          )
        ),
        2
      )
      assertEquals(a.addFigures(Ck, List(fig(NewFigure.Name("a.jpg"), "3"))), 1)
      assertEquals(
        a.figures(Ck).map(f => (f.name, f.sha256.value: String)),
        List("a.jpg" -> NewFigure.sha256(figureBytes("1")), "b.jpg" -> NewFigure.sha256(figureBytes("2")))
      )
      assertEquals(a.figureCount(Ck), 2)
      assertEquals(
        a.figure(Ck, AssetService.AssetName("a.jpg")).map((m, b) => (m, b.value.toList)),
        Some(("image/jpeg", bytes("1").toList))
      )
      assertEquals(a.figure(Ck, AssetService.AssetName("nope.jpg")), None)
      assertEquals(a.replaceFigures(Ck, List(fig(NewFigure.Name("c.jpg"), "2"), fig(NewFigure.Name("a.jpg"), "4"))), 2)
      assertEquals(
        a.figures(Ck).map(f => (f.name, f.sha256.value: String)),
        List("a.jpg" -> NewFigure.sha256(figureBytes("4")), "c.jpg" -> NewFigure.sha256(figureBytes("2")))
      )
      assertEquals(
        a.sourceFigures(Ck).map(f => (f.name, f.width, f.hasThumb)),
        List(("a.jpg", Some(10), false), ("c.jpg", Some(10), false))
      )
  }

  stest(
    "a figure described and patched: kind, caption, licence, point, a period bound by bound; an inverted one refused"
  ) { s =>
    s.source(Ck.value)
    val a = s.assets
    val _ = a.addFigures(Ck, List(fig(NewFigure.Name("a.jpg"), "1")))
    assertEquals(
      a.patch(Ck, AssetService.AssetName("nope.jpg"), AssetPatch(caption = Some(MediaItem.Caption("x"))))
        .left
        .map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    val first = a.patch(
      Ck,
      AssetService.AssetName("a.jpg"),
      AssetPatch(
        kind = Some(MediaItem.Kind("plan")),
        caption = Some(MediaItem.Caption("A plan")),
        lat = Some(Latitude(40.0)),
        lon = Some(Longitude(34.5)),
        periodStart = Some(Year(-1600L))
      )
    )
    assertEquals(
      first.map(d => (d.kind, d.caption, d.lat, d.lon, d.periodStart, d.periodEnd)),
      Right((Some("plan"), Some("A plan"), Some(40.0), Some(34.5), Some(-1600L), None))
    )
    assertEquals(
      a.patch(Ck, AssetService.AssetName("a.jpg"), AssetPatch(periodEnd = Some(Year(-1200L))))
        .map(d => (d.periodStart, d.periodEnd)),
      Right((Some(-1600L), Some(-1200L)))
    )
    assert(
      a.patch(
        Ck,
        AssetService.AssetName("a.jpg"),
        AssetPatch(license = Some(MediaItem.License("CC0-1.0")), periodEnd = Some(Year(-1700L)))
      ).isLeft
    )
    assertEquals(
      a.detail(Ck, AssetService.AssetName("a.jpg")).map(d => (d.license, d.periodEnd)),
      Some((Some("CC0-1.0"), Some(-1200L)))
    )
    assertEquals(a.detail(Ck, AssetService.AssetName("nope.jpg")), None)
  }

  stest("a provider's picture: stored once under its key, its kind kept, its rights re-read, a thumbnail set") { s =>
    val a = s.assets
    val (uid, fresh) = a.save(
      picture(ProviderAsset.Key("k1"), license = ProviderAsset.License("")),
      (Target.TargetType("place"), Target.Id("zq_nowhere"))
    )
    assert(fresh)
    assertEquals(
      a.known(
        AssetService.Provider("zq-provider"),
        List(MediaItemRow.ProviderKey("k1"), MediaItemRow.ProviderKey("k2"))
      ),
      Map("k1" -> Known(Known.Uid(uid.value), None))
    )
    val (again, inserted) = a.save(
      picture(ProviderAsset.Key("k1"), kind = Some(ProviderAsset.Kind(AssetKind.Map))),
      (Target.TargetType("place"), Target.Id("zq_nowhere"))
    )
    assertEquals((again, inserted), (uid, false))
    assertEquals(
      a.show(uid).map(i => (i.kind, i.license, i.provider, i.providerKey)),
      Some((Some(AssetKind.Photo), Some("CC0-1.0"), Some("zq-provider"), Some("k1")))
    )
    a.refreshRights(
      uid,
      Rights(Rights.License("CC-BY-4.0"), None, Some(Rights.Author("Ann")), Rights.Credit("Ann, CC BY"))
    )
    assertEquals(
      a.show(uid).map(i => (i.license, i.licenseUrl, i.author, i.credit)),
      Some((Some("CC-BY-4.0"), None, Some("Ann"), Some("Ann, CC BY")))
    )
    assertEquals(a.thumb(uid).map((m, b) => (m, b.value.toList)), Some(("image/jpeg", bytes("thumb k1").toList)))
    a.setThumb(
      uid,
      ProviderAsset.Thumb(bytes("t2")),
      ProviderAsset.ThumbMime("image/png"),
      Some(ProviderAsset.ThumbWidth(2)),
      None
    )
    assertEquals(a.thumb(uid).map((m, b) => (m, b.value.toList)), Some(("image/png", bytes("t2").toList)))
    assertEquals(a.file(uid).map(_._2.value.toList), Some(bytes("k1").toList))
    assert(a.show(uid).flatMap(_.retrievedAt).exists(_.value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")))
    assertEquals(a.show(AssetService.AssetUid("00000000-0000-0000-0000-000000000000")), None)
  }

  stest("where pictures are shown: a link made once and updated, a source's picture replaced in its role, the cards") {
    s =>
      s.source(Ck.value)
      val a = s.assets
      val (p1, _) = a.save(picture(ProviderAsset.Key("p1")), (Target.TargetType("place"), Target.Id("zq_x")))
      val (p2, _) = a.save(picture(ProviderAsset.Key("p2")), (Target.TargetType("place"), Target.Id("zq_x")))
      assert(
        a.link(
          p2,
          AssetService.TargetType("place"),
          AssetService.TargetId("zq_x"),
          LocatorMapRole,
          None,
          None,
          AssetService.LinkRank(0),
          None
        )
      )
      assert(
        a.link(
          p1,
          AssetService.TargetType("place"),
          AssetService.TargetId("zq_x"),
          ImageRole,
          Some(AssetService.LinkProperty("P18")),
          Some(AssetService.LinkItem("Q1")),
          AssetService.LinkRank(1),
          None
        )
      )
      assert(
        !a.link(
          p1,
          AssetService.TargetType("place"),
          AssetService.TargetId("zq_x"),
          ImageRole,
          Some(AssetService.LinkProperty("P18")),
          Some(AssetService.LinkItem("Q1")),
          AssetService.LinkRank(0),
          Some(MediaMatch.Basis("its own picture"))
        )
      )
      assertEquals(
        a.thumbs(AssetService.TargetType("place"), AssetService.TargetId("zq_x"), AssetService.Limit(4))
          .map(t => (t.uid.value: String, t.role)),
        List(p1 -> MediaRole.Image, p2 -> MediaRole.LocatorMap)
      )
      assertEquals(
        a.thumbs(AssetService.TargetType("place"), AssetService.TargetId("zq_x"), AssetService.Limit(1))
          .map(_.uid.value: String),
        List(p1)
      )
      assertEquals(
        a.show(p1).map(_.targets.map(t => (t.targetId, t.role, t.basis))),
        Some(List(("zq_x", MediaRole.Image, Some("its own picture"))))
      )
      a.setSourcePicture(Ck, p1, CoverRole, Some(AssetService.LinkProperty("calibre")))
      a.setSourcePicture(Ck, p2, CoverRole, Some(AssetService.LinkProperty("calibre")))
      assertEquals(a.sourcePicture(Ck, CoverRole), Some((p2, "p2")))
      assertEquals(
        a.thumbs(AssetService.TargetType("source"), AssetService.TargetId(Ck.value), AssetService.Limit(4))
          .map(_.uid.value: String),
        List(p2)
      )
      assertEquals(a.placeThumbs(Place.Id("zq_nowhere"), EventMatcher.NearFinding.Km(3.0), AssetService.Limit(4)), Nil)
  }

  stest("the list, its facets and the map's pictures: by provider, licence and text; in the span") { s =>
    s.source(Ck.value)
    val a = s.assets
    val _ = a.addFigures(Ck, List(fig(NewFigure.Name("f1.jpg"), "1"), fig(NewFigure.Name("f2.jpg"), "2")))
    val (p1, _) = a.save(picture(ProviderAsset.Key("p1")), (Target.TargetType("place"), Target.Id("zq_x")))
    val _ = a.patch(
      Ck,
      AssetService.AssetName("f1.jpg"),
      AssetPatch(
        lat = Some(Latitude(40.0)),
        lon = Some(Longitude(34.0)),
        periodStart = Some(Year(-1600L)),
        periodEnd = Some(Year(-1500L))
      )
    )
    val _ = a.patch(Ck, AssetService.AssetName("f2.jpg"), AssetPatch(license = Some(MediaItem.License("CC-BY-4.0"))))
    assertEquals(
      a.list(Api.MediaQuery(provider = Some(MediaProvider.Id(MediaProvider.Source))), AssetService.Limit(50))
        .items
        .map(_.name),
      List("f1.jpg", "f2.jpg")
    )
    assertEquals(
      a.list(Api.MediaQuery(provider = Some(MediaProvider.Id("zq-provider"))), AssetService.Limit(50))
        .items
        .map(_.uid.value: String),
      List(p1)
    )
    assertEquals(
      a.list(Api.MediaQuery(license = Some(Api.MediaQuery.License("none"))), AssetService.Limit(50)).items.map(_.name),
      List("f1.jpg")
    )
    assertEquals(
      a.list(Api.MediaQuery(q = Some(Api.MediaQuery.Q("F2"))), AssetService.Limit(50)).items.map(_.name),
      List("f2.jpg")
    )
    val all = a.list(Api.MediaQuery(), AssetService.Limit(2))
    assertEquals(((all.total.value: Int), all.items.size, all.items.head.uid.value: String), (3, 2, p1))
    assertEquals(a.facets.providers.map(c => (c.key, c.n)), List((MediaProvider.Source, 2), ("zq-provider", 1)))
    assertEquals(a.facets.licences.map(c => (c.key, c.n)), List(("CC-BY-4.0", 1), ("CC0-1.0", 1), ("none", 1)))
    assertEquals(
      a.geo(Api.MediaGeoQuery(), AssetService.Limit(200)).map(h => (h.assetName, h.lat, h.periodStart, h.periodEnd)),
      List(("f1.jpg", 40.0, Some(-1600L), Some(-1500L)))
    )
    assertEquals(
      a.geo(
        Api.MediaGeoQuery(from = Some(Api.MediaGeoQuery.From(-1550L)), to = Some(Api.MediaGeoQuery.To(-1540L))),
        AssetService.Limit(200)
      ).size,
      1
    )
    assertEquals(
      a.geo(
        Api.MediaGeoQuery(from = Some(Api.MediaGeoQuery.From(-1400L)), to = Some(Api.MediaGeoQuery.To(-1300L))),
        AssetService.Limit(200)
      ),
      Nil
    )
    assertEquals(a.geo(Api.MediaGeoQuery(kind = Some(MediaItem.Kind("map"))), AssetService.Limit(200)), Nil)
    assertEquals(a.geoOf(List(p1)), Map.empty)
  }

class AssetServiceInMemorySuite extends AssetServiceContract with MediaStoreSuite.InMemory
class AssetServiceDbSuite extends AssetServiceContract with MediaStoreSuite.OnDatabase
