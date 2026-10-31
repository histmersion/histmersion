package com.histmersion.domain

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.values.MediaRole

/** The host's half of the media providers (#1446): how a stated licence is read and judged, the card's role order, the
  * counts, a gazetteer id that is a Wikidata item, the media config and the import report — no network, no database.
  * Commons' and Wikidata's answers are read by the media plugins and `upstreams`, in their own suites.
  */
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
    assertEquals(firstKnown(List(" ", "odd", "CC BY 2.0")), Licence("CC-BY-2.0", CcBy), "any number of statements")
    assertEquals(firstKnown(List(" ", "odd")), Licence("odd", Unknown))
    assertEquals(firstKnown(Nil), Licence("", Unknown))

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

  // ── roles and counts (shared) ────────────────────────────────────────────────────────────────

  test("the card's role order puts the item's own image first, unknown roles last"):
    assert(MediaRole.order(MediaRole.Image) < MediaRole.order(MediaRole.LocatorMap))
    assertEquals(MediaRole.order("other"), MediaRole.all.size)

  test("counts: most frequent first, then by key, with the allowed flag"):
    assertEquals(
      MediaCount.of(List("b", "a", "b", "c"), _ != "c"),
      List(MediaCount("b", 2), MediaCount("a", 1), MediaCount("c", 1, allowed = false))
    )

  // ── Wikidata ─────────────────────────────────────────────────────────────────────────────────

  test("a gazetteer id that IS a Wikidata item names it"):
    assertEquals(Wikidata.qidOfPlaceId("wikidata_q132564"), Some("Q132564"))
    assertEquals(Wikidata.qidOfPlaceId("pleiades_570491"), None)
    assertEquals(Wikidata.qidOfPlaceId("wikidata_qabc"), None)
    assert(Wikidata.isQid("Q1") && !Wikidata.isQid("Q01") && !Wikidata.isQid("P18"))

  // ── config ───────────────────────────────────────────────────────────────────────────────────

  test("media config: --limit is clamped to 1 … max-limit, absent = the default"):
    val m = AppConfig.load(org.ekrich.config.ConfigFactory.empty).media
    assertEquals(m.limit(None), m.defaultLimit)
    assertEquals(m.limit(Some(0)), m.defaultLimit)
    assertEquals(m.limit(Some(7)), 7)
    assertEquals(m.limit(Some(1000000)), m.maxLimit)

  // ── the report ───────────────────────────────────────────────────────────────────────────────

  test("the import report prints the counts per provider and licence as a table, refusals and failures after"):
    val r = MediaImportReport(
      MediaProvider.Commons,
      dryRun = true,
      targets = 20,
      items = 9,
      statements = 19,
      files = 10,
      byLicence = List(MediaCount("CC-BY-SA-3.0", 5), MediaCount("CC-BY-NC-4.0", 1, allowed = false)),
      byRole = List(MediaCount("locator-map", 16), MediaCount("image", 3)),
      rejected = List(MediaCount("licence cc-by-nc not allowed for wikimedia-commons", 1)),
      failed = List("x: y")
    )
    val md = Markdown.mediaImportReport(r)
    assert(md.startsWith("# Media import · Wikimedia Commons (dry run — nothing stored)"), md)
    assert(md.contains("**By provider:** Wikimedia Commons 10"), md)
    assert(
      md.contains(
        "| licence | files | allowed |\n|---|---|---|\n| `CC-BY-SA-3.0` | 5 | yes |\n| `CC-BY-NC-4.0` | 1 | **no** |"
      ),
      md
    )
    assert(md.contains("**Not stored**\n- licence cc-by-nc not allowed for wikimedia-commons: 1"), md)
    assert(md.contains("**Failed**\n- x: y"), md)
    assert(!md.contains("**Stored:**"), "a dry run stores nothing")
    assert(Markdown.mediaImportReport(r.copy(dryRun = false, stored = 2)).contains("**Stored:** 2 new"))
