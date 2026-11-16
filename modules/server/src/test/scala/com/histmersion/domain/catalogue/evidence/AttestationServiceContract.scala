package com.histmersion
package domain.catalogue.evidence

import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.catalogue.CatalogueStoreSuite
import com.histmersion.api.catalogue.Attestation
import com.histmersion.api.library.Citekey

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
        Citekey("zzat2020_one"),
        Attestation.TargetType("place"),
        Attestation.TargetId("zz_hill"),
        Some(Attestation.Quote("the hill")),
        Some(Attestation.Prefix("on ")),
        Some(Attestation.Suffix(" stood")),
        Some(Attestation.CharStart(3)),
        Some(Attestation.CharEnd(11)),
        Some(Attestation.ChunkOrd(0)),
        Some(Attestation.Page("12")),
        Some(Attestation.Canvas("https://iiif.test/c1")),
        Some(Attestation.Xywh("1,2,3,4")),
        Some(Attestation.CtsUrn("urn:cts:x")),
        Some(Attestation.Note("n"))
      )
    )
    val second = a.create(
      Attestation.Put(
        Citekey("zzat2020_one"),
        Attestation.TargetType("event"),
        Attestation.TargetId("zz_battle"),
        quote = Some(Attestation.Quote("the battle"))
      )
    )
    val third = a.create(
      Attestation.Put(Citekey("zzat2021_two"), Attestation.TargetType("place"), Attestation.TargetId("zz_hill"))
    )
    assert(first.id.value < second.id.value && second.id.value < third.id.value)
    assertEquals(a.get(first.id), Some(first))
    assertEquals(
      (first.quote, first.charEnd, first.ctsUrn, first.note),
      (Some("the hill"), Some(11), Some("urn:cts:x"), Some("n"))
    )
    assertEquals(a.get(Attestation.Id(third.id.value + 100)), None)
    val all = a.list(None, None, None, PageSize(10), Offset(0))
    assertEquals(((all.total.value: Int), all.attestations.map(_.id)), (3, List(first.id, second.id, third.id)))
    assertEquals(
      a.list(Some(com.histmersion.domain.library.Citekey("zzat2020_one")), None, None, PageSize(10), Offset(0))
        .attestations
        .map(_.id),
      List(first.id, second.id)
    )
    assertEquals(
      a.list(
        None,
        Some(Attestation.TargetType("place")),
        Some(Attestation.TargetId("zz_hill")),
        PageSize(10),
        Offset(0)
      ).attestations
        .map(_.id),
      List(first.id, third.id)
    )
    assertEquals(
      (a.list(
        Some(com.histmersion.domain.library.Citekey.assume(" ")),
        Some(Attestation.TargetType("")),
        None,
        PageSize(10),
        Offset(0)
      ).total
        .value: Int),
      3
    )
    val page = a.list(None, None, None, PageSize(1), Offset(1))
    assertEquals(
      ((page.total.value: Int), page.attestations.map(_.id), (page.offset.value: Int)),
      (3, List(second.id), 1)
    )
    assertEquals(a.list(None, None, None, PageSize.assume(0), Offset.assume(-5)).attestations.map(_.id), List(first.id))
    val patched = a
      .patch(first.id, Attestation.Patch(note = Some(Attestation.Note("changed")), page = Some(Attestation.Page("13"))))
      .toOption
      .get
    assertEquals(
      patched,
      first.modify(_.note).setTo(Some(Attestation.Note("changed"))).modify(_.page).setTo(Some(Attestation.Page("13")))
    )
    assert(
      a.patch(Attestation.Id(third.id.value + 100), Attestation.Patch(note = Some(Attestation.Note("x"))))
        .left
        .exists(_.problems.sentences.mkString("; ").contains("No attestation"))
    )
    assert(a.delete(second.id))
    assert(!a.delete(second.id))
    assertEquals(a.list(None, None, None, PageSize(10), Offset(0)).attestations.map(_.id), List(first.id, third.id))
  }
