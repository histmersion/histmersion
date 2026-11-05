package com.histmersion.domain.catalogue.evidence

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
        "zzat2020_one",
        "place",
        "zz_hill",
        Some("the hill"),
        Some("on "),
        Some(" stood"),
        Some(3),
        Some(11),
        Some(0),
        Some("12"),
        Some("https://iiif.test/c1"),
        Some("1,2,3,4"),
        Some("urn:cts:x"),
        Some("n")
      )
    )
    val second = a.create(Attestation.Put("zzat2020_one", "event", "zz_battle", quote = Some("the battle")))
    val third = a.create(Attestation.Put("zzat2021_two", "place", "zz_hill"))
    assert(first.id < second.id && second.id < third.id)
    assertEquals(a.get(first.id), Some(first))
    assertEquals(
      (first.quote, first.charEnd, first.ctsUrn, first.note),
      (Some("the hill"), Some(11), Some("urn:cts:x"), Some("n"))
    )
    assertEquals(a.get(third.id + 100), None)
    val all = a.list(None, None, None, 10, 0)
    assertEquals(((all.total.value: Int), all.attestations.map(_.id)), (3, List(first.id, second.id, third.id)))
    assertEquals(a.list(Some("zzat2020_one"), None, None, 10, 0).attestations.map(_.id), List(first.id, second.id))
    assertEquals(a.list(None, Some("place"), Some("zz_hill"), 10, 0).attestations.map(_.id), List(first.id, third.id))
    assertEquals((a.list(Some(" "), Some(""), None, 10, 0).total.value: Int), 3)
    val page = a.list(None, None, None, 1, 1)
    assertEquals(((page.total.value: Int), page.attestations.map(_.id), (page.offset.value: Int)), (3, List(second.id), 1))
    assertEquals(a.list(None, None, None, 0, -5).attestations.map(_.id), List(first.id))
    val patched = a.patch(first.id, Attestation.Patch(note = Some("changed"), page = Some("13"))).toOption.get
    assertEquals(patched, first.modify(_.note).setTo(Some("changed")).modify(_.page).setTo(Some("13")))
    assert(
      a.patch(third.id + 100, Attestation.Patch(note = Some("x"))).left.exists(_.message.contains("No attestation"))
    )
    assert(a.delete(second.id))
    assert(!a.delete(second.id))
    assertEquals(a.list(None, None, None, 10, 0).attestations.map(_.id), List(first.id, third.id))
  }
