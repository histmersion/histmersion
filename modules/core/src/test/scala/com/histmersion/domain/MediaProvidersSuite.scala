package com.histmersion
package domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.media.MediaMarkdown

import com.histmersion.domain.catalogue.places.Wikidata

import com.histmersion.config.*
import com.histmersion.domain.media.MediaRole
import com.histmersion.api.media.{MediaCount, MediaImportReport, MediaProvider}
import com.histmersion.domain.catalogue.Place
import com.histmersion.config.MediaConfig
import com.histmersion.api.media.MediaItem
import com.histmersion.api.library.RepairAcquire

/** The host's half of the media providers (#1446): how a stated licence is read and judged, the card's role order, the
  * counts, a gazetteer id that is a Wikidata item, the media config and the import report — no network, no database.
  * Commons' and Wikidata's answers are read by the media plugins and `upstreams`, in their own suites.
  */
class MediaProvidersSuite extends munit.FunSuite:

  import Licence.*
  import com.histmersion.domain.Licence.Family.*

  // ── licences ─────────────────────────────────────────────────────────────────────────────────

  test("Commons' machine ids and short names read as SPDX ids and families"):
    assertEquals(classify(Id("cc-by-sa-3.0")), Licence(Licence.Id("CC-BY-SA-3.0"), CcBySa))
    assertEquals(classify(Id("CC BY-SA 3.0")), Licence(Licence.Id("CC-BY-SA-3.0"), CcBySa))
    assertEquals(classify(Id("cc-by-2.0")), Licence(Licence.Id("CC-BY-2.0"), CcBy))
    assertEquals(classify(Id("CC BY 4.0")), Licence(Licence.Id("CC-BY-4.0"), CcBy))
    assertEquals(classify(Id("cc-by-sa-3.0-de")), Licence(Licence.Id("CC-BY-SA-3.0-DE"), CcBySa))
    assertEquals(classify(Id("cc-by-nc-sa-4.0")), Licence(Licence.Id("CC-BY-NC-SA-4.0"), CcByNcSa))
    assertEquals(classify(Id("cc-by-nc-4.0")), Licence(Licence.Id("CC-BY-NC-4.0"), CcByNc))
    assertEquals(classify(Id("cc-by-nd-4.0")), Licence(Licence.Id("CC-BY-ND-4.0"), CcByNd))
    assertEquals(classify(Id("cc-by-sa-4")), Licence(Licence.Id("CC-BY-SA-4.0"), CcBySa))
    assertEquals(classify(Id("cc0")), Licence(Licence.Id("CC0-1.0"), Cc0))
    assertEquals(classify(Id("CC0 1.0")), Licence(Licence.Id("CC0-1.0"), Cc0))
    assertEquals(classify(Id("pd")), Licence(Licence.Id("public-domain"), PublicDomain))
    assertEquals(classify(Id("Public domain")), Licence(Licence.Id("public-domain"), PublicDomain))
    assertEquals(classify(Id("PD-old-70")), Licence(Licence.Id("public-domain"), PublicDomain))
    assertEquals(classify(Id("GFDL")).family, Gfdl)
    assertEquals(classify(Id("Copyrighted")).family, AllRightsReserved)
    assertEquals(classify(Id("Attribution only license")).family, Unknown)
    assertEquals(classify(Id("")).family, Unknown)

  test("parse takes the machine id first, then the short name; neither read is unknown"):
    assertEquals(Licence.parse(Some(Statement("cc-by-sa-4.0")), Some(Statement("CC BY 2.0"))).id.value, "CC-BY-SA-4.0")
    assertEquals(Licence.parse(Some(Statement("something-odd")), Some(Statement("CC BY 2.0"))).id.value, "CC-BY-2.0")
    assertEquals(Licence.parse(None, None), Licence(Licence.Id(""), Unknown))
    assertEquals(Licence.parse(Some(Statement("weird")), None), Licence(Licence.Id("weird"), Unknown))
    assertEquals(
      firstKnown(List(Statement(" "), Statement("odd"), Statement("CC BY 2.0"))),
      Licence(Licence.Id("CC-BY-2.0"), CcBy),
      "any number of statements"
    )
    assertEquals(firstKnown(List(Statement(" "), Statement("odd"))), Licence(Licence.Id("odd"), Unknown))
    assertEquals(firstKnown(Nil), Licence(Licence.Id(""), Unknown))

  test("the allowlist keeps known families only, and never all-rights-reserved or unknown"):
    assertEquals(allowList(AllowList("cc0, public-domain,CC-BY ,cc-by-sa")), Set(Cc0, PublicDomain, CcBy, CcBySa))
    assertEquals(allowList(AllowList("cc-by,all-rights-reserved,unknown,bogus")), Set(CcBy))
    assertEquals(allowList(AllowList("")), Set.empty[Licence.Family])
    val default = allowList(AllowList("cc0,public-domain,cc-by,cc-by-sa"))
    assert(allowed(classify(Id("cc-by-sa-4.0")), default))
    assert(!allowed(classify(Id("cc-by-nc-4.0")), default), "non-commercial is off by default")
    assert(allowed(classify(Id("cc-by-nc-4.0")), default + CcByNc), "…and on when the provider lists it")
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
    assertEquals(MediaRole.order(MediaRole.Wire("other")), MediaRole.all.size)

  test("counts: most frequent first, then by key, with the allowed flag"):
    assertEquals(
      MediaCount
        .of(List(MediaCount.Key("b"), MediaCount.Key("a"), MediaCount.Key("b"), MediaCount.Key("c")), _.value != "c"),
      List(
        MediaCount(MediaCount.Key("b"), MediaCount.N(2)),
        MediaCount(MediaCount.Key("a"), MediaCount.N(1)),
        MediaCount(MediaCount.Key("c"), MediaCount.N(1), allowed = MediaCount.Allowed(false))
      )
    )

  // ── Wikidata ─────────────────────────────────────────────────────────────────────────────────

  test("a gazetteer id that IS a Wikidata item names it"):
    assertEquals(Wikidata.qidOfPlaceId(Place.Id("wikidata_q132564")), Some("Q132564"))
    assertEquals(Wikidata.qidOfPlaceId(Place.Id("pleiades_570491")), None)
    assertEquals(Wikidata.qidOfPlaceId(Place.Id("wikidata_qabc")), None)
    assert(Wikidata.isQid("Q1") && !Wikidata.isQid("Q01") && !Wikidata.isQid("P18"))

  // ── config ───────────────────────────────────────────────────────────────────────────────────

  test("media config: --limit is clamped to 1 … max-limit, absent = the default"):
    val m = AppConfig.load(org.ekrich.config.ConfigFactory.empty).media
    assertEquals(m.limit(None), m.defaultLimit)
    assertEquals(m.limit(Some(MediaConfig.Limit(0))), m.defaultLimit)
    assertEquals(m.limit(Some(MediaConfig.Limit(7))), 7)
    assertEquals(m.limit(Some(MediaConfig.Limit(1000000))), m.maxLimit)

  // ── the report ───────────────────────────────────────────────────────────────────────────────

  test("the import report prints the counts per provider and licence as a table, refusals and failures after"):
    val r = MediaImportReport(
      MediaItem.Provider(MediaProvider.Commons),
      dryRun = RepairAcquire.RepairRequest.DryRun(true),
      targets = MediaImportReport.Targets(20),
      items = MediaImportReport.Items(9),
      statements = MediaImportReport.Statements(19),
      files = MediaImportReport.Files(10),
      byLicence = List(
        MediaCount(MediaCount.Key("CC-BY-SA-3.0"), MediaCount.N(5)),
        MediaCount(MediaCount.Key("CC-BY-NC-4.0"), MediaCount.N(1), allowed = MediaCount.Allowed(false))
      ),
      byRole = List(
        MediaCount(MediaCount.Key("locator-map"), MediaCount.N(16)),
        MediaCount(MediaCount.Key("image"), MediaCount.N(3))
      ),
      rejected =
        List(MediaCount(MediaCount.Key("licence cc-by-nc not allowed for wikimedia-commons"), MediaCount.N(1))),
      failed = List(MediaImportReport.Failed("x: y"))
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
    assert(
      MediaMarkdown
        .mediaImportReport(
          r.modify(_.dryRun)
            .setTo(RepairAcquire.RepairRequest.DryRun(false))
            .modify(_.stored)
            .setTo(MediaImportReport.Stored(2))
        )
        .contains("**Stored:** 2 new")
    )
