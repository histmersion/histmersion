package com.histmersion.domain

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import io.circe.parser.parse as json
import com.histmersion.values.MediaRole

/** The pure half of the media providers (#1446): licences, credit lines, and the Wikidata and
  * Commons answers — no network, no database. */
class MediaProvidersSuite extends munit.FunSuite:

  import MediaLicence.*

  // ── licences ─────────────────────────────────────────────────────────────────────────────────

  test("Commons' machine ids and short names read as SPDX ids and families"):
    assertEquals(classify("cc-by-sa-3.0"), Licence("CC-BY-SA-3.0", CcBySa))
    assertEquals(classify("CC BY-SA 3.0"), Licence("CC-BY-SA-3.0", CcBySa))
    assertEquals(classify("cc-by-2.0"), Licence("CC-BY-2.0", CcBy))
    assertEquals(classify("CC BY 4.0"), Licence("CC-BY-4.0", CcBy))
    assertEquals(classify("cc-by-sa-3.0-de"), Licence("CC-BY-SA-3.0-DE", CcBySa))
    assertEquals(classify("cc-by-nc-sa-4.0"), Licence("CC-BY-NC-SA-4.0", CcByNcSa))
    assertEquals(classify("cc-by-nc-4.0"), Licence("CC-BY-NC-4.0", CcByNc))
    assertEquals(classify("cc-by-nd-4.0"), Licence("CC-BY-ND-4.0", CcByNd))
    assertEquals(classify("cc-by-sa-4"), Licence("CC-BY-SA-4.0", CcBySa))
    assertEquals(classify("cc0"), Licence("CC0-1.0", Cc0))
    assertEquals(classify("CC0 1.0"), Licence("CC0-1.0", Cc0))
    assertEquals(classify("pd"), Licence("public-domain", PublicDomain))
    assertEquals(classify("Public domain"), Licence("public-domain", PublicDomain))
    assertEquals(classify("PD-old-70"), Licence("public-domain", PublicDomain))
    assertEquals(classify("GFDL").family, Gfdl)
    assertEquals(classify("Copyrighted").family, AllRights)
    assertEquals(classify("Attribution only license").family, Unknown)
    assertEquals(classify("").family, Unknown)

  test("parse takes the machine id first, then the short name; neither read is unknown"):
    assertEquals(MediaLicence.parse(Some("cc-by-sa-4.0"), Some("CC BY 2.0")).id, "CC-BY-SA-4.0")
    assertEquals(MediaLicence.parse(Some("something-odd"), Some("CC BY 2.0")).id, "CC-BY-2.0")
    assertEquals(MediaLicence.parse(None, None), Licence("", Unknown))
    assertEquals(MediaLicence.parse(Some("weird"), None), Licence("weird", Unknown))

  test("the allowlist keeps known families only, and never all-rights-reserved or unknown"):
    assertEquals(allowList("cc0, public-domain,CC-BY ,cc-by-sa"), Set(Cc0, PublicDomain, CcBy, CcBySa))
    assertEquals(allowList("cc-by,all-rights-reserved,unknown,bogus"), Set(CcBy))
    assertEquals(allowList(""), Set.empty[String])
    val default = allowList("cc0,public-domain,cc-by,cc-by-sa")
    assert(allowed(classify("cc-by-sa-4.0"), default))
    assert(!allowed(classify("cc-by-nc-4.0"), default), "non-commercial is off by default")
    assert(allowed(classify("cc-by-nc-4.0"), default + CcByNc), "…and on when the provider lists it")
    assert(!allowed(Licence(AllRights, AllRights), Set(AllRights)), "all rights reserved: never")
    assert(!allowed(Licence("x", Unknown), Set(Unknown)), "unknown: never")

  test("which families need the author named"):
    assert(needsAttribution(CcBy) && needsAttribution(CcBySa) && needsAttribution(CcByNcSa) && needsAttribution(Gfdl))
    assert(!needsAttribution(Cc0) && !needsAttribution(PublicDomain))

  // ── text ─────────────────────────────────────────────────────────────────────────────────────

  test("plain text out of Commons' HTML author fields"):
    assertEquals(MediaText.plain("""<a href="//commons.wikimedia.org/wiki/User:X" title="User:X">David  Monniaux</a>"""),
      "David Monniaux")
    assertEquals(MediaText.plain("Tom &amp; Jerry &#233;&#x20AC; &nbsp;x"), "Tom & Jerry é€ x")
    assertEquals(MediaText.plain("a" * 400, max = 10).length, 10)
    assertEquals(MediaText.plain(null), "")

  test("tracking parameters are stripped from file URLs, the rest kept"):
    assertEquals(MediaText.stripTracking("https://u/x.jpg?utm_source=a&utm_campaign=b"), "https://u/x.jpg")
    assertEquals(MediaText.stripTracking("https://u/x.jpg?page=2&utm_source=a"), "https://u/x.jpg?page=2")
    assertEquals(MediaText.stripTracking("https://u/x.jpg"), "https://u/x.jpg")

  test("a provider file title becomes a link-safe name"):
    assertEquals(MediaText.safeName("File:Lions Gate (Mycenae).jpg"), "Lions_Gate_Mycenae_.jpg")
    assertEquals(MediaText.safeName("File:"), "file")

  // ── roles and counts (shared) ────────────────────────────────────────────────────────────────

  test("the statement → role map parses, dropping malformed and repeated entries"):
    assertEquals(MediaRoles.parse("P18:image, P242:locator-map,bogus,P1:nope,P18:plan,P3311:plan"),
      List("P18" -> "image", "P242" -> "locator-map", "P3311" -> "plan"))
    assertEquals(MediaRoles.parse(""), Nil)

  test("the card's role order puts the item's own image first, unknown roles last"):
    assert(MediaRole.order(MediaRole.Image) < MediaRole.order(MediaRole.LocatorMap))
    assertEquals(MediaRole.order("other"), MediaRole.all.size)

  test("counts: most frequent first, then by key, with the allowed flag"):
    assertEquals(MediaCount.of(List("b", "a", "b", "c"), _ != "c"),
      List(MediaCount("b", 2), MediaCount("a", 1), MediaCount("c", 1, allowed = false)))

  // ── Wikidata ─────────────────────────────────────────────────────────────────────────────────

  private val entities = json("""{"entities":{
    "Q1":{"id":"Q1","claims":{
      "P18":[
        {"mainsnak":{"snaktype":"value","datavalue":{"value":"B.jpg"}},"rank":"normal"},
        {"mainsnak":{"snaktype":"value","datavalue":{"value":"A.jpg"}},"rank":"preferred"},
        {"mainsnak":{"snaktype":"value","datavalue":{"value":"Old.jpg"}},"rank":"deprecated"}],
      "P242":[{"mainsnak":{"snaktype":"somevalue"},"rank":"normal"},
              {"mainsnak":{"snaktype":"value","datavalue":{"value":"Map.svg"}},"rank":"normal"}],
      "P31":[{"mainsnak":{"snaktype":"value","datavalue":{"value":{"id":"Q5"}}},"rank":"normal"}],
      "P373":[{"mainsnak":{"snaktype":"value","datavalue":{"value":"Mycenae"}},"rank":"normal"}]}},
    "Q2":{"id":"Q2","missing":""}}}""").toOption.get

  test("wbgetentities: the picture statements kept, deprecated and no-value ones dropped, P373 read"):
    val items = Wikidata.items(entities, Set("P18", "P242")).toOption.get
    assertEquals(items.keySet, Set("Q1"), "a missing item is left out")
    val q1 = items("Q1")
    assertEquals(q1.statements.map(s => (s.property, s.file, s.preferred)),
      List(("P18", "B.jpg", false), ("P18", "A.jpg", true), ("P242", "Map.svg", false)))
    assertEquals(q1.commonsCategory, Some("Mycenae"))

  test("best: the preferred statements when there are any, else the normal ones, capped"):
    val st = Wikidata.items(entities, Set("P18")).toOption.get("Q1").statements
    assertEquals(Wikidata.best(st, 2).map(_.file), List("A.jpg"))
    val normals = st.map(_.copy(preferred = false))
    assertEquals(Wikidata.best(normals, 1).map(_.file), List("B.jpg"))
    assertEquals(Wikidata.best(normals, 0), Nil)

  test("an API error (maxlag) is a Left with its code"):
    val err = json("""{"error":{"code":"maxlag","info":"Waiting for wdqs1013: 11 seconds lagged."}}""").toOption.get
    assertEquals(Wikidata.items(err, Set("P18")).left.toOption.map(_.takeWhile(_ != ':')), Some("maxlag"))

  test("a gazetteer id that IS a Wikidata item names it"):
    assertEquals(Wikidata.qidOfPlaceId("wikidata_q132564"), Some("Q132564"))
    assertEquals(Wikidata.qidOfPlaceId("pleiades_570491"), None)
    assertEquals(Wikidata.qidOfPlaceId("wikidata_qabc"), None)
    assert(Wikidata.isQid("Q1") && !Wikidata.isQid("Q01") && !Wikidata.isQid("P18"))

  test("the wbgetentities URL: ids joined and encoded, maxlag only when set"):
    val u = Wikidata.entitiesUrl("https://wd/w/api.php", List("Q1", "Q2"), 5)
    assert(u.startsWith("https://wd/w/api.php?action=wbgetentities&format=json&formatversion=2&props=claims&ids=Q1%7CQ2"), u)
    assert(u.endsWith("&maxlag=5"), u)
    assert(!Wikidata.entitiesUrl("https://wd/w/api.php", List("Q1"), 0).contains("maxlag"))

  // ── Commons ──────────────────────────────────────────────────────────────────────────────────

  test("a Wikidata file name becomes its Commons title"):
    assertEquals(Commons.fileTitle("lions_Gate.jpg"), "File:Lions Gate.jpg")
    assertEquals(Commons.fileTitle("File:Mycenae  ruins.jpg"), "File:Mycenae ruins.jpg")
    assertEquals(Commons.fileTitle("  "), "")

  test("the imageinfo URL asks for the rendition width and, with meta, only the licence fields"):
    val u = Commons.imageInfoUrl("https://c/w/api.php", List("File:A b.jpg"), 1280, 5, meta = true)
    assert(u.contains("iiurlwidth=1280") && u.contains("iiextmetadatafilter=LicenseShortName%7CLicense%7C"), u)
    assert(u.contains("titles=File%3AA+b.jpg") && u.endsWith("&maxlag=5"), u)
    val t = Commons.imageInfoUrl("https://c/w/api.php", List("File:A.jpg"), 330, 0, meta = false)
    assert(!t.contains("extmetadata") && !t.contains("maxlag"), t)

  private val imageinfo = json("""{"query":{
    "normalized":[{"from":"File:a.jpg","to":"File:A.jpg"}],
    "pages":[
      {"title":"File:Gone.jpg","missing":true},
      {"title":"File:A.jpg","imageinfo":[{"size":5,"width":3000,"height":2000,
        "thumburl":"https://up/t/1280px-A.jpg?utm_source=x","thumbwidth":1280,"thumbheight":853,
        "url":"https://up/A.jpg?utm_source=x","descriptionurl":"https://commons/wiki/File:A.jpg","mime":"image/jpeg",
        "extmetadata":{"License":{"value":"cc-by-sa-4.0"},"Artist":{"value":"<b>Ann</b>"},"Flag":{"value":true}}}]}]}}""")
    .toOption.get

  test("imageinfo: files by the title asked (normalised too), missing files as Left, URLs untracked"):
    val m = Commons.imageInfo(imageinfo).toOption.get
    assertEquals(m("File:Gone.jpg"), Left("missing on Commons"))
    val a = m("File:A.jpg").toOption.get
    assertEquals(m("File:a.jpg"), m("File:A.jpg"), "the asked title maps to the canonical one")
    assertEquals(a.originalUrl, "https://up/A.jpg")
    assertEquals(a.renditionUrl, Some("https://up/t/1280px-A.jpg"))
    assertEquals((a.renditionWidth, a.renditionHeight, a.width), (Some(1280), Some(853), Some(3000)))
    assertEquals(a.meta.get("License"), Some("cc-by-sa-4.0"))
    assertEquals(a.meta.get("Flag"), Some("true"))

  test("rights: licence, author and Commons' credit form read from extmetadata"):
    val r = Commons.rights(Map("License" -> "cc-by-sa-3.0", "LicenseShortName" -> "CC BY-SA 3.0",
      "Artist" -> """<a href="//commons.wikimedia.org/wiki/User:D">David Monniaux</a>""",
      "LicenseUrl" -> "//creativecommons.org/licenses/by-sa/3.0/", "AttributionRequired" -> "true")).toOption.get
    assertEquals(r.licence, Licence("CC-BY-SA-3.0", CcBySa))
    assertEquals(r.credit, "David Monniaux, CC BY-SA 3.0, via Wikimedia Commons")
    assertEquals(r.licenceUrl, Some("https://creativecommons.org/licenses/by-sa/3.0/"))
    assertEquals(r.author, Some("David Monniaux"))

  test("rights: a custom Attribution wins over Artist"):
    val r = Commons.rights(Map("License" -> "cc-by-4.0", "Artist" -> "user42", "Attribution" -> "Photo: Jane Roe"))
    assertEquals(r.map(_.credit), Right("Photo: Jane Roe, CC-BY-4.0, via Wikimedia Commons"))

  test("rights: public domain with no author credits Commons alone"):
    assertEquals(Commons.rights(Map("License" -> "pd", "LicenseShortName" -> "Public domain")).map(_.credit),
      Right("Public domain, via Wikimedia Commons"))

  test("rights refused: non-free, unreadable, all rights reserved, attribution with no author"):
    assertEquals(Commons.rights(Map("License" -> "cc-by-4.0", "Artist" -> "x", "NonFree" -> "true")),
      Left("marked non-free on Commons"))
    assertEquals(Commons.rights(Map("Artist" -> "x")), Left("no licence stated"))
    assert(Commons.rights(Map("LicenseShortName" -> "Some custom terms", "Artist" -> "x")).left.exists(_.contains("not recognised")))
    assertEquals(Commons.rights(Map("LicenseShortName" -> "Copyrighted", "Artist" -> "x")), Left("all rights reserved"))
    assertEquals(Commons.rights(Map("License" -> "cc-by-4.0")), Left("no author to credit"))
    assertEquals(Commons.rights(Map("License" -> "cc0", "AttributionRequired" -> "true")), Left("no author to credit"))

  // ── config ───────────────────────────────────────────────────────────────────────────────────

  test("media config: an honest User-Agent with no e-mail, the default allowlist without NC, P15/P3311 roles"):
    val m = AppConfig.load(org.ekrich.config.ConfigFactory.empty).media
    assertEquals(m.userAgent, "Histmersion (+https://github.com/histmersion/histmersion)")
    assert(!m.userAgent.contains("@") && !m.userAgent.toLowerCase.contains("mailto"))
    assertEquals(m.commons.allowed, Set(Cc0, PublicDomain, CcBy, CcBySa))
    assertEquals(m.wikidata.placeRoles.toMap.get("P3311"), Some(MediaRole.Plan))
    assertEquals(m.wikidata.placeRoles.toMap.get("P15"), Some(MediaRole.RouteMap))
    assertEquals(m.wikidata.eventRoles, List("P18" -> MediaRole.Image))

  test("media config: --limit is clamped to 1 … max-limit, absent = the default"):
    val m = AppConfig.load(org.ekrich.config.ConfigFactory.empty).media
    assertEquals(m.limit(None), m.defaultLimit)
    assertEquals(m.limit(Some(0)), m.defaultLimit)
    assertEquals(m.limit(Some(7)), 7)
    assertEquals(m.limit(Some(1000000)), m.maxLimit)

  // ── the report ───────────────────────────────────────────────────────────────────────────────

  test("the import report prints the counts per provider and licence as a table, refusals and failures after"):
    val r = MediaImportReport(MediaProvider.Commons, dryRun = true, targets = 20, items = 9, statements = 19, files = 10,
      byLicence = List(MediaCount("CC-BY-SA-3.0", 5), MediaCount("CC-BY-NC-4.0", 1, allowed = false)),
      byRole = List(MediaCount("locator-map", 16), MediaCount("image", 3)),
      rejected = List(MediaCount("licence cc-by-nc not allowed for wikimedia-commons", 1)), failed = List("x: y"))
    val md = Markdown.mediaImportReport(r)
    assert(md.startsWith("# Media import · Wikimedia Commons (dry run — nothing stored)"), md)
    assert(md.contains("**By provider:** Wikimedia Commons 10"), md)
    assert(md.contains("| licence | files | allowed |\n|---|---|---|\n| `CC-BY-SA-3.0` | 5 | yes |\n| `CC-BY-NC-4.0` | 1 | **no** |"), md)
    assert(md.contains("**Not stored**\n- licence cc-by-nc not allowed for wikimedia-commons: 1"), md)
    assert(md.contains("**Failed**\n- x: y"), md)
    assert(!md.contains("**Stored:**"), "a dry run stores nothing")
    assert(Markdown.mediaImportReport(r.copy(dryRun = false, stored = 2)).contains("**Stored:** 2 new"))
