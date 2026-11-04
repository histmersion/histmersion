package com.histmersion.domain.catalogue.periods

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Dataset
import com.histmersion.api.catalogue.{HistoricalPeriod, Period, PresetRegion}
import com.histmersion.domain.catalogue.{CatalogueStores, CatalogueStoreSuite}

/** The contract the named periods keep: parents written before their parts and the inclusive span read back, the
  * placement a re-import keeps, an idempotent upsert, the presets and the search by label, region and span, the seed's
  * deletions and version, a label search with `_` and `%` literal.
  */
abstract class PeriodServiceContract extends CatalogueStoreSuite:
  private def hp(id: String, s: Long, e: Long, parent: Option[String] = None) =
    HistoricalPeriod(id, id.toUpperCase, Some(s"PeriodO $id · test"), Period(s, e), Some("Zzrland"), parent)

  private val tree = List(
    hp("zzr_root", -3000, -1000),
    hp("zzr_bronze", -3000, -1200, Some("zzr_root")),
    hp("zzr_lba", -1600, -1200, Some("zzr_bronze")),
    hp("zzr_lm", -1700, -1050, Some("zzr_bronze"))
  )

  private def ids(ps: List[HistoricalPeriod]) = ps.map(_.id)

  stest("parents are written before their parts, the span reads back inclusive, the parts by start") { s =>
    val p = s.periods
    assertEquals(p.write(tree, Dataset.Seed), 4)
    assertEquals(p.get("zzr_lba"), Some(tree(2)))
    assertEquals(p.get("zzr_lba").map(_.period.end), Some(-1200L))
    assertEquals(ids(p.children("zzr_bronze")), List("zzr_lm", "zzr_lba"))
    assertEquals(p.get("zzr_nope"), None)
    assertEquals(p.write(Nil, Dataset.Seed), 0)
    intercept[Exception](p.write(List(hp("zzr_orphan", 1, 2, Some("zzr_missing"))), Dataset.Seed))
    assertEquals(p.get("zzr_orphan"), None)
  }

  stest("placement names every stored id with its parent; a re-run is idempotent and a re-parent sticks") { s =>
    val p = s.periods
    p.write(tree, Dataset.Seed)
    assertEquals(
      p.placement(List("zzr_root", "zzr_lba", "zzr_missing")),
      Map("zzr_root" -> None, "zzr_lba" -> Some("zzr_bronze"))
    )
    assertEquals(p.placement(Nil), Map.empty)
    val before = p.count()
    p.write(List(hp("zzr_lba", -1600, -1200, Some("zzr_root"))), Dataset.Seed)
    assertEquals(p.count(), before)
    assertEquals(p.get("zzr_lba").flatMap(_.parentId), Some("zzr_root"))
  }

  stest("the presets are the rows filed under a heading; the search finds by label, region and either bound") { s =>
    val p = s.periods
    p.write(tree :+ hp("zzr_other", 100, 200).modify(_.preset).setTo(Some(PresetRegion.Aegean)), Dataset.Seed)
    assertEquals(
      p.search(PeriodSearch(Some("ZZR"))).filter(_.preset.isDefined).map(x => x.id -> x.preset),
      List("zzr_other" -> Some(PresetRegion.Aegean))
    )
    assertEquals(p.presetCount(), 1)
    assertEquals(ids(p.search(PeriodSearch(Some("ZZR"), to = Some(-2500)))).toSet, Set("zzr_root", "zzr_bronze"))
    assertEquals(ids(p.search(PeriodSearch(Some("ZZR"), Some("zzrland"), from = Some(150)))), List("zzr_other"))
    assertEquals(ids(p.search(PeriodSearch(region = Some("elsewhere")))), Nil)
    assertEquals(
      ids(p.search(PeriodSearch())),
      List("zzr_bronze", "zzr_root", "zzr_lm", "zzr_lba", "zzr_other")
    )
    assertEquals(
      ids(p.search(PeriodSearch(from = Some(-1650), to = Some(-1650)))).toSet,
      Set("zzr_root", "zzr_bronze", "zzr_lm")
    )
  }

  stest("an upsert without a preset keeps the stored one; retainPresets takes the others off the menu") { s =>
    val p = s.periods
    p.write(tree :+ hp("zzr_other", 100, 200).modify(_.preset).setTo(Some(PresetRegion.Aegean)), Dataset.Seed)
    p.write(List(hp("zzr_other", 100, 200)), Dataset.Seed)
    assertEquals(p.get("zzr_other").flatMap(_.preset), Some(PresetRegion.Aegean))
    p.write(List(hp("zzr_root", -3000, -1000).modify(_.preset).setTo(Some(PresetRegion.Iran))), Dataset.Seed)
    assertEquals(p.retainPresets(List("zzr_root")), 1)
    assertEquals(p.get("zzr_other").map(x => (x.preset, x.label)), Some((None, "ZZR_OTHER")))
    assertEquals(p.get("zzr_root").flatMap(_.preset), Some(PresetRegion.Iran))
    assertEquals(p.presetCount(), 1)
  }

  stest("deleteSeedExcept removes the seed's rows not kept, never a collection's own; their parts lose the parent") {
    s =>
      val p = s.periods
      p.write(tree, Dataset.Seed)
      p.write(List(hp("zzr_mine", 1, 2, Some("zzr_lm"))), Dataset.Local)
      assertEquals(s.seedRows().toSet, tree.map(_.id).toSet)
      assertEquals(p.deleteSeedExcept(List("zzr_root", "zzr_bronze", "zzr_lba")), 1)
      assertEquals(p.get("zzr_lm"), None)
      assertEquals(p.get("zzr_mine").map(_.parentId), Some(None))
      assertEquals(p.deleteSeedExcept(Nil), 3)
      assertEquals(ids(p.search(PeriodSearch())), List("zzr_mine"))
  }

  stest("the seed version is stored and moved") { s =>
    val p = s.periods
    assertEquals(p.seedVersion("zzr_seed"), None)
    p.setSeedVersion("zzr_seed", 2)
    p.setSeedVersion("zzr_seed", 3)
    assertEquals(p.seedVersion("zzr_seed"), Some(3))
  }

  stest("the label search treats `_` and `%` as literal characters") { s =>
    val p = s.periods
    p.write(List(hp("zzr_root", -3000, -1000), hp("zzr_other", 100, 200), hp("zz%r", 1, 2)), Dataset.Seed)
    assertEquals(ids(p.search(PeriodSearch(Some("ZZR_")))).toSet, Set("zzr_root", "zzr_other"))
    assertEquals(ids(p.search(PeriodSearch(Some("zzr_oth")))), List("zzr_other"))
    assertEquals(ids(p.search(PeriodSearch(Some("ZZ_R")))), Nil)
    assertEquals(ids(p.search(PeriodSearch(Some("%")))), List("zz%r"))
    assertEquals(ids(p.search(PeriodSearch(Some("  ")))).size, 3)
  }
