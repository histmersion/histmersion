package com.histmersion
package util.data.backup

import com.histmersion.api.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.RowCount
import com.histmersion.api.Write
import com.histmersion.api.backup.Backup
import com.histmersion.server.Deps
import com.histmersion.util.storage.{ByteSource, Location, Storage}

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.util.storage.Store

/** The backups in memory: named, listed, pruned and refused as the database implementation does. */
class BackupServiceSuite extends munit.FunSuite:

  private def fixture(keep: BackupService.InMemory.Keep, rows: RowCount = RowCount(0)) =
    val store = Storage.InMemory().store(Location.Backups)
    (store, BackupService.InMemory(store, keep, Deps.data, AtomicReference(rows)))

  test("dumps in one second get suffixes; the oldest beyond keep are pruned; the listing is newest first"):
    val (store, svc) = fixture(keep = BackupService.InMemory.Keep(2))
    val at = Instant.parse("2026-10-31T10:00:00Z")
    val first = svc.create(at).fold(e => fail(e.message.sentence), identity)
    val second = svc.create(at).fold(e => fail(e.message.sentence), identity)
    val third = svc.create(at.plusSeconds(1)).fold(e => fail(e.message.sentence), identity)
    assertEquals(
      (first.file, second.file),
      ("histmersion-20261031T100000Z.dump", "histmersion-20261031T100000Z-2.dump")
    )
    assertEquals(third.pruned.map(_.value: String), List(first.file.value: String))
    assertEquals(svc.list.map(_.map(_.file)), Right(List(third.file, second.file)))
    assert(third.sha256.value.length == 64, third)
    store.put(Store.Key.applyUnsafe("notes.txt"), ByteSource.Content(Array[Byte](1))): Unit
    assertEquals(svc.list.map(_.size), Right(2))

  test("only a listed, syntactically safe name resolves; a restore of it changes nothing here"):
    val (_, svc) = fixture(keep = BackupService.InMemory.Keep(0))
    val made = svc.create(Instant.parse("2026-10-31T10:00:00Z")).fold(e => fail(e.message.sentence), identity)
    assertEquals(svc.resolve(PreparedBackup.File(made.file.value)).map(_.value: String), Right(made.file))
    assertEquals(
      svc.resolve(PreparedBackup.File("histmersion-20260101T000000Z.dump")).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    assert(svc.resolve(PreparedBackup.File("../x.dump")).isLeft)
    assertEquals(svc.resolve(PreparedBackup.File(made.file.value)).flatMap(svc.restore), Right((0L, Nil)))
    assert(svc.archive(PreparedBackup.File(made.file.value)).isLeft, "a dump in memory is no file to hand out")

  test("a restore over guarded rows is refused, naming the registry's guarded records"):
    val (_, svc) = fixture(keep = BackupService.InMemory.Keep(0), rows = RowCount(3))
    assertEquals(svc.guardedRows, 3)
    assertEquals(
      (svc.refusal(RowCount(3)).message).sentence,
      "the database still holds 3 source(s); restoring would drop them — re-run with force"
    )
    assertEquals(
      Backup.safeName(Backup.Name("histmersion-20261031T100000Z.dump")),
      Right("histmersion-20261031T100000Z.dump")
    )
