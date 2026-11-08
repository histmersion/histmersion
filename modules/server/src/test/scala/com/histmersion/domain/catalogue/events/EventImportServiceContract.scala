package com.histmersion
package domain.catalogue.events

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.{CatalogRecord, EventPatch, EventPut}
import com.histmersion.domain.catalogue.CatalogueStoreSuite
import com.histmersion.api.catalogue.Correction
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.domain.catalogue.Event
import com.histmersion.api.catalogue.EventSummary

/** The contract an event catalogue's import keeps: a new event made from the record it is named after, refreshed when
  * its record is written again, an event made by hand only filled; the records kept by key with their kinds; the parts
  * read; and the corrections by hand — recorded when a person changes an imported event, kept over the catalogue's
  * value, retired once the catalogue agrees, flagged when it says something else.
  */
abstract class EventImportServiceContract extends CatalogueStoreSuite:
  private def record(id: CatalogRecord.ProviderId, label: CatalogRecord.Label, kind: CatalogRecord.Kind = CatalogRecord.Kind("eruption")) =
    CatalogRecord(
      CatalogRecord.Provider("gvp"),
      id,
      CatalogRecord.Url(s"https://volcano.test/$id"),
      label,
      kind,
      TimeSpan(Some(Year(-1600)), None, None, Some(Year(-1600))),
      Some(Latitude(36.4)),
      Some(Longitude(25.4)),
      vei = Some(CatalogRecord.Vei(6)),
      names = List(CatalogRecord.Names(label.value + " alias"))
    )
  private def placeNew(r: CatalogRecord) =
    r -> CatalogMerge.Placement.New(Event.Id.applyUnsafe(CatalogRecord.eventId(r).value), CatalogMerge.Placement.Why("new"))

  stest("a record makes its event, writing it again refreshes it, an event made by hand is only filled") { s =>
    val im = s.eventImports
    val first = im.save(List(placeNew(record(CatalogRecord.ProviderId("1"), CatalogRecord.Label("Zz Thera")))))
    assertEquals((first.created, first.updated, first.rows), (1, 0, 1))
    val stored = im.stored().find(_.id.value == "gvp_1").get
    assertEquals((stored.catalogue, stored.label, stored.keys), (true, "Zz Thera", Set("gvp" -> "1")))
    assertEquals(stored.lat.map(l => math.round(l.value)), Some(36L))
    val again = im.save(List(placeNew(record(CatalogRecord.ProviderId("1"), CatalogRecord.Label("Zz Thera again")))))
    assertEquals((again.created, again.updated), (0, 1))
    assertEquals(s.events.get(Event.Id("gvp_1")).map(_.event.label), Some("Zz Thera again"))
    assertEquals(s.events.tierAndNames(Event.Id("gvp_1")).map(_._3), Some(Some("gvp")))
    s.events.put(Event.Id("zz_hand"), EventPut(EventSummary.Label("Zz by hand"), EventSummary.Kind("eruption"), TimeSpan(Some(Year(-1600)), None, None, Some(Year(-1600)))))
    val filled =
      im.save(List(record(CatalogRecord.ProviderId("2"), CatalogRecord.Label("Zz other name")) -> CatalogMerge.Placement.Existing(Event.Id("zz_hand"), CatalogMerge.Placement.Why("matched"))))
    assertEquals((filled.created, filled.updated), (0, 0))
    assertEquals(s.events.get(Event.Id("zz_hand")).map(_.event.label), Some("Zz by hand"), "never changed")
    assert(s.events.tierAndNames(Event.Id("zz_hand")).exists(_._2.contains("Zz other name")), "its names added to")
    val (records, undecodable) = im.records(Some(CatalogRecord.Provider("gvp")))
    assertEquals(records.map((id, _, r) => (id, r.providerId)), List(("gvp_1", "1"), ("zz_hand", "2")))
    assertEquals(undecodable, 0)
    assertEquals(im.storedKinds(CatalogRecord.Provider("gvp")), Map(("gvp", "1") -> "eruption", ("gvp", "2") -> "eruption"))
    assertEquals(
      s.events.catalogueRefs(Event.Id("gvp_1")).map(r => (r.provider, r.providerId, r.vei)),
      List(("gvp", "1", Some(6)))
    )
  }

  stest("the parts read: marked, listed, cleared") { s =>
    val im = s.eventImports
    im.markPart(CatalogRecord.Provider("gvp"), EventImportRepo.PartName("w1"), RowCount(3))
    im.markPart(CatalogRecord.Provider("gvp"), EventImportRepo.PartName("w2"), RowCount(0))
    im.markPart(CatalogRecord.Provider("noaa"), EventImportRepo.PartName("w1"), RowCount(1))
    assertEquals(im.partsDone(CatalogRecord.Provider("gvp")), Set("w1", "w2"))
    im.clearParts(CatalogRecord.Provider("gvp"))
    assertEquals((im.partsDone(CatalogRecord.Provider("gvp")), im.partsDone(CatalogRecord.Provider("noaa"))), (Set.empty[String], Set("w1")))
  }

  stest("a person's change is a correction: kept over the catalogue, retired once it agrees, flagged when it changes") {
    s =>
      val im = s.eventImports
      val _ = im.save(List(placeNew(record(CatalogRecord.ProviderId("7"), CatalogRecord.Label("Zz Vesuvius")))))
      assertEquals(s.events.patch(Event.Id("gvp_7"), EventPatch(label = Some(EventSummary.Label("Zz Vesuvius (by hand)")))), Right(()))
      def listed(status: Option[Correction.StatusValue]) =
        s.corrections.list(Correction.TargetKind(Correction.Target.Event), Some(Event.Id("gvp_7")), status, PageSize(50), Offset(0)).corrections
      assertEquals(
        listed(None).map(c => (c.field, c.status, c.label)),
        List(("label", Correction.Status.Active, Some("Zz Vesuvius (by hand)")))
      )
      val kept = im.save(List(placeNew(record(CatalogRecord.ProviderId("7"), CatalogRecord.Label("Zz Vesuvius")))))
      assertEquals(kept.corrections, Correction.Counts(kept = Correction.Counts.Kept(1)))
      assertEquals(s.events.get(Event.Id("gvp_7")).map(_.event.label), Some("Zz Vesuvius (by hand)"), "the person's value")
      val conflict = im.save(List(placeNew(record(CatalogRecord.ProviderId("7"), CatalogRecord.Label("Zz Somma")))))
      assertEquals(conflict.corrections, Correction.Counts(conflicts = Correction.Counts.Conflicts(1)))
      assertEquals(listed(Some(Correction.StatusValue("conflict"))).map(_.field), List("label"))
      val agreed = im.save(List(placeNew(record(CatalogRecord.ProviderId("7"), CatalogRecord.Label("Zz Vesuvius (by hand)")))))
      assertEquals(agreed.corrections, Correction.Counts(retired = Correction.Counts.Retired(1)))
      assertEquals(listed(None), Nil, "a retired correction is not listed by default")
      assertEquals(listed(Some(Correction.StatusValue("agreed"))).map(_.field), List("label"))
  }
