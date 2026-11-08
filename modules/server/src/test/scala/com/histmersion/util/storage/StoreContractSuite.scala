package com.histmersion
package util.storage

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.{ByteCount, ByteOffset}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.time.Instant
import scala.util.Using
import com.histmersion.util.basemap.PmTiles

/** What every [[Store]] does, run on a store on disk and on one in memory: the in-memory store is what tests of the
  * code given a store run on, so it must answer as the disk does.
  */
abstract class StoreContractSuite extends munit.FunSuite:
  def fresh(): Store

  private def key(s: String) = Store.Key.applyUnsafe(s)
  private def utf(s: String) = ByteSource.Content(s.getBytes(UTF_8))

  test("put, get, stat, list and walk"):
    val s = fresh()
    assert(s.put(key("a/b/one.txt"), utf("one")).isRight)
    assert(s.write(key("a/two.bin"), ByteSource.Content(Array[Byte](1, 2, 3))).isRight)
    assertEquals(s.get(key("a/b/one.txt")).map(_.text()), Some("one"))
    assertEquals(s.stat(key("a/two.bin")).map(st => (st.kind, st.size.value: Long)), Some((Stored.Kind.File, 3L)))
    assert(s.isDirectory(key("a/b")) && s.isFile(key("a/two.bin")) && !s.exists(key("a/none")))
    assertEquals(
      s.list(key("a")).map(st => (st.key.value: String, st.kind)),
      List("a/b" -> Stored.Kind.Directory, "a/two.bin" -> Stored.Kind.File)
    )
    assertEquals(s.walk().map(st => st.key.value: String), List("a/b/one.txt", "a/two.bin"))
    assertEquals(s.size.value: Long, 6L)
    assertEquals(s.get(key("a/none")), None)
    assertEquals(s.list(key("nowhere")), Nil)

  test("a sub-store sees its keys relative to its prefix"):
    val s = fresh()
    val sub = s.sub(key("cache"))
    assert(sub.put(key("t/1.webp"), utf("x")).isRight)
    assertEquals(s.walk().map(st => st.key.value: String), List("cache/t/1.webp"))
    assertEquals(sub.walk().map(st => st.key.value: String), List("t/1.webp"))
    assertEquals(sub.deleteTree(Store.Key.Root).map(_.value: Long), Some(1L))
    assertEquals(s.walk(), Nil)

  test("a reservation is invisible until committed, and gone when discarded"):
    val s = fresh()
    val r = s.reserve(key("out.pmtiles")).toOption.get
    Using.resource(r.output())(_.write(utf("0123456789").value))
    r.writeAt(ByteOffset.applyUnsafe(2L), utf("ab"))
    assertEquals(r.size.value: Long, 10L)
    assert(!s.exists(key("out.pmtiles")))
    assert(r.commit().isRight)
    r.close()
    assertEquals(s.get(key("out.pmtiles")).map(_.text()), Some("01ab456789"))
    val d = s.reserve(key("gone")).toOption.get
    Using.resource(d.output())(_.write(utf("x").value))
    d.close()
    assertEquals(s.walk().map(st => st.key.value: String), List("out.pmtiles"))

  test("positional reads, streams and digests"):
    val s = fresh()
    s.put(key("f"), utf("hello world")): Unit
    val b = s.get(key("f")).get
    assertEquals(String(b.readAt(ByteOffset.applyUnsafe(6L), PmTiles.ByteLength(5)).value, UTF_8), "world")
    Using.resource(b.reader()) { r =>
      assertEquals(r.size.value: Long, 11L)
      assertEquals(String(r.readUpTo(ByteOffset.applyUnsafe(6L), PmTiles.ByteLength(100)).value, UTF_8), "world")
      intercept[java.io.EOFException](r.read(ByteOffset.applyUnsafe(6L), PmTiles.ByteLength(100)))
    }
    assertEquals(Using.resource(b.open())(in => String(in.readAllBytes(), UTF_8)), "hello world")
    assertEquals(b.sha256().value: String, "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9")
    assertEquals(b.mapped().remaining(), 11)

  test("move, copy, delete, touch"):
    val s = fresh()
    s.put(key("a"), utf("A")): Unit
    assert(s.copy(key("a"), key("b")).isRight)
    assert(s.move(key("b"), key("d/c")).isRight)
    assertEquals(s.walk().map(st => st.key.value: String), List("a", "d/c"))
    assert(s.move(key("none"), key("x")).isLeft)
    val at = Instant.parse("2020-01-01T00:00:00Z")
    s.touch(key("a"), at)
    assertEquals(s.stat(key("a")).map(_.modified), Some(at))
    assert(s.delete(key("a")) && !s.delete(key("a")))
    assertEquals(s.deleteTree(key("nowhere")), None)

  test("a reservation streamed, then overwritten in place at its head, replaces the file at its key whole"):
    val s = fresh()
    s.put(key("t/x.pmtiles"), utf("an older archive, longer")): Unit
    val r = s.reserve(key("t/x.pmtiles")).toOption.get
    Using.resource(r.output()) { out => out.write(utf("....").value); out.write(utf("body").value) }
    assertEquals(r.size.value: Long, 8L)
    r.writeAt(ByteOffset.applyUnsafe(0L), utf("head"))
    assertEquals(s.get(key("t/x.pmtiles")).map(_.text()), Some("an older archive, longer"))
    assertEquals(r.commit().map(_.size.value: Long), Right(8L))
    r.close()
    assertEquals(s.get(key("t/x.pmtiles")).map(_.text()), Some("headbody"))
    assertEquals(s.walk().map(st => st.key.value: String), List("t/x.pmtiles"))

  test("a sub-store's touch is the modification time its walk reports"):
    val s = fresh()
    val cache = s.sub(key("cache/tiles"))
    cache.put(key("1/0/0.webp"), utf("x")): Unit
    val at = Instant.parse("2021-06-01T12:00:00Z")
    cache.touch(key("1/0/0.webp"), at)
    assertEquals(cache.walk().map(st => (st.key.value: String, st.modified)), List(("1/0/0.webp", at)))
    assertEquals(s.walk(key("cache")).map(_.modified), List(at))

  test("prune: the least recently used first, down to what may be kept; or what is older than a time"):
    val s = fresh()
    List("old" -> "2020-01-01T00:00:00Z", "mid" -> "2021-01-01T00:00:00Z", "new" -> "2022-01-01T00:00:00Z").foreach {
      (k, t) =>
        s.put(key(k), utf("1234")): Unit
        s.touch(key(k), Instant.parse(t))
    }
    assertEquals(
      s.prune(PruneRule.LeastRecentlyUsed(ByteCount.applyUnsafe(5L))).removed.map(st => st.key.value: String),
      List("old", "mid")
    )
    assertEquals(s.walk().map(st => st.key.value: String), List("new"))
    assertEquals(s.prune(PruneRule.OlderThan(Instant.parse("2023-01-01T00:00:00Z"))).freed.value: Long, 4L)

  test("measure: the files below the store, by bytes and count; a sub-store not made yet is absent"):
    val s = fresh()
    s.put(key("a.bin"), ByteSource.Content(Array.fill[Byte](100)(1))): Unit
    s.put(key("x/y/b.bin"), ByteSource.Content(Array.fill[Byte](200)(1))): Unit
    val r = s.measure(MeasureBudget.unlimited)
    assertEquals((r.bytes.value: Long, r.files, r.exists, r.partial), (300L, 2L, true, false))
    assertEquals(s.sub(key("x")).measure(MeasureBudget.unlimited).bytes.value: Long, 200L)
    assertEquals(s.sub(key("nope")).measure(MeasureBudget.unlimited), Store.Measure.absent)

  test("measure: a budget that runs out stops the walk, partial, and is shared by the next walk"):
    val s = fresh()
    (1 to 30).foreach(i => s.put(key(f"f$i%02d.bin"), ByteSource.Content(Array.fill[Byte](10)(1))): Unit)
    val b = MeasureBudget(MeasureBudget.MaxEntries(10), MeasureBudget.DeadlineNanos(Long.MaxValue))
    val r = s.measure(b)
    assert(r.partial.value && r.files.value < 30L && r.bytes.value < 300L, r)
    assert(b.exhausted)
    assert(s.measure(b).partial.value)

  test("names: what a directory holds directly, by name; none for a missing one"):
    val s = fresh()
    s.put(key("b/x"), ByteSource.Content(Array[Byte](1))): Unit
    s.put(key("a"), ByteSource.Content(Array[Byte](1))): Unit
    assertEquals(s.names(), List("a", "b"))
    assertEquals(s.names(key("nowhere")), Nil)
    assert(!s.isLink(key("a")) && !s.isExecutable(key("a")))

  test("a reservation with a suffixed temporary name commits over its key"):
    val s = fresh()
    val r = s.reserve(key("d/x.dump"), TempName.Suffixed(TempName.Suffixed.Suffix(".part"))).toOption.get
    Using.resource(r.output())(_.write(utf("dump").value))
    assert(!s.isFile(key("d/x.dump")))
    assertEquals(r.commit().map(_.size.value: Long), Right(4L))
    assertEquals(s.get(key("d/x.dump")).map(_.text()), Some("dump"))
    assertEquals(s.names(key("d")), List("x.dump"))

