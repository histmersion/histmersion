package com.histmersion
package plugins.media
import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.plugin.*
import com.histmersion.pluginhost.HostLicences

/** The collections' parsers (#1446): each API's answer read into a [[MediaItem]] — the licence as the record itself
  * states it, the place fields most specific first, the dates as signed years. Fixtures are trimmed real answers (Met,
  * Cleveland, Chicago, Commons, 2026-10-17); loc.gov answered with a bot challenge, so its fixtures follow the
  * documented JSON shape. The host reads every stated licence ([[HostLicences]]): what it reads is checked too.
  */
class CollectionParsersSuite extends munit.FunSuite:

  private def fixture(name: String): Json =
    RawJson
      .parse(scala.io.Source.fromResource(s"media/$name")(using scala.io.Codec.UTF8).mkString)
      .fold(e => fail(e.toString), identity)

  private val Cc0Url = "https://creativecommons.org/publicdomain/zero/1.0/"

  // ── the Met ──────────────────────────────────────────────────────────────────────────────────

  test(
    "Met: v1.1 search URL (geoLocation and q both the name, paged by limit); ids; a retired endpoint's message is an error"
  ):
    val u = MetCollection.searchUrl(
      CollectionSettings.ApiUrl("https://collectionapi.metmuseum.org/public/collection/"),
      MediaPlace.Label("Tell el-Amarna"),
      CollectionSettings.Candidates(20)
    )
    assertEquals(
      u,
      "https://collectionapi.metmuseum.org/public/collection/v1.1/search?hasImages=true" +
        "&geoLocation=Tell+el-Amarna&q=Tell+el-Amarna&limit=20"
    )
    assertEquals(
      MetCollection.ids(fixture("met-search.json")),
      Right(List(252448L, 248284L, 252447L, 252446L, 253574L))
    )
    assertEquals(MetCollection.ids(Json.obj("total" -> Json.fromInt(0), "objectIDs" -> Json.Null)), Right(Nil))
    assert(MetCollection.ids(RawJson.parse("""{"message":"retired"}""").toOption.get).isLeft)

  test(
    "Met: an object's licence from isPublicDomain, astronomical years moved one back, place fields most specific first"
  ):
    val r = MetCollection.record(fixture("met-object.json")).fold(e => fail(e.sentence), identity)
    assertEquals(r.key, "252448")
    assertEquals(r.period, Period.span(-1400L, -1201L), "-1399..-1200 astronomical = 1400–1201 BCE")
    assertEquals(r.geography, List("excavation" -> "Mycenae", "country" -> "Greece"))
    assertEquals(r.imageUrl, Some("https://images.metmuseum.org/CRDImages/gr/web-large/DP121403.jpg"))
    assertEquals(r.originalUrl, Some("https://images.metmuseum.org/CRDImages/gr/original/DP121403.jpg"))
    assertEquals((r.role, r.rendition), (None, None), "the collection's role; the host measures the file")
    val rights = r.rights.fold(e => fail(e.sentence), identity)
    assertEquals((rights.licence, rights.licenceUrl), (Licence("CC0-1.0"), Some(Cc0Url)))
    assertEquals(
      HostLicences.readName(LicenceReader.ReadNameName(rights.licence.value)),
      Licence.Read(Licence.Read.Id("CC0-1.0"), Licence.Read.Family(Licence.Read.Cc0))
    )
    assertEquals(rights.credit, "Terracotta animal figurine, The Metropolitan Museum of Art, CC0")
    val closed = fixture("met-object.json").mapObject(_.updated("isPublicDomain", Json.False))
    assert(MetCollection.record(closed).toOption.get.rights.isLeft, "not public domain: refused, never assumed")
    assertEquals(MetCollection.year(MetCollection.Year(0)), -1L)
    assertEquals(MetCollection.year(MetCollection.Year(1200)), 1200L)

  // ── Cleveland ────────────────────────────────────────────────────────────────────────────────

  test(
    "Cleveland: records from the search, CC0 from share_license_status, find_spot before culture, web rendition stored"
  ):
    val rs =
      ClevelandCollection
        .records(fixture("cma-search.json"))
        .fold(e => fail(e.sentence), identity)
        .map(_.fold(e => fail(e.sentence), identity))
    assertEquals(rs.size, 4)
    val earring = rs.head
    assertEquals(earring.key, "1916.131")
    assertEquals(earring.geography, List("culture" -> "Cyprus"))
    assertEquals(earring.period, Period.span(-499L, -400L))
    assertEquals(earring.imageUrl, Some("https://openaccess-cdn.clevelandart.org/1916.131/1916.131_web.jpg"))
    assertEquals(
      earring.rights.map(_.credit),
      Right("Earring, The Cleveland Museum of Art, Gift of Mr. and Mrs. J. H. Wade, CC0")
    )
    assertEquals(earring.rights.map(_.licence), Right(Licence("CC0-1.0")))
    val copyrighted =
      Json.obj("accession_number" -> Json.fromString("x"), "share_license_status" -> Json.fromString("Copyrighted"))
    assert(ClevelandCollection.record(copyrighted).toOption.get.rights.isLeft)
    assert(
      ClevelandCollection
        .searchUrl(
          Plugin.Http.Url("https://openaccess-api.clevelandart.org/api"),
          CollectionQuery.Text("Cyprus"),
          CollectionSettings.Candidates(20)
        )
        .value
        .startsWith("https://openaccess-api.clevelandart.org/api/artworks/?q=Cyprus&cc0=1&has_image=1&limit=20&fields=")
    )

  // ── Chicago ──────────────────────────────────────────────────────────────────────────────────

  test("Chicago: is_public_domain decides; IIIF renditions at the display and thumbnail widths"):
    val rs = ArtInstituteCollection
      .records(
        fixture("aic-search.json"),
        Plugin.Http.Url("https://www.artic.edu/iiif/2"),
        CollectionSettings.DisplayPx(843),
        ArtInstituteCollection.ThumbPx(330)
      )
      .fold(e => fail(e.sentence), identity)
      .map(_.fold(e => fail(e.sentence), identity))
    val coin = rs.head
    assertEquals(coin.key, "4441")
    assertEquals(coin.geography, List("place_of_origin" -> "Cyprus"))
    assertEquals(coin.period, Period.span(-65L, -64L))
    assertEquals(
      coin.imageUrl,
      Some("https://www.artic.edu/iiif/2/96ab8413-59a3-e92c-314a-669173745abd/full/843,/0/default.jpg")
    )
    assertEquals(
      coin.thumbUrl,
      Some("https://www.artic.edu/iiif/2/96ab8413-59a3-e92c-314a-669173745abd/full/330,/0/default.jpg")
    )
    assertEquals(coin.pageUrl, "https://www.artic.edu/artworks/4441")
    assert(coin.rights.isRight)
    assertEquals(
      coin.rights.map(r => HostLicences.readName(LicenceReader.ReadNameName(r.licence.value)).family),
      Right(Licence.Read.Cc0)
    )
    assert(rs(1).rights.isLeft, "the fixture's second work is marked not public domain")

  // ── the Library of Congress ──────────────────────────────────────────────────────────────────

  test("LoC: item ids of a search; a map is public domain only with 'No known restrictions' AND a date by max-year"):
    assertEquals(LocCollection.results(fixture("loc-search.json")), Right(List("2007627513", "2010593001")))
    assertEquals(LocCollection.itemId(Plugin.Http.Url("http://www.loc.gov/item/2007627513/")), Some("2007627513"))
    val r =
      LocCollection
        .record(
          fixture("loc-item.json"),
          CollectionSettings.ApiUrl("https://www.loc.gov"),
          CollectionSettings.MaxYear(1928),
          CollectionSettings.DisplayPx(1280),
          MediaSettings.ThumbPx(330)
        )
        .fold(e => fail(e.sentence), identity)
    assertEquals(r.key, "2007627513")
    assertEquals(r.period, Period.span(1880L, 1880L))
    assertEquals(r.imageUrl.map(_.value.split('/').last), Some("1024.jpg"), "the largest JPEG no wider than display-px")
    assertEquals(
      r.thumbUrl.map(_.value.split('/').last),
      Some("1024.jpg"),
      "the smallest JPEG no narrower than thumb-px"
    )
    assertEquals(r.originalUrl.map(_.value.split('/').last), Some("4000.jpg"))
    assertEquals(r.rights.map(_.licence), Right(Licence("public-domain")))
    assertEquals(
      r.rights.map(rr => HostLicences.readName(LicenceReader.ReadNameName(rr.licence.value)).family),
      Right(Licence.Read.PublicDomain)
    )
    assertEquals(
      r.rights.map(_.credit),
      Right("Plan of Mycenae, Steffen, Bernhard, Library of Congress, Geography and Map Division")
    )
    assertEquals(r.geography.map(_._2), List("mycenae", "greece"))
    val late = LocCollection
      .record(
        fixture("loc-item.json"),
        CollectionSettings.ApiUrl("https://www.loc.gov"),
        CollectionSettings.MaxYear(1870),
        CollectionSettings.DisplayPx(1280),
        MediaSettings.ThumbPx(330)
      )
      .toOption
      .get
    assert(late.rights.isLeft, "after max-year: may be in copyright")
    val restricted = RawJson
      .parse(
        fixture("loc-item.json").noSpaces
          .replace("No known restrictions on publication.", "Rights status not evaluated.")
      )
      .toOption
      .get
    assert(
      LocCollection
        .record(
          restricted,
          CollectionSettings.ApiUrl("https://www.loc.gov"),
          CollectionSettings.MaxYear(1928),
          CollectionSettings.DisplayPx(1280),
          MediaSettings.ThumbPx(330)
        )
        .toOption
        .get
        .rights
        .isLeft
    )
    assertEquals(LocCollection.yearsOf(LocCollection.Date("[ca. 1906-1910]")), Period.span(1906L, 1910L))
    assertEquals(LocCollection.yearsOf(LocCollection.Date("undated")), None)

  // ── depictions ───────────────────────────────────────────────────────────────────────────────

  test("Commons depictions: haswbstatement search URL and its titles; P1299 items preferred first, deprecated dropped"):
    val u = Commons.Depicts.searchUrl(
      Commons.Settings.ApiUrl("https://commons.wikimedia.org/w/api.php"),
      WikidataId("Q203729"),
      DepictsSettings.SearchLimit(10),
      Commons.Settings.Maxlag(5)
    )
    assertEquals(
      u,
      "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2&list=search" +
        "&srnamespace=6&srlimit=10&srsearch=haswbstatement%3AP180%3DQ203729&maxlag=5"
    )
    val ts = Commons.Depicts.titles(fixture("commons-depicts.json")).fold(e => fail(e.sentence), identity)
    assertEquals(ts.size, 3)
    assert(ts.head.value.startsWith("File:Museum Hattusa"))
    assertEquals(
      Commons.Depicts.depictedBy(fixture("wd-depicted-by.json")),
      Map("Q203729" -> List("Q5002", "Q5001"), "Q404" -> Nil)
    )

  // ── deep time ────────────────────────────────────────────────────────────────────────────────

  test("the span of two years in either order; a year no Year holds makes none; range takes the one there is"):
    assertEquals(Period.span(-1100L, -1200L), Period.span(-1200L, -1100L))
    assertEquals(Period.span(-1200L, -1100L).map(q => (q.start.value: Int, q.end.value: Int)), Some((-1200, -1100)))
    assertEquals(Period.span(5L, 5L).map(_.isPoint), Some(true))
    assertEquals(Period.span(-1L, Int.MaxValue.toLong + 1), None)
    assertEquals(Period.range(Some(-5L), None), Period.span(-5L, -5L))
    assertEquals(Period.range(None, None), None)
