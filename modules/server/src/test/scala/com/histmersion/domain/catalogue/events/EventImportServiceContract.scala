package com.histmersion.domain.catalogue.events

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.{CatalogRecord, EventPatch, EventPut}
import com.histmersion.domain.catalogue.CatalogueStoreSuite
import com.histmersion.api.catalogue.Correction

/** The contract an event catalogue's import keeps: a new event made from the record it is named after, refreshed when
  * its record is written again, an event made by hand only filled; the records kept by key with their kinds; the parts
  * read; and the corrections by hand — recorded when a person changes an imported event, kept over the catalogue's
  * value, retired once the catalogue agrees, flagged when it says something else.
  */
abstract class EventImportServiceContract extends CatalogueStoreSuite:
  private def record(id: String, label: String, kind: String = "eruption") =
    CatalogRecord(
      com.histmersion.api.catalogue.CatalogRecord.Provider("gvp"),
      id,
      s"https://volcano.test/$id",
      label,
      kind,
      TimeSpan(Some(com.histmersion.api.catalogue.Year(-1600)), None, None, Some(com.histmersion.api.catalogue.Year(-1600))),
      Some(36.4),
      Some(25.4),
      vei = Some(6),
      names = List(label + " alias")
    )
  private def placeNew(r: CatalogRecord) = r -> CatalogMerge.Placement.New(CatalogRecord.eventId(r), com.histmersion.domain.catalogue.events.CatalogMerge.Placement.Why("new"))

  stest("a record makes its event, writing it again refreshes it, an event made by hand is only filled") { s =>
    val im = s.eventImports
    val first = im.save(List(placeNew(record("1", "Zz Thera"))))
    assertEquals((first.created, first.updated, first.rows), (1, 0, 1))
    val stored = im.stored().find(_.id == "gvp_1").get
    assertEquals((stored.catalogue, stored.label, stored.keys), (true, "Zz Thera", Set("gvp" -> "1")))
    assertEquals(stored.lat.map(math.round), Some(36L))
    val again = im.save(List(placeNew(record("1", "Zz Thera again"))))
    assertEquals((again.created, again.updated), (0, 1))
    assertEquals(s.events.get(com.histmersion.domain.catalogue.Event.Id("gvp_1")).map(_.event.label), Some("Zz Thera again"))
    assertEquals(s.events.tierAndNames(com.histmersion.domain.catalogue.Event.Id("gvp_1")).map(_._3), Some(Some("gvp")))
    s.events.put(com.histmersion.domain.catalogue.Event.Id("zz_hand"), EventPut(com.histmersion.api.catalogue.EventSummary.Label("Zz by hand"), com.histmersion.api.catalogue.EventSummary.Kind("eruption"), TimeSpan(Some(com.histmersion.api.catalogue.Year(-1600)), None, None, Some(com.histmersion.api.catalogue.Year(-1600)))))
    val filled =
      im.save(List(record("2", "Zz other name") -> CatalogMerge.Placement.Existing("zz_hand", "matched")))
    assertEquals((filled.created, filled.updated), (0, 0))
    assertEquals(s.events.get(com.histmersion.domain.catalogue.Event.Id("zz_hand")).map(_.event.label), Some("Zz by hand"), "never changed")
    assert(s.events.tierAndNames(com.histmersion.domain.catalogue.Event.Id("zz_hand")).exists(_._2.contains("Zz other name")), "its names added to")
    val (records, undecodable) = im.records(Some(com.histmersion.api.catalogue.CatalogRecord.Provider("gvp")))
    assertEquals(records.map((id, _, r) => (id, r.providerId)), List(("gvp_1", "1"), ("zz_hand", "2")))
    assertEquals(undecodable, 0)
    assertEquals(im.storedKinds(com.histmersion.api.catalogue.CatalogRecord.Provider("gvp")), Map(("gvp", "1") -> "eruption", ("gvp", "2") -> "eruption"))
    assertEquals(
      s.events.catalogueRefs(com.histmersion.domain.catalogue.Event.Id("gvp_1")).map(r => (r.provider, r.providerId, r.vei)),
      List(("gvp", "1", Some(6)))
    )
  }

  stest("the parts read: marked, listed, cleared") { s =>
    val im = s.eventImports
    im.markPart(com.histmersion.api.catalogue.CatalogRecord.Provider("gvp"), com.histmersion.domain.catalogue.events.EventImportRepo.PartName("w1"), com.histmersion.RowCount(3))
    im.markPart(com.histmersion.api.catalogue.CatalogRecord.Provider("gvp"), com.histmersion.domain.catalogue.events.EventImportRepo.PartName("w2"), com.histmersion.RowCount(0))
    im.markPart(com.histmersion.api.catalogue.CatalogRecord.Provider("noaa"), com.histmersion.domain.catalogue.events.EventImportRepo.PartName("w1"), com.histmersion.RowCount(1))
    assertEquals(im.partsDone(com.histmersion.api.catalogue.CatalogRecord.Provider("gvp")), Set("w1", "w2"))
    im.clearParts(com.histmersion.api.catalogue.CatalogRecord.Provider("gvp"))
    assertEquals((im.partsDone(com.histmersion.api.catalogue.CatalogRecord.Provider("gvp")), im.partsDone(com.histmersion.api.catalogue.CatalogRecord.Provider("noaa"))), (Set.empty[String], Set("w1")))
  }

  stest("a person's change is a correction: kept over the catalogue, retired once it agrees, flagged when it changes") {
    s =>
      val im = s.eventImports
      val _ = im.save(List(placeNew(record("7", "Zz Vesuvius"))))
      assertEquals(s.events.patch(com.histmersion.domain.catalogue.Event.Id("gvp_7"), EventPatch(label = Some(com.histmersion.api.catalogue.EventSummary.Label("Zz Vesuvius (by hand)")))), Right(()))
      def listed(status: Option[String]) =
        s.corrections.list(Correction.Target.Event, Some(com.histmersion.domain.catalogue.Event.Id("gvp_7")), status, com.histmersion.PageSize(50), com.histmersion.Offset(0)).corrections
      assertEquals(
        listed(None).map(c => (c.field, c.status, c.label)),
        List(("label", Correction.Status.Active, Some("Zz Vesuvius (by hand)")))
      )
      val kept = im.save(List(placeNew(record("7", "Zz Vesuvius"))))
      assertEquals(kept.corrections, Correction.Counts(kept = com.histmersion.api.catalogue.Correction.Counts.Kept(1)))
      assertEquals(s.events.get(com.histmersion.domain.catalogue.Event.Id("gvp_7")).map(_.event.label), Some("Zz Vesuvius (by hand)"), "the person's value")
      val conflict = im.save(List(placeNew(record("7", "Zz Somma"))))
      assertEquals(conflict.corrections, Correction.Counts(conflicts = com.histmersion.api.catalogue.Correction.Counts.Conflicts(1)))
      assertEquals(listed(Some("conflict")).map(_.field), List("label"))
      val agreed = im.save(List(placeNew(record("7", "Zz Vesuvius (by hand)"))))
      assertEquals(agreed.corrections, Correction.Counts(retired = com.histmersion.api.catalogue.Correction.Counts.Retired(1)))
      assertEquals(listed(None), Nil, "a retired correction is not listed by default")
      assertEquals(listed(Some("agreed")).map(_.field), List("label"))
  }
