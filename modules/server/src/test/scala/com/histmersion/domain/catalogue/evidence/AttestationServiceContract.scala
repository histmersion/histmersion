package com.histmersion.domain.catalogue.evidence

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.catalogue.CatalogueStoreSuite
import com.histmersion.api.catalogue.Attestation

/** The contract the attestations keep: ids in the order they were made, every field read back, the list narrowed by
  * source, target kind and target and paged, a patch changing only what it names, a delete once.
  */
abstract class AttestationServiceContract extends CatalogueStoreSuite:
  stest("made, read back, listed and paged, patched, deleted") { s =>
    val a = s.attestations
    s.source("zzat2020_one")
    s.source("zzat2021_two")
    val first = a.create(
      Attestation.Put(
        com.histmersion.api.library.Citekey("zzat2020_one"),
        com.histmersion.api.catalogue.Attestation.TargetType("place"),
        com.histmersion.api.catalogue.Attestation.TargetId("zz_hill"),
        Some(com.histmersion.api.catalogue.Attestation.Quote("the hill")),
        Some(com.histmersion.api.catalogue.Attestation.Prefix("on ")),
        Some(com.histmersion.api.catalogue.Attestation.Suffix(" stood")),
        Some(com.histmersion.api.catalogue.Attestation.CharStart(3)),
        Some(com.histmersion.api.catalogue.Attestation.CharEnd(11)),
        Some(com.histmersion.api.catalogue.Attestation.ChunkOrd(0)),
        Some(com.histmersion.api.catalogue.Attestation.Page("12")),
        Some(com.histmersion.api.catalogue.Attestation.Canvas("https://iiif.test/c1")),
        Some(com.histmersion.api.catalogue.Attestation.Xywh("1,2,3,4")),
        Some(com.histmersion.api.catalogue.Attestation.CtsUrn("urn:cts:x")),
        Some(com.histmersion.api.catalogue.Attestation.Note("n"))
      )
    )
    val second = a.create(Attestation.Put(com.histmersion.api.library.Citekey("zzat2020_one"), com.histmersion.api.catalogue.Attestation.TargetType("event"), com.histmersion.api.catalogue.Attestation.TargetId("zz_battle"), quote = Some(com.histmersion.api.catalogue.Attestation.Quote("the battle"))))
    val third = a.create(Attestation.Put(com.histmersion.api.library.Citekey("zzat2021_two"), com.histmersion.api.catalogue.Attestation.TargetType("place"), com.histmersion.api.catalogue.Attestation.TargetId("zz_hill")))
    assert(first.id.value < second.id.value && second.id.value < third.id.value)
    assertEquals(a.get(first.id), Some(first))
    assertEquals(
      (first.quote, first.charEnd, first.ctsUrn, first.note),
      (Some("the hill"), Some(11), Some("urn:cts:x"), Some("n"))
    )
    assertEquals(a.get(Attestation.Id(third.id.value + 100)), None)
    val all = a.list(None, None, None, com.histmersion.PageSize(10), com.histmersion.Offset(0))
    assertEquals(((all.total.value: Int), all.attestations.map(_.id)), (3, List(first.id, second.id, third.id)))
    assertEquals(a.list(Some(com.histmersion.domain.library.Citekey("zzat2020_one")), None, None, com.histmersion.PageSize(10), com.histmersion.Offset(0)).attestations.map(_.id), List(first.id, second.id))
    assertEquals(a.list(None, Some(com.histmersion.api.catalogue.Attestation.TargetType("place")), Some(com.histmersion.api.catalogue.Attestation.TargetId("zz_hill")), com.histmersion.PageSize(10), com.histmersion.Offset(0)).attestations.map(_.id), List(first.id, third.id))
    assertEquals((a.list(Some(com.histmersion.domain.library.Citekey.assume(" ")), Some(com.histmersion.api.catalogue.Attestation.TargetType("")), None, com.histmersion.PageSize(10), com.histmersion.Offset(0)).total.value: Int), 3)
    val page = a.list(None, None, None, com.histmersion.PageSize(1), com.histmersion.Offset(1))
    assertEquals(((page.total.value: Int), page.attestations.map(_.id), (page.offset.value: Int)), (3, List(second.id), 1))
    assertEquals(a.list(None, None, None, com.histmersion.PageSize.assume(0), com.histmersion.Offset.assume(-5)).attestations.map(_.id), List(first.id))
    val patched = a.patch(first.id, Attestation.Patch(note = Some(com.histmersion.api.catalogue.Attestation.Note("changed")), page = Some(com.histmersion.api.catalogue.Attestation.Page("13")))).toOption.get
    assertEquals(patched, first.modify(_.note).setTo(Some(com.histmersion.api.catalogue.Attestation.Note("changed"))).modify(_.page).setTo(Some(com.histmersion.api.catalogue.Attestation.Page("13"))))
    assert(
      a.patch(Attestation.Id(third.id.value + 100), Attestation.Patch(note = Some(com.histmersion.api.catalogue.Attestation.Note("x")))).left.exists(_.message.value.contains("No attestation"))
    )
    assert(a.delete(second.id))
    assert(!a.delete(second.id))
    assertEquals(a.list(None, None, None, com.histmersion.PageSize(10), com.histmersion.Offset(0)).attestations.map(_.id), List(first.id, third.id))
  }
