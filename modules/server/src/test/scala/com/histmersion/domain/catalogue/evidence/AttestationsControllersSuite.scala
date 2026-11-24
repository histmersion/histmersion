package com.histmersion
package domain.catalogue.evidence

import com.histmersion.util.problem.Text.{sentence, sentences}
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
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given

/** The attestations' controllers over the in-memory service, no server and no database: an old citekey and an old place
  * id resolved before they are stored, an unknown target kind and a missing source refused, a finding id kept as given,
  * the list narrowed by an old id, the page size the limit makes, every write refused under the lock.
  */
class AttestationsControllersSuite extends munit.FunSuite:
  private val renamed: Map[(AliasKind, AttestationsControllers.Alias), AttestationsControllers.Alias] = Map(
    (AliasKinds.SourceRecords, AttestationsControllers.Alias("zzold1990_x")) -> AttestationsControllers.Alias(
      "zznew1990_x"
    ),
    (AliasKinds.PlaceRecords, AttestationsControllers.Alias("zz_old_hill")) -> AttestationsControllers.Alias("zz_hill")
  )
  private def current(k: AliasKind, id: AttestationsControllers.Alias) = renamed.getOrElse((k, id), id)

  private val sources = new Passage.Source:
    def canonical(citekey: Citekey): Option[Citekey] = Option.when(citekey == Citekey("zznew1990_x"))(citekey)
    def naming(name: Title, limit: PageSize): List[Citekey] = Nil
    def reader(citekey: Citekey): PassageReader = (_, _, _, _, _, _) => Left(Passage.Text("no text"))

  private def controllers(gate: WriteGate = WriteGate()) =
    AttestationsControllers(
      AttestationService.InMemory(AtomicReference(CatalogueState.empty)),
      sources,
      current,
      limit => PageSize.applyUnsafe(limit.getOrElse(PageSize(25)).value.min(100)),
      gate
    )

  test("old identifiers resolve before they are stored; a kind is normalised; a finding id is kept"):
    val c = controllers()
    val a = c
      .create(
        Caller.Local,
        Attestation.Put(
          api.library.Citekey("zzold1990_x"),
          Attestation.TargetType(" Place "),
          Attestation.TargetId("zz_old_hill")
        )
      )
      .toOption
      .get
    assertEquals((a.citekey, a.targetType, a.targetId), ("zznew1990_x", "place", "zz_hill"))
    val f = c
      .create(
        Caller.Local,
        Attestation.Put(
          api.library.Citekey("zznew1990_x"),
          Attestation.TargetType("finding"),
          Attestation.TargetId("zz_old_hill")
        )
      )
      .toOption
      .get
    assertEquals(f.targetId, "zz_old_hill")
    assertEquals(
      c.list(
        Some(api.library.Citekey("zzold1990_x")),
        Some(Attestation.TargetType("place")),
        Some(Attestation.TargetId("zz_old_hill")),
        None,
        None
      ).attestations,
      List(a)
    )
    assertEquals(c.list(None, None, None, Some(PageSize(1)), None).attestations, List(a))
    assertEquals(c.get(f.id).map(_.id), Right(f.id))
    assert(c.get(Attestation.Id(999)).left.exists(_.problems.sentences.mkString("; ") == "No attestation 999"))

  test("an unknown kind is invalid, a missing source not found; a held lock refuses every write"):
    val c = controllers()
    val bad = c.create(
      Caller.Local,
      Attestation.Put(
        api.library.Citekey("zznew1990_x"),
        Attestation.TargetType("planet"),
        Attestation.TargetId("x")
      )
    )
    assert(
      bad.left.exists(e =>
        e.problems.sentences.mkString("; ").startsWith("unknown attestation target 'planet'") && e.code.exists(
          _.value == "invalid"
        )
      )
    )
    val none = c.create(
      Caller.Local,
      Attestation.Put(
        api.library.Citekey("zzgone1900_y"),
        Attestation.TargetType("place"),
        Attestation.TargetId("zz_hill")
      )
    )
    assertEquals(none.left.map(_.problems.head).left.map(_.sentence), Left("No source 'zzgone1900_y'"))
    assert(
      c.delete(Caller.Local, Attestation.Id(5)).left.exists(_.problems.sentences.mkString("; ") == "No attestation 5")
    )
    val gate = WriteGate()
    val locked = controllers(gate)
    val held = gate.exclusively(WriteGate.OperationName("restore"))(
      Right(
        List(
          locked
            .create(
              Caller.Local,
              Attestation.Put(
                api.library.Citekey("zznew1990_x"),
                Attestation.TargetType("place"),
                Attestation.TargetId("zz_hill")
              )
            )
            .isLeft,
          locked.patch(Caller.Local, Attestation.Id(1), Attestation.Patch()).isLeft,
          locked.delete(Caller.Local, Attestation.Id(1)).isLeft
        )
      )
    )
    assertEquals(held, Right(List(true, true, true)))
