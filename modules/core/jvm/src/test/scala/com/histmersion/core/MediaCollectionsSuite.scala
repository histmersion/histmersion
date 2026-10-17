package com.histmersion.core

import io.circe.Json
import io.circe.parser.parse

/** The pure half of the collections (#1446): each API's answer read into a [[MediaRecord]] — the
  * licence from the record itself, the place fields most specific first, the dates as signed years —
  * and the match rules that tie a record to a place. Fixtures are trimmed real answers (Met, Cleveland,
  * Chicago, Commons, 2026-10-17); loc.gov answered with a bot challenge, so its fixtures follow the
  * documented JSON shape. */
class MediaCollectionsSuite extends munit.FunSuite:

  private def fixture(name: String): Json =
    parse(scala.io.Source.fromResource(s"media/$name")(using scala.io.Codec.UTF8).mkString).fold(e => fail(e.toString), identity)

  // ── the Met ──────────────────────────────────────────────────────────────────────────────────

  test("Met: v1.1 search URL (geoLocation and q both the name, paged by limit); ids; a retired endpoint's message is an error"):
    val u = MetMuseum.searchUrl("https://collectionapi.metmuseum.org/public/collection/", "Tell el-Amarna", 20)
    assertEquals(u, "https://collectionapi.metmuseum.org/public/collection/v1.1/search?hasImages=true" +
      "&geoLocation=Tell+el-Amarna&q=Tell+el-Amarna&limit=20")
    assertEquals(MetMuseum.ids(fixture("met-search.json")), Right(List(252448L, 248284L, 252447L, 252446L, 253574L)))
    assertEquals(MetMuseum.ids(Json.obj("total" -> Json.fromInt(0), "objectIDs" -> Json.Null)), Right(Nil))
    assert(MetMuseum.ids(parse("""{"message":"retired"}""").toOption.get).isLeft)

  test("Met: an object's licence from isPublicDomain, astronomical years moved one back, place fields most specific first"):
    val r = MetMuseum.record(fixture("met-object.json")).fold(fail(_), identity)
    assertEquals(r.key, "252448")
    assertEquals(r.period, Some((-1400L, -1201L)), "-1399..-1200 astronomical = 1400–1201 BCE")
    assertEquals(r.geography, List("excavation" -> "Mycenae", "country" -> "Greece"))
    assertEquals(r.imageUrl, Some("https://images.metmuseum.org/CRDImages/gr/web-large/DP121403.jpg"))
    assertEquals(r.originalUrl, Some("https://images.metmuseum.org/CRDImages/gr/original/DP121403.jpg"))
    val rights = r.rights.fold(fail(_), identity)
    assertEquals(rights.licence, MediaLicence.Licence("CC0-1.0", MediaLicence.Cc0))
    assertEquals(rights.credit, "Terracotta animal figurine, The Metropolitan Museum of Art, CC0")
    val closed = fixture("met-object.json").mapObject(_.add("isPublicDomain", Json.False))
    assert(MetMuseum.record(closed).toOption.get.rights.isLeft, "not public domain: refused, never assumed")
    assertEquals(MetMuseum.year(0), -1L)
    assertEquals(MetMuseum.year(1200), 1200L)

  // ── Cleveland ────────────────────────────────────────────────────────────────────────────────

  test("Cleveland: records from the search, CC0 from share_license_status, find_spot before culture, web rendition stored"):
    val rs = ClevelandArt.records(fixture("cma-search.json")).fold(fail(_), identity).map(_.fold(fail(_), identity))
    assertEquals(rs.size, 4)
    val earring = rs.head
    assertEquals(earring.key, "1916.131")
    assertEquals(earring.geography, List("culture" -> "Cyprus"))
    assertEquals(earring.period, Some((-499L, -400L)))
    assertEquals(earring.imageUrl, Some("https://openaccess-cdn.clevelandart.org/1916.131/1916.131_web.jpg"))
    assertEquals(earring.rights.map(_.credit),
      Right("Earring, The Cleveland Museum of Art, Gift of Mr. and Mrs. J. H. Wade, CC0"))
    val copyrighted = Json.obj("accession_number" -> Json.fromString("x"), "share_license_status" -> Json.fromString("Copyrighted"))
    assert(ClevelandArt.record(copyrighted).toOption.get.rights.isLeft)
    assert(ClevelandArt.searchUrl("https://openaccess-api.clevelandart.org/api", "Cyprus", 20)
      .startsWith("https://openaccess-api.clevelandart.org/api/artworks/?q=Cyprus&cc0=1&has_image=1&limit=20&fields="))

  // ── Chicago ──────────────────────────────────────────────────────────────────────────────────

  test("Chicago: is_public_domain decides; IIIF renditions at the display and thumbnail widths"):
    val rs = ArtInstitute.records(fixture("aic-search.json"), "https://www.artic.edu/iiif/2", 843, 330)
      .fold(fail(_), identity).map(_.fold(fail(_), identity))
    val coin = rs.head
    assertEquals(coin.key, "4441")
    assertEquals(coin.geography, List("place_of_origin" -> "Cyprus"))
    assertEquals(coin.period, Some((-65L, -64L)))
    assertEquals(coin.imageUrl, Some("https://www.artic.edu/iiif/2/96ab8413-59a3-e92c-314a-669173745abd/full/843,/0/default.jpg"))
    assertEquals(coin.thumbUrl, Some("https://www.artic.edu/iiif/2/96ab8413-59a3-e92c-314a-669173745abd/full/330,/0/default.jpg"))
    assertEquals(coin.pageUrl, "https://www.artic.edu/artworks/4441")
    assert(coin.rights.isRight)
    assert(rs(1).rights.isLeft, "the fixture's second work is marked not public domain")

  // ── the Library of Congress ──────────────────────────────────────────────────────────────────

  test("LoC: item ids of a search; a map is public domain only with 'No known restrictions' AND a date by max-year"):
    assertEquals(LibraryOfCongress.results(fixture("loc-search.json")), Right(List("2007627513", "2010593001")))
    assertEquals(LibraryOfCongress.itemId("http://www.loc.gov/item/2007627513/"), Some("2007627513"))
    val r = LibraryOfCongress.record(fixture("loc-item.json"), "https://www.loc.gov", 1928, 1280, 330).fold(fail(_), identity)
    assertEquals(r.key, "2007627513")
    assertEquals(r.period, Some((1880L, 1880L)))
    assertEquals(r.imageUrl.map(_.split('/').last), Some("1024.jpg"), "the largest JPEG no wider than display-px")
    assertEquals(r.thumbUrl.map(_.split('/').last), Some("1024.jpg"), "the smallest JPEG no narrower than thumb-px")
    assertEquals(r.originalUrl.map(_.split('/').last), Some("4000.jpg"))
    assertEquals(r.rights.map(_.licence.family), Right(MediaLicence.PublicDomain))
    assertEquals(r.rights.map(_.credit), Right("Plan of Mycenae, Steffen, Bernhard, Library of Congress, Geography and Map Division"))
    assertEquals(r.geography.map(_._2), List("mycenae", "greece"))
    val late = LibraryOfCongress.record(fixture("loc-item.json"), "https://www.loc.gov", 1870, 1280, 330).toOption.get
    assert(late.rights.isLeft, "after max-year: may be in copyright")
    val restricted = parse(fixture("loc-item.json").noSpaces.replace("No known restrictions on publication.", "Rights status not evaluated."))
      .toOption.get
    assert(LibraryOfCongress.record(restricted, "https://www.loc.gov", 1928, 1280, 330).toOption.get.rights.isLeft)
    assertEquals(LibraryOfCongress.yearsOf("[ca. 1906-1910]"), Some((1906L, 1910L)))
    assertEquals(LibraryOfCongress.yearsOf("undated"), None)

  // ── depictions ───────────────────────────────────────────────────────────────────────────────

  test("Commons depictions: haswbstatement search URL and its titles; P1299 items preferred first, deprecated dropped"):
    val u = CommonsDepicts.searchUrl("https://commons.wikimedia.org/w/api.php", "Q203729", 10, 5)
    assertEquals(u, "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2&list=search" +
      "&srnamespace=6&srlimit=10&srsearch=haswbstatement%3AP180%3DQ203729&maxlag=5")
    val ts = CommonsDepicts.titles(fixture("commons-depicts.json")).fold(fail(_), identity)
    assertEquals(ts.size, 3)
    assert(ts.head.startsWith("File:Museum Hattusa"))
    assertEquals(CommonsDepicts.depictedBy(fixture("wd-depicted-by.json")), Map("Q203729" -> List("Q5002", "Q5001"), "Q404" -> Nil))

  // ── matching ─────────────────────────────────────────────────────────────────────────────────

  private def rec(geo: List[(String, String)], period: Option[(Long, Long)] = Some((-1400L, -1200L))) =
    MediaRecord("k", "t", "https://x", Some("https://x/i.jpg"), None, None,
      Right(MediaRights.cc0(None, "t")), geo, period)

  private val mycenae = MediaMatch.PlaceInfo("pleiades_570491", "Mycenae", Set("mycenae", "mykenai"), Some((-1600L, -1100L)))
  private val greece  = MediaMatch.PlaceInfo("geonames_390903", "Greece", Set("greece"), Some((-3000L, -100L)))
  private val onMap: MediaMatch.Resolve = Map(
    "mycenae" -> Set("pleiades_570491"), "mykenai" -> Set("pleiades_570491"), "greece" -> Set("geonames_390903"),
    "thebes" -> Set("thebes_egypt", "thebes_greece")).withDefaultValue(Set.empty)

  test("museum rule: the most specific place field that names a place on the map decides, by name key, with the dates"):
    val r = rec(List("excavation" -> "Mykénai", "country" -> "Greece"))
    assertEquals(MediaMatch.museum(r, mycenae, onMap), Right("excavation = Mykénai; 1400–1200 BCE within 1600–1100 BCE"))
    assertEquals(MediaMatch.museum(r, greece, onMap), Left("names another place on the map"),
      "an object from Mycenae is not filed under Greece too")
    assertEquals(MediaMatch.museum(rec(List("city" -> "Atlantis", "country" -> "Greece")), greece, onMap).map(_.take(17)),
      Right("country = Greece;"), "a field that names nothing on the map is passed over")

  test("museum rule: uncertain and ambiguous values, missing or disjoint dates are never matched"):
    assertEquals(MediaMatch.museum(rec(List("culture" -> "Mycenae ?")), mycenae, onMap), Left("no place on the record"))
    assertEquals(MediaMatch.museum(rec(List("culture" -> "probably Mycenae")), mycenae, onMap), Left("no place on the record"))
    val thebes = MediaMatch.PlaceInfo("thebes_greece", "Thebes", Set("thebes"), Some((-1600L, -1100L)))
    assertEquals(MediaMatch.museum(rec(List("city" -> "Thebes")), thebes, onMap), Left("its place name is ambiguous on the map"))
    assertEquals(MediaMatch.museum(rec(List("excavation" -> "Mycenae"), None), mycenae, onMap), Left("no dates on the record"))
    assertEquals(MediaMatch.museum(rec(List("excavation" -> "Mycenae")), mycenae.copy(period = None), onMap),
      Left("the place has no period to compare"))
    assertEquals(MediaMatch.museum(rec(List("excavation" -> "Mycenae"), Some((1700L, 1750L))), mycenae, onMap),
      Left("its dates do not overlap the place's"))
    assertEquals(MediaMatch.museum(rec(List("excavation" -> "Mycenae, Tomb 5")), mycenae, onMap),
      Left("its place is not on the map"), "whole value against whole name: never a substring")

  test("map rule: any location naming the place alone, made by max-year"):
    val m = rec(List("location" -> "greece", "location" -> "mycenae"), Some((1880L, 1880L)))
    assertEquals(MediaMatch.map(m, mycenae, onMap, 1928), Right("location = mycenae; map of 1880–1880 CE"))
    assertEquals(MediaMatch.map(m, greece, onMap, 1928), Right("location = greece; map of 1880–1880 CE"))
    assertEquals(MediaMatch.map(m.copy(period = Some((1950L, 1950L))), greece, onMap, 1928), Left("made after 1928"))
    assertEquals(MediaMatch.map(rec(List("location" -> "thebes"), Some((1880L, 1880L))),
      MediaMatch.PlaceInfo("thebes_egypt", "Thebes", Set("thebes"), None), onMap, 1928), Left("its place name is ambiguous on the map"))

  test("year ranges read as a person reads them; overlap is inclusive"):
    assertEquals(MediaMatch.years((-200L, 100L)), "200 BCE–100 CE")
    assert(MediaMatch.overlaps((-1200L, -1100L), (-1100L, -1000L)))
    assert(!MediaMatch.overlaps((-1200L, -1101L), (-1100L, -1000L)))
