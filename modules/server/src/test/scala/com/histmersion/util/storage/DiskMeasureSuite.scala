package com.histmersion.util.storage

import java.nio.file.{Files, Path}
import java.util.Comparator

/** A store on disk measured by a bounded walk: what About › Storage reports of each directory. */
class DiskMeasureSuite extends munit.FunSuite:

  private val tmp = FunFixture[Path](
    _ => Files.createTempDirectory("histmersion-measure"),
    d => {
      val s = Files.walk(d)
      try
        s.sorted(Comparator.reverseOrder()).forEach { p =>
          val _ = Files.deleteIfExists(p)
        }
      finally s.close()
    }
  )

  private def file(p: Path, bytes: Int): Path =
    Files.createDirectories(p.getParent)
    Files.write(p, Array.fill[Byte](bytes)(1))

  private def at(p: Path): Store = Storage.at(Location.DataDirectory, p)
  private def measured(p: Path, b: MeasureBudget = MeasureBudget.unlimited) =
    val r = at(p).measure(b)
    (r.bytes.value: Long, r.files, r.exists, r.partial, r.unreadable)

  tmp.test("a tree's regular files are summed, directories walked to any depth"): d =>
    file(d.resolve("a.bin"), 100)
    file(d.resolve("x/b.bin"), 200)
    file(d.resolve("x/y/z/c.bin"), 300)
    assertEquals(measured(d), (600L, 3L, true, false, 0))

  tmp.test("a missing directory is not there — zero, never an error"): d =>
    assertEquals(at(d.resolve("nope")).measure(MeasureBudget.unlimited), StoreMeasure.absent)

  tmp.test("a regular file as the root is its own size"): d =>
    assertEquals(measured(file(d.resolve("one.pmtiles"), 1234)), (1234L, 1L, true, false, 0))

  tmp.test("links inside the tree are neither followed nor counted; a loop cannot hang the walk"): d =>
    val big = file(d.resolve("elsewhere/big.bin"), 10000).getParent
    val root = Files.createDirectories(d.resolve("root"))
    file(root.resolve("own.bin"), 10)
    Files.createSymbolicLink(root.resolve("to-big"), big)
    Files.createSymbolicLink(root.resolve("to-file"), big.resolve("big.bin"))
    Files.createSymbolicLink(root.resolve("loop"), root)
    assertEquals(measured(root), (10L, 1L, true, false, 0))
    assert(at(root).isLink(StoreKey.applyUnsafe("to-file")))

  tmp.test("the root itself may be a link (a configured directory moved to another disk) and is followed"): d =>
    file(d.resolve("real/a.bin"), 50)
    val link = Files.createSymbolicLink(d.resolve("link"), d.resolve("real"))
    assertEquals(measured(link)._1, 50L)

  tmp.test("the time cap stops the walk too"): d =>
    (1 to 20).foreach(i => file(d.resolve(s"f$i.bin"), 10))
    // a clock that passes the deadline after five reads
    var t = 0L
    val r = at(d).measure(MeasureBudget(Long.MaxValue, 5L, () => { t += 1; t }))
    assert(r.partial && r.files < 20L, r)

  tmp.test("an unreadable directory is skipped and counted, the rest is measured"): d =>
    file(d.resolve("ok/a.bin"), 10)
    val locked = Files.createDirectories(d.resolve("locked"))
    file(locked.resolve("secret.bin"), 99)
    Files.setPosixFilePermissions(locked, java.nio.file.attribute.PosixFilePermissions.fromString("---------"))
    try
      assume(!Files.isReadable(locked), "running as a user who reads everything (root)")
      val r = at(d).measure(MeasureBudget.unlimited)
      assertEquals((r.bytes.value: Long, r.unreadable), (10L, 1))
    finally Files.setPosixFilePermissions(locked, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"))

  tmp.test("a directory not made yet is on its nearest existing parent's volume"): d =>
    val v = at(d.resolve("not/made/yet")).volume
    assert(v.isDefined)
    assertEquals(v.map(_.id), at(d).volume.map(_.id))
    assert(v.forall(x => x.total.value > 0 && x.usable.value >= 0 && x.usable.value <= x.total.value), v)

  tmp.test("an executable is a file this process may run"): d =>
    val f = file(d.resolve("pg_dump"), 1)
    assert(!at(d).isExecutable(StoreKey.applyUnsafe("pg_dump")))
    val _ = f.toFile.setExecutable(true)
    assert(at(d).isExecutable(StoreKey.applyUnsafe("pg_dump")))
    assert(!at(d).isExecutable(StoreKey.applyUnsafe("absent")))
