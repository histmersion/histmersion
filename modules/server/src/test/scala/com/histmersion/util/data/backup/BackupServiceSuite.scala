package com.histmersion.util.data.backup

import com.histmersion.api.Write
import com.histmersion.api.backup.Backup
import com.histmersion.server.Deps
import com.histmersion.util.storage.{Location, Storage}

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.util.storage.Store

/** The backups in memory: named, listed, pruned and refused as the database implementation does. */
class BackupServiceSuite extends munit.FunSuite:

  private def fixture(keep: Int, rows: Int = 0) =
    val store = Storage.InMemory().store(Location.Backups)
    (store, BackupService.InMemory(store, keep, Deps.data, AtomicReference(rows)))

  test("dumps in one second get suffixes; the oldest beyond keep are pruned; the listing is newest first"):
    val (store, svc) = fixture(keep = 2)
    val at = Instant.parse("2026-10-31T10:00:00Z")
    val first = svc.create(at).fold(e => fail(e.message), identity)
    val second = svc.create(at).fold(e => fail(e.message), identity)
    val third = svc.create(at.plusSeconds(1)).fold(e => fail(e.message), identity)
    assertEquals(
      (first.file, second.file),
      ("histmersion-20261031T100000Z.dump", "histmersion-20261031T100000Z-2.dump")
    )
    assertEquals(third.pruned, List(first.file))
    assertEquals(svc.list.map(_.map(_.file)), Right(List(third.file, second.file)))
    assert(third.sha256.length == 64, third)
    store.put(Store.Key.applyUnsafe("notes.txt"), Array[Byte](1)): Unit
    assertEquals(svc.list.map(_.size), Right(2))

  test("only a listed, syntactically safe name resolves; a restore of it changes nothing here"):
    val (_, svc) = fixture(keep = 0)
    val made = svc.create(Instant.parse("2026-10-31T10:00:00Z")).fold(e => fail(e.message), identity)
    assertEquals(svc.resolve(made.file).map(_.value: String), Right(made.file))
    assertEquals(svc.resolve("histmersion-20260101T000000Z.dump").left.map(_.code), Left(Some(Write.CodeNotFound)))
    assert(svc.resolve("../x.dump").isLeft)
    assertEquals(svc.resolve(made.file).flatMap(svc.restore), Right((0L, Nil)))
    assert(svc.archive(made.file).isLeft, "a dump in memory is no file to hand out")

  test("a restore over guarded rows is refused, naming the registry's guarded records"):
    val (_, svc) = fixture(keep = 0, rows = 3)
    assertEquals(svc.guardedRows, 3)
    assertEquals(
      svc.refusal(3).message,
      "the database still holds 3 source(s); restoring would drop them — re-run with force"
    )
    assertEquals(Backup.safeName("histmersion-20261031T100000Z.dump"), Right("histmersion-20261031T100000Z.dump"))
