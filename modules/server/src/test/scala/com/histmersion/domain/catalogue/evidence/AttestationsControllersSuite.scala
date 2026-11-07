package com.histmersion.domain.catalogue.evidence

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.aliases.AliasKinds

import com.histmersion.domain.{Caller, Title}
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.domain.catalogue.evidence.passages.PassageReader
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.aliases.AliasKind

import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.catalogue.Attestation
import com.histmersion.domain.catalogue.evidence.passages.Passage

/** The attestations' controllers over the in-memory service, no server and no database: an old citekey and an old place
  * id resolved before they are stored, an unknown target kind and a missing source refused, a finding id kept as given,
  * the list narrowed by an old id, the page size the limit makes, every write refused under the lock.
  */
class AttestationsControllersSuite extends munit.FunSuite:
  private val renamed: Map[(AliasKind, AttestationsControllers.Alias), AttestationsControllers.Alias] = Map(
    (AliasKinds.SourceRecords, AttestationsControllers.Alias("zzold1990_x")) -> AttestationsControllers.Alias("zznew1990_x"),
    (AliasKinds.PlaceRecords, AttestationsControllers.Alias("zz_old_hill")) -> AttestationsControllers.Alias("zz_hill")
  )
  private def current(k: AliasKind, id: AttestationsControllers.Alias) = renamed.getOrElse((k, id), id)

  private val sources = new Passage.Source:
    def canonical(citekey: Citekey): Option[Citekey] = Option.when(citekey == Citekey("zznew1990_x"))(citekey)
    def naming(name: Title, limit: com.histmersion.PageSize): List[Citekey] = Nil
    def reader(citekey: Citekey): PassageReader = (_, _, _, _, _, _) => Left(com.histmersion.domain.catalogue.evidence.passages.Passage.Text("no text"))

  private def controllers(gate: WriteGate = WriteGate()) =
    AttestationsControllers(
      AttestationService.InMemory(AtomicReference(CatalogueState.empty)),
      sources,
      current,
      limit => com.histmersion.PageSize.applyUnsafe(limit.getOrElse(com.histmersion.PageSize(25)).value.min(100)),
      gate
    )

  test("old identifiers resolve before they are stored; a kind is normalised; a finding id is kept"):
    val c = controllers()
    val a = c.create(Caller.Local, Attestation.Put(com.histmersion.api.library.Citekey("zzold1990_x"), com.histmersion.api.catalogue.Attestation.TargetType(" Place "), com.histmersion.api.catalogue.Attestation.TargetId("zz_old_hill"))).toOption.get
    assertEquals((a.citekey, a.targetType, a.targetId), ("zznew1990_x", "place", "zz_hill"))
    val f = c.create(Caller.Local, Attestation.Put(com.histmersion.api.library.Citekey("zznew1990_x"), com.histmersion.api.catalogue.Attestation.TargetType("finding"), com.histmersion.api.catalogue.Attestation.TargetId("zz_old_hill"))).toOption.get
    assertEquals(f.targetId, "zz_old_hill")
    assertEquals(c.list(Some(com.histmersion.api.library.Citekey("zzold1990_x")), Some(com.histmersion.api.catalogue.Attestation.TargetType("place")), Some(com.histmersion.api.catalogue.Attestation.TargetId("zz_old_hill")), None, None).attestations, List(a))
    assertEquals(c.list(None, None, None, Some(com.histmersion.PageSize(1)), None).attestations, List(a))
    assertEquals(c.get(f.id).map(_.id), Right(f.id))
    assert(c.get(com.histmersion.api.catalogue.Attestation.Id(999)).left.exists(_.message.value == "No attestation 999"))

  test("an unknown kind is invalid, a missing source not found; a held lock refuses every write"):
    val c = controllers()
    val bad = c.create(Caller.Local, Attestation.Put(com.histmersion.api.library.Citekey("zznew1990_x"), com.histmersion.api.catalogue.Attestation.TargetType("planet"), com.histmersion.api.catalogue.Attestation.TargetId("x")))
    assert(bad.left.exists(e => e.message.value.startsWith("unknown target type 'planet'") && e.code.contains("invalid")))
    val none = c.create(Caller.Local, Attestation.Put(com.histmersion.api.library.Citekey("zzgone1900_y"), com.histmersion.api.catalogue.Attestation.TargetType("place"), com.histmersion.api.catalogue.Attestation.TargetId("zz_hill")))
    assertEquals(none.left.map(_.message), Left("No source 'zzgone1900_y'"))
    assert(c.delete(Caller.Local, com.histmersion.api.catalogue.Attestation.Id(5)).left.exists(_.message.value == "No attestation 5"))
    val gate = WriteGate()
    val locked = controllers(gate)
    val held = gate.exclusively(com.histmersion.util.data.maintenance.WriteGate.OperationName("restore"))(
      Right(
        List(
          locked.create(Caller.Local, Attestation.Put(com.histmersion.api.library.Citekey("zznew1990_x"), com.histmersion.api.catalogue.Attestation.TargetType("place"), com.histmersion.api.catalogue.Attestation.TargetId("zz_hill"))).isLeft,
          locked.patch(Caller.Local, com.histmersion.api.catalogue.Attestation.Id(1), com.histmersion.api.catalogue.Attestation.Patch()).isLeft,
          locked.delete(Caller.Local, com.histmersion.api.catalogue.Attestation.Id(1)).isLeft
        )
      )
    )
    assertEquals(held, Right(List(true, true, true)))
