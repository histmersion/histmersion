package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.media.MediaMarkdown

import com.histmersion.domain.catalogue.places.Wikidata

import com.histmersion.config.*
import com.histmersion.domain.media.MediaRole
import com.histmersion.api.media.{MediaCount, MediaImportReport, MediaProvider}

/** The host's half of the media providers (#1446): how a stated licence is read and judged, the card's role order, the
  * counts, a gazetteer id that is a Wikidata item, the media config and the import report — no network, no database.
  * Commons' and Wikidata's answers are read by the media plugins and `upstreams`, in their own suites.
  */
class MediaProvidersSuite extends munit.FunSuite:

  import Licence.*
  import com.histmersion.domain.Licence.Family.*

  // ── licences ─────────────────────────────────────────────────────────────────────────────────

  test("Commons' machine ids and short names read as SPDX ids and families"):
    assertEquals(classify(com.histmersion.domain.Licence.Id("cc-by-sa-3.0")), Licence(Licence.Id("CC-BY-SA-3.0"), CcBySa))
    assertEquals(classify(com.histmersion.domain.Licence.Id("CC BY-SA 3.0")), Licence(Licence.Id("CC-BY-SA-3.0"), CcBySa))
    assertEquals(classify(com.histmersion.domain.Licence.Id("cc-by-2.0")), Licence(Licence.Id("CC-BY-2.0"), CcBy))
    assertEquals(classify(com.histmersion.domain.Licence.Id("CC BY 4.0")), Licence(Licence.Id("CC-BY-4.0"), CcBy))
    assertEquals(classify(com.histmersion.domain.Licence.Id("cc-by-sa-3.0-de")), Licence(Licence.Id("CC-BY-SA-3.0-DE"), CcBySa))
    assertEquals(classify(com.histmersion.domain.Licence.Id("cc-by-nc-sa-4.0")), Licence(Licence.Id("CC-BY-NC-SA-4.0"), CcByNcSa))
    assertEquals(classify(com.histmersion.domain.Licence.Id("cc-by-nc-4.0")), Licence(Licence.Id("CC-BY-NC-4.0"), CcByNc))
    assertEquals(classify(com.histmersion.domain.Licence.Id("cc-by-nd-4.0")), Licence(Licence.Id("CC-BY-ND-4.0"), CcByNd))
    assertEquals(classify(com.histmersion.domain.Licence.Id("cc-by-sa-4")), Licence(Licence.Id("CC-BY-SA-4.0"), CcBySa))
    assertEquals(classify(com.histmersion.domain.Licence.Id("cc0")), Licence(Licence.Id("CC0-1.0"), Cc0))
    assertEquals(classify(com.histmersion.domain.Licence.Id("CC0 1.0")), Licence(Licence.Id("CC0-1.0"), Cc0))
    assertEquals(classify(com.histmersion.domain.Licence.Id("pd")), Licence(Licence.Id("public-domain"), PublicDomain))
    assertEquals(classify(com.histmersion.domain.Licence.Id("Public domain")), Licence(Licence.Id("public-domain"), PublicDomain))
    assertEquals(classify(com.histmersion.domain.Licence.Id("PD-old-70")), Licence(Licence.Id("public-domain"), PublicDomain))
    assertEquals(classify(com.histmersion.domain.Licence.Id("GFDL")).family, Gfdl)
    assertEquals(classify(com.histmersion.domain.Licence.Id("Copyrighted")).family, AllRightsReserved)
    assertEquals(classify(com.histmersion.domain.Licence.Id("Attribution only license")).family, Unknown)
    assertEquals(classify(com.histmersion.domain.Licence.Id("")).family, Unknown)

  test("parse takes the machine id first, then the short name; neither read is unknown"):
    assertEquals(Licence.parse(Some(com.histmersion.domain.Licence.Statement("cc-by-sa-4.0")), Some(com.histmersion.domain.Licence.Statement("CC BY 2.0"))).id.value, "CC-BY-SA-4.0")
    assertEquals(Licence.parse(Some(com.histmersion.domain.Licence.Statement("something-odd")), Some(com.histmersion.domain.Licence.Statement("CC BY 2.0"))).id.value, "CC-BY-2.0")
    assertEquals(Licence.parse(None, None), Licence(Licence.Id(""), Unknown))
    assertEquals(Licence.parse(Some(com.histmersion.domain.Licence.Statement("weird")), None), Licence(Licence.Id("weird"), Unknown))
    assertEquals(
      firstKnown(List(com.histmersion.domain.Licence.Statement(" "), com.histmersion.domain.Licence.Statement("odd"), com.histmersion.domain.Licence.Statement("CC BY 2.0"))),
      Licence(Licence.Id("CC-BY-2.0"), CcBy),
      "any number of statements"
    )
    assertEquals(firstKnown(List(com.histmersion.domain.Licence.Statement(" "), com.histmersion.domain.Licence.Statement("odd"))), Licence(Licence.Id("odd"), Unknown))
    assertEquals(firstKnown(Nil), Licence(Licence.Id(""), Unknown))

  test("the allowlist keeps known families only, and never all-rights-reserved or unknown"):
    assertEquals(allowList(com.histmersion.domain.Licence.AllowList("cc0, public-domain,CC-BY ,cc-by-sa")), Set(Cc0, PublicDomain, CcBy, CcBySa))
    assertEquals(allowList(com.histmersion.domain.Licence.AllowList("cc-by,all-rights-reserved,unknown,bogus")), Set(CcBy))
    assertEquals(allowList(com.histmersion.domain.Licence.AllowList("")), Set.empty[Licence.Family])
    val default = allowList(com.histmersion.domain.Licence.AllowList("cc0,public-domain,cc-by,cc-by-sa"))
    assert(allowed(classify(com.histmersion.domain.Licence.Id("cc-by-sa-4.0")), default))
    assert(!allowed(classify(com.histmersion.domain.Licence.Id("cc-by-nc-4.0")), default), "non-commercial is off by default")
    assert(allowed(classify(com.histmersion.domain.Licence.Id("cc-by-nc-4.0")), default + CcByNc), "…and on when the provider lists it")
    assert(
      !allowed(Licence(Licence.Id("all-rights-reserved"), AllRightsReserved), Set(AllRightsReserved)),
      "all rights reserved: never"
    )
    assert(!allowed(Licence(Licence.Id("x"), Unknown), Set(Unknown)), "unknown: never")

  test("which families need the author named"):
    assert(CcBy.needsAttribution && CcBySa.needsAttribution && CcByNcSa.needsAttribution && Gfdl.needsAttribution)
    assert(!Cc0.needsAttribution && !PublicDomain.needsAttribution)

  // ── roles and counts (shared) ────────────────────────────────────────────────────────────────

  test("the card's role order puts the item's own image first, unknown roles last"):
    assert(MediaRole.order(MediaRole.Wire(MediaRole.Image)) < MediaRole.order(MediaRole.Wire(MediaRole.LocatorMap)))
    assertEquals(MediaRole.order(com.histmersion.domain.media.MediaRole.Wire("other")), MediaRole.all.size)

  test("counts: most frequent first, then by key, with the allowed flag"):
    assertEquals(
      MediaCount.of(List(com.histmersion.api.media.MediaCount.Key("b"), com.histmersion.api.media.MediaCount.Key("a"), com.histmersion.api.media.MediaCount.Key("b"), com.histmersion.api.media.MediaCount.Key("c")), _.value != "c"),
      List(MediaCount(com.histmersion.api.media.MediaCount.Key("b"), com.histmersion.api.media.MediaCount.N(2)), MediaCount(com.histmersion.api.media.MediaCount.Key("a"), com.histmersion.api.media.MediaCount.N(1)), MediaCount(com.histmersion.api.media.MediaCount.Key("c"), com.histmersion.api.media.MediaCount.N(1), allowed = com.histmersion.api.media.MediaCount.Allowed(false)))
    )

  // ── Wikidata ─────────────────────────────────────────────────────────────────────────────────

  test("a gazetteer id that IS a Wikidata item names it"):
    assertEquals(Wikidata.qidOfPlaceId(com.histmersion.domain.catalogue.Place.Id("wikidata_q132564")), Some("Q132564"))
    assertEquals(Wikidata.qidOfPlaceId(com.histmersion.domain.catalogue.Place.Id("pleiades_570491")), None)
    assertEquals(Wikidata.qidOfPlaceId(com.histmersion.domain.catalogue.Place.Id("wikidata_qabc")), None)
    assert(Wikidata.isQid("Q1") && !Wikidata.isQid("Q01") && !Wikidata.isQid("P18"))

  // ── config ───────────────────────────────────────────────────────────────────────────────────

  test("media config: --limit is clamped to 1 … max-limit, absent = the default"):
    val m = AppConfig.load(org.ekrich.config.ConfigFactory.empty).media
    assertEquals(m.limit(None), m.defaultLimit)
    assertEquals(m.limit(Some(com.histmersion.config.MediaConfig.Limit(0))), m.defaultLimit)
    assertEquals(m.limit(Some(com.histmersion.config.MediaConfig.Limit(7))), 7)
    assertEquals(m.limit(Some(com.histmersion.config.MediaConfig.Limit(1000000))), m.maxLimit)

  // ── the report ───────────────────────────────────────────────────────────────────────────────

  test("the import report prints the counts per provider and licence as a table, refusals and failures after"):
    val r = MediaImportReport(
      com.histmersion.api.media.MediaItem.Provider(MediaProvider.Commons),
      dryRun = com.histmersion.api.library.RepairAcquire.RepairRequest.DryRun(true),
      targets = com.histmersion.api.media.MediaImportReport.Targets(20),
      items = com.histmersion.api.media.MediaImportReport.Items(9),
      statements = com.histmersion.api.media.MediaImportReport.Statements(19),
      files = com.histmersion.api.media.MediaImportReport.Files(10),
      byLicence = List(MediaCount(com.histmersion.api.media.MediaCount.Key("CC-BY-SA-3.0"), com.histmersion.api.media.MediaCount.N(5)), MediaCount(com.histmersion.api.media.MediaCount.Key("CC-BY-NC-4.0"), com.histmersion.api.media.MediaCount.N(1), allowed = com.histmersion.api.media.MediaCount.Allowed(false))),
      byRole = List(MediaCount(com.histmersion.api.media.MediaCount.Key("locator-map"), com.histmersion.api.media.MediaCount.N(16)), MediaCount(com.histmersion.api.media.MediaCount.Key("image"), com.histmersion.api.media.MediaCount.N(3))),
      rejected = List(MediaCount(com.histmersion.api.media.MediaCount.Key("licence cc-by-nc not allowed for wikimedia-commons"), com.histmersion.api.media.MediaCount.N(1))),
      failed = List(com.histmersion.api.media.MediaImportReport.Failed("x: y"))
    )
    val md = MediaMarkdown.mediaImportReport(r)
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
    assert(MediaMarkdown.mediaImportReport(r.modify(_.dryRun).setTo(false).modify(_.stored).setTo(com.histmersion.api.media.MediaImportReport.Stored(2))).contains("**Stored:** 2 new"))
