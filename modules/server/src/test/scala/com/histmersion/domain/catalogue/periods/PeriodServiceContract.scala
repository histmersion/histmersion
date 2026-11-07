package com.histmersion.domain.catalogue.periods

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Dataset
import com.histmersion.api.catalogue.{HistoricalPeriod, Period, PresetRegion, Year}
import com.histmersion.domain.catalogue.{CatalogueStores, CatalogueStoreSuite}

/** The contract the named periods keep: parents written before their parts and the inclusive span read back, the
  * placement a re-import keeps, an idempotent upsert, the presets and the search by label, region and span, the seed's
  * deletions and version, a label search with `_` and `%` literal.
  */
abstract class PeriodServiceContract extends CatalogueStoreSuite:
  private def hp(id: HistoricalPeriod.Id, s: Year, e: Year, parent: Option[HistoricalPeriod.ParentId] = None) =
    HistoricalPeriod(id, HistoricalPeriod.Label(id.value.toUpperCase), Some(HistoricalPeriod.Authority(s"PeriodO $id · test")), Period(s, e), Some(com.histmersion.api.catalogue.HistoricalPeriod.Region("Zzrland")), parent)

  private val tree = List(
    hp(HistoricalPeriod.Id("zzr_root"), Year(-3000), Year(-1000)),
    hp(HistoricalPeriod.Id("zzr_bronze"), Year(-3000), Year(-1200), Some(HistoricalPeriod.ParentId("zzr_root"))),
    hp(HistoricalPeriod.Id("zzr_lba"), Year(-1600), Year(-1200), Some(HistoricalPeriod.ParentId("zzr_bronze"))),
    hp(HistoricalPeriod.Id("zzr_lm"), Year(-1700), Year(-1050), Some(HistoricalPeriod.ParentId("zzr_bronze")))
  )

  private def ids(ps: List[HistoricalPeriod]) = ps.map(_.id)

  stest("parents are written before their parts, the span reads back inclusive, the parts by start") { s =>
    val p = s.periods
    assertEquals(p.write(tree, Dataset.Seed), 4)
    assertEquals(p.get(com.histmersion.api.catalogue.HistoricalPeriod.Id("zzr_lba")), Some(tree(2)))
    assertEquals(p.get(com.histmersion.api.catalogue.HistoricalPeriod.Id("zzr_lba")).map(_.period.end), Some(-1200L))
    assertEquals(ids(p.children(com.histmersion.api.catalogue.HistoricalPeriod.Id("zzr_bronze"))), List("zzr_lm", "zzr_lba"))
    assertEquals(p.get(com.histmersion.api.catalogue.HistoricalPeriod.Id("zzr_nope")), None)
    assertEquals(p.write(Nil, Dataset.Seed), 0)
    intercept[Exception](p.write(List(hp(HistoricalPeriod.Id("zzr_orphan"), Year(1), Year(2), Some(HistoricalPeriod.ParentId("zzr_missing")))), Dataset.Seed))
    assertEquals(p.get(com.histmersion.api.catalogue.HistoricalPeriod.Id("zzr_orphan")), None)
  }

  stest("placement names every stored id with its parent; a re-run is idempotent and a re-parent sticks") { s =>
    val p = s.periods
    p.write(tree, Dataset.Seed)
    assertEquals(
      p.placement(List(com.histmersion.api.catalogue.HistoricalPeriod.Id("zzr_root"), com.histmersion.api.catalogue.HistoricalPeriod.Id("zzr_lba"), com.histmersion.api.catalogue.HistoricalPeriod.Id("zzr_missing"))),
      Map("zzr_root" -> None, "zzr_lba" -> Some("zzr_bronze"))
    )
    assertEquals(p.placement(Nil), Map.empty)
    val before = p.count()
    p.write(List(hp(HistoricalPeriod.Id("zzr_lba"), Year(-1600), Year(-1200), Some(HistoricalPeriod.ParentId("zzr_root")))), Dataset.Seed)
    assertEquals(p.count(), before)
    assertEquals(p.get(com.histmersion.api.catalogue.HistoricalPeriod.Id("zzr_lba")).flatMap(_.parentId), Some("zzr_root"))
  }

  stest("the presets are the rows filed under a heading; the search finds by label, region and either bound") { s =>
    val p = s.periods
    p.write(tree :+ hp(HistoricalPeriod.Id("zzr_other"), Year(100), Year(200)).modify(_.preset).setTo(Some(PresetRegion.Aegean)), Dataset.Seed)
    assertEquals(
      p.search(PeriodSearch(Some(com.histmersion.domain.catalogue.periods.PeriodSearch.Text("ZZR")))).filter(_.preset.isDefined).map(x => x.id -> x.preset),
      List("zzr_other" -> Some(PresetRegion.Aegean))
    )
    assertEquals(p.presetCount(), 1)
    assertEquals(ids(p.search(PeriodSearch(Some(com.histmersion.domain.catalogue.periods.PeriodSearch.Text("ZZR")), to = Some(com.histmersion.domain.catalogue.periods.PeriodSearch.To(-2500))))).toSet, Set("zzr_root", "zzr_bronze"))
    assertEquals(ids(p.search(PeriodSearch(Some(com.histmersion.domain.catalogue.periods.PeriodSearch.Text("ZZR")), Some(com.histmersion.domain.catalogue.periods.PeriodSearch.Region("zzrland")), from = Some(com.histmersion.domain.catalogue.periods.PeriodSearch.From(150))))), List("zzr_other"))
    assertEquals(ids(p.search(PeriodSearch(region = Some(com.histmersion.domain.catalogue.periods.PeriodSearch.Region("elsewhere"))))), Nil)
    assertEquals(
      ids(p.search(PeriodSearch())),
      List("zzr_bronze", "zzr_root", "zzr_lm", "zzr_lba", "zzr_other")
    )
    assertEquals(
      ids(p.search(PeriodSearch(from = Some(com.histmersion.domain.catalogue.periods.PeriodSearch.From(-1650)), to = Some(com.histmersion.domain.catalogue.periods.PeriodSearch.To(-1650))))).toSet,
      Set("zzr_root", "zzr_bronze", "zzr_lm")
    )
  }

  stest("an upsert without a preset keeps the stored one; retainPresets takes the others off the menu") { s =>
    val p = s.periods
    p.write(tree :+ hp(HistoricalPeriod.Id("zzr_other"), Year(100), Year(200)).modify(_.preset).setTo(Some(PresetRegion.Aegean)), Dataset.Seed)
    p.write(List(hp(HistoricalPeriod.Id("zzr_other"), Year(100), Year(200))), Dataset.Seed)
    assertEquals(p.get(com.histmersion.api.catalogue.HistoricalPeriod.Id("zzr_other")).flatMap(_.preset), Some(PresetRegion.Aegean))
    p.write(List(hp(HistoricalPeriod.Id("zzr_root"), Year(-3000), Year(-1000)).modify(_.preset).setTo(Some(PresetRegion.Iran))), Dataset.Seed)
    assertEquals(p.retainPresets(List(com.histmersion.api.catalogue.HistoricalPeriod.Id("zzr_root"))), 1)
    assertEquals(p.get(com.histmersion.api.catalogue.HistoricalPeriod.Id("zzr_other")).map(x => (x.preset, x.label)), Some((None, "ZZR_OTHER")))
    assertEquals(p.get(com.histmersion.api.catalogue.HistoricalPeriod.Id("zzr_root")).flatMap(_.preset), Some(PresetRegion.Iran))
    assertEquals(p.presetCount(), 1)
  }

  stest("deleteSeedExcept removes the seed's rows not kept, never a collection's own; their parts lose the parent") {
    s =>
      val p = s.periods
      p.write(tree, Dataset.Seed)
      p.write(List(hp(HistoricalPeriod.Id("zzr_mine"), Year(1), Year(2), Some(HistoricalPeriod.ParentId("zzr_lm")))), Dataset.Local)
      assertEquals(s.seedRows().toSet, tree.map(_.id).toSet)
      assertEquals(p.deleteSeedExcept(List(com.histmersion.api.catalogue.HistoricalPeriod.Id("zzr_root"), com.histmersion.api.catalogue.HistoricalPeriod.Id("zzr_bronze"), com.histmersion.api.catalogue.HistoricalPeriod.Id("zzr_lba"))), 1)
      assertEquals(p.get(com.histmersion.api.catalogue.HistoricalPeriod.Id("zzr_lm")), None)
      assertEquals(p.get(com.histmersion.api.catalogue.HistoricalPeriod.Id("zzr_mine")).map(_.parentId), Some(None))
      assertEquals(p.deleteSeedExcept(Nil), 3)
      assertEquals(ids(p.search(PeriodSearch())), List("zzr_mine"))
  }

  stest("the seed version is stored and moved") { s =>
    val p = s.periods
    assertEquals(p.seedVersion(com.histmersion.domain.catalogue.periods.PeriodService.SeedName("zzr_seed")), None)
    p.setSeedVersion(com.histmersion.domain.catalogue.periods.PeriodService.SeedName("zzr_seed"), com.histmersion.domain.catalogue.periods.PeriodService.SeedVersion(2))
    p.setSeedVersion(com.histmersion.domain.catalogue.periods.PeriodService.SeedName("zzr_seed"), com.histmersion.domain.catalogue.periods.PeriodService.SeedVersion(3))
    assertEquals(p.seedVersion(com.histmersion.domain.catalogue.periods.PeriodService.SeedName("zzr_seed")), Some(3))
  }

  stest("the label search treats `_` and `%` as literal characters") { s =>
    val p = s.periods
    p.write(List(hp(HistoricalPeriod.Id("zzr_root"), Year(-3000), Year(-1000)), hp(HistoricalPeriod.Id("zzr_other"), Year(100), Year(200)), hp(HistoricalPeriod.Id("zz%r"), Year(1), Year(2))), Dataset.Seed)
    assertEquals(ids(p.search(PeriodSearch(Some(com.histmersion.domain.catalogue.periods.PeriodSearch.Text("ZZR_"))))).toSet, Set("zzr_root", "zzr_other"))
    assertEquals(ids(p.search(PeriodSearch(Some(com.histmersion.domain.catalogue.periods.PeriodSearch.Text("zzr_oth"))))), List("zzr_other"))
    assertEquals(ids(p.search(PeriodSearch(Some(com.histmersion.domain.catalogue.periods.PeriodSearch.Text("ZZ_R"))))), Nil)
    assertEquals(ids(p.search(PeriodSearch(Some(com.histmersion.domain.catalogue.periods.PeriodSearch.Text("%"))))), List("zz%r"))
    assertEquals(ids(p.search(PeriodSearch(Some(com.histmersion.domain.catalogue.periods.PeriodSearch.Text("  "))))).size, 3)
  }
