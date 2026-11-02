package com.histmersion.domain.catalogue.evidence

import com.histmersion.api.aliases.AliasKinds

import com.histmersion.api.catalogue.AttestationPut
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.domain.catalogue.evidence.passages.{PassageReader, PassageSource}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.aliases.AliasKind

import java.util.concurrent.atomic.AtomicReference

/** The attestations' controllers over the in-memory service, no server and no database: an old citekey and an old place
  * id resolved before they are stored, an unknown target kind and a missing source refused, a finding id kept as given,
  * the list narrowed by an old id, the page size the limit makes, every write refused under the lock.
  */
class AttestationsControllersSuite extends munit.FunSuite:
  private val renamed: Map[(AliasKind, String), String] = Map(
    (AliasKinds.SourceRecords, "zzold1990_x") -> "zznew1990_x",
    (AliasKinds.PlaceRecords, "zz_old_hill") -> "zz_hill"
  )
  private def current(k: AliasKind, id: String) = renamed.getOrElse((k, id), id)

  private val sources = new PassageSource:
    def canonical(citekey: String): Option[String] = Option.when(citekey == "zznew1990_x")(citekey)
    def naming(name: String, limit: Int): List[String] = Nil
    def reader(citekey: String): PassageReader = (_, _, _, _, _, _) => Left("no text")

  private def controllers(gate: WriteGate = WriteGate()) =
    AttestationsControllers(
      AttestationService.InMemory(AtomicReference(CatalogueState.empty)),
      sources,
      current,
      limit => limit.getOrElse(25).min(100),
      gate
    )

  test("old identifiers resolve before they are stored; a kind is normalised; a finding id is kept"):
    val c = controllers()
    val a = c.create(Caller.Local, AttestationPut("zzold1990_x", " Place ", "zz_old_hill")).toOption.get
    assertEquals((a.citekey, a.targetType, a.targetId), ("zznew1990_x", "place", "zz_hill"))
    val f = c.create(Caller.Local, AttestationPut("zznew1990_x", "finding", "zz_old_hill")).toOption.get
    assertEquals(f.targetId, "zz_old_hill")
    assertEquals(c.list(Some("zzold1990_x"), Some("place"), Some("zz_old_hill"), None, None).attestations, List(a))
    assertEquals(c.list(None, None, None, Some(1), None).attestations, List(a))
    assertEquals(c.get(f.id).map(_.id), Right(f.id))
    assert(c.get(999).left.exists(_.message == "No attestation 999"))

  test("an unknown kind is invalid, a missing source not found; a held lock refuses every write"):
    val c = controllers()
    val bad = c.create(Caller.Local, AttestationPut("zznew1990_x", "planet", "x"))
    assert(bad.left.exists(e => e.message.startsWith("unknown target type 'planet'") && e.code.contains("invalid")))
    val none = c.create(Caller.Local, AttestationPut("zzgone1900_y", "place", "zz_hill"))
    assertEquals(none.left.map(_.message), Left("No source 'zzgone1900_y'"))
    assert(c.delete(Caller.Local, 5).left.exists(_.message == "No attestation 5"))
    val gate = WriteGate()
    val locked = controllers(gate)
    val held = gate.exclusively("restore")(
      Right(
        List(
          locked.create(Caller.Local, AttestationPut("zznew1990_x", "place", "zz_hill")).isLeft,
          locked.patch(Caller.Local, 1, com.histmersion.api.catalogue.AttestationPatch()).isLeft,
          locked.delete(Caller.Local, 1).isLeft
        )
      )
    )
    assertEquals(held, Right(List(true, true, true)))