class DiskStoreSuite extends StoreContractSuite:
  def fresh(): Store =
    Storage(_ => Some(Files.createTempDirectory("histmersion-store")), () => Files.createTempDirectory("t"))
      .store(Location.Basemap)

  test("on disk: a key is a path below the root; a symbolic link is written through when asked"):
    val dir = Files.createTempDirectory("histmersion-store")
    val s = Storage(_ => Some(dir), () => dir).store(Location.UserConfig)
    assertEquals(s.where(Store.Key.applyUnsafe("x/y")), dir.resolve("x/y").toString)
    val real = Files.writeString(dir.resolve("real.conf"), "old")
    Files.createSymbolicLink(dir.resolve("link.conf"), real)
    val r = s.reserve(Store.Key.applyUnsafe("link.conf"), TempName.Unique, throughLink = Store.ThroughLink(true)).toOption.get
    Using.resource(r.output())(_.write("new".getBytes(UTF_8)))
    assert(r.onDisk.exists(p => p.getFileName.toString.startsWith(".link.conf.")))
    assert(r.commit(keepPermissions = Store.KeepPermissions(true)).isRight)
    assert(Files.isSymbolicLink(dir.resolve("link.conf")))
    assertEquals(Files.readString(real), "new")
    assert(s.usableSpace.value > 0)

  test("on disk: a reservation's suffixed file is the key's name and the suffix, beside it"):
    val dir = Files.createTempDirectory("histmersion-store")
    val r = Storage.at(Location.Backups, dir).reserve(Store.Key.applyUnsafe("a.dump"), TempName.Suffixed(TempName.Suffixed.Suffix(".part")))
    assertEquals(r.flatMap(_.onDisk).map(_.toString), Right(dir.resolve("a.dump.part").toString))
    r.foreach(_.close())

  test("a store at a directory is the store the storage gives for a location placed there"):
    val dir = Files.createTempDirectory("histmersion-store")
    val at = Storage.at(Location.Basemap, dir)
    at.put(Store.Key.applyUnsafe("vector.pmtiles"), ByteSource.Content("v".getBytes(UTF_8))): Unit
    val placed = Storage(_ => Some(dir), () => dir).store(Location.Basemap)
    assertEquals(placed.get(Store.Key.applyUnsafe("vector.pmtiles")).map(_.text()), Some("v"))
    assertEquals((at.location, at.where()), (Location.Basemap, dir.toString))

  test("a scratch store is removed when it closes"):
    val base = Files.createTempDirectory("histmersion-scratch")
    val storage = Storage(_ => None, () => base)
    val scratch = storage.scratch(Storage.ScratchPurpose("histmersion-test-"))
    scratch.store.put(Store.Key.applyUnsafe("a/b"), ByteSource.Content(Array[Byte](1))): Unit
    assertEquals(Files.list(base).count(), 1L)
    scratch.close()
    assertEquals(Files.list(base).count(), 0L)
    assert(storage.find(Location.Plugins).isEmpty)
    intercept[IllegalStateException](storage.store(Location.Plugins))

class MemoryStoreSuite extends StoreContractSuite:
  def fresh(): Store = Storage.InMemory().store(Location.Basemap)

  test("in memory: nothing is on disk, and each location is one store for everyone holding the storage"):
    val storage = Storage.InMemory()
    storage.store(Location.Spool).put(Store.Key.applyUnsafe("x"), ByteSource.Content(Array[Byte](1))): Unit
    assert(storage.store(Location.Spool).isFile(Store.Key.applyUnsafe("x")))
    assert(storage.store(Location.Spool).onDisk(Store.Key.applyUnsafe("x")).isLeft)
    assertEquals(storage.store(Location.Spool).volume, None)
