package com.histmersion.cli

import com.histmersion.api.given

import java.nio.file.{Files, Path}
import java.util.Comparator

class DirSizeSuite extends munit.FunSuite:

  private val tmp = FunFixture[Path](
    _ => Files.createTempDirectory("histmersion-dirsize"),
    d => {
      val s = Files.walk(d);
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

  tmp.test("a tree's regular files are summed, directories walked to any depth"): d =>
    file(d.resolve("a.bin"), 100)
    file(d.resolve("x/b.bin"), 200)
    file(d.resolve("x/y/z/c.bin"), 300)
    val r = DirSize.measure(d, DirSize.Budget.unlimited)
    assertEquals((r.bytes, r.files, r.exists, r.partial, r.unreadable), (600L, 3L, true, false, 0))

  tmp.test("a missing directory is not there — zero, never an error"): d =>
    val r = DirSize.measure(d.resolve("nope"), DirSize.Budget.unlimited)
    assertEquals(r, DirSize.Result(0L, 0L, exists = false))

  tmp.test("a regular file as the root is its own size"): d =>
    val f = file(d.resolve("one.pmtiles"), 1234)
    assertEquals(DirSize.measure(f, DirSize.Budget.unlimited), DirSize.Result(1234L, 1L, exists = true))

  tmp.test("links inside the tree are neither followed nor counted; a loop cannot hang the walk"): d =>
    val big = file(d.resolve("elsewhere/big.bin"), 10000).getParent
    val root = Files.createDirectories(d.resolve("root"))
    file(root.resolve("own.bin"), 10)
    Files.createSymbolicLink(root.resolve("to-big"), big)
    Files.createSymbolicLink(root.resolve("to-file"), big.resolve("big.bin"))
    Files.createSymbolicLink(root.resolve("loop"), root)
    val r = DirSize.measure(root, DirSize.Budget.unlimited)
    assertEquals((r.bytes, r.files, r.partial), (10L, 1L, false))

  tmp.test("the root itself may be a link (a configured directory moved to another disk) and is followed"): d =>
    file(d.resolve("real/a.bin"), 50)
    val link = Files.createSymbolicLink(d.resolve("link"), d.resolve("real"))
    assertEquals(DirSize.measure(link, DirSize.Budget.unlimited).bytes, 50L)

  tmp.test("the entry cap stops the walk: partial, with a lower bound"): d =>
    (1 to 50).foreach(i => file(d.resolve(f"f$i%02d.bin"), 10))
    val b = DirSize.Budget(10, Long.MaxValue)
    val r = DirSize.measure(d, b)
    assert(r.partial, r)
    assert(r.bytes < 500L && r.files < 50L, r)
    assert(b.exhausted)
    // the budget is shared: the next walk of the same measurement stops at once
    val next = DirSize.measure(d, b)
    assert(next.partial && next.bytes == 0L, next)

  tmp.test("the time cap stops the walk too"): d =>
    (1 to 20).foreach(i => file(d.resolve(s"f$i.bin"), 10))
    // a clock that passes the deadline after five reads
    var t = 0L
    val b = DirSize.Budget(Long.MaxValue, 5L, () => { t += 1; t })
    val r = DirSize.measure(d, b)
    assert(r.partial, r)
    assert(r.files < 20L, r)

  tmp.test("an unreadable directory is skipped and counted, the rest is measured"): d =>
    file(d.resolve("ok/a.bin"), 10)
    val locked = Files.createDirectories(d.resolve("locked"))
    file(locked.resolve("secret.bin"), 99)
    val perms = java.nio.file.attribute.PosixFilePermissions.fromString("---------")
    Files.setPosixFilePermissions(locked, perms)
    try
      assume(!Files.isReadable(locked), "running as a user who reads everything (root)")
      val r = DirSize.measure(d, DirSize.Budget.unlimited)
      assertEquals(r.bytes, 10L)
      assertEquals(r.unreadable, 1)
    finally Files.setPosixFilePermissions(locked, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"))

  tmp.test("a directory not made yet is on its nearest existing parent's volume"): d =>
    val v = DirSize.volume(d.resolve("not/made/yet"))
    assert(v.isDefined)
    assertEquals(v.map(_.id), DirSize.volume(d).map(_.id))
    assert(v.forall(x => x.totalBytes > 0 && x.freeBytes >= 0 && x.freeBytes <= x.totalBytes), v)

  tmp.test("children are listed by name; a missing directory has none"): d =>
    file(d.resolve("b/x"), 1); file(d.resolve("a"), 1)
    assertEquals(DirSize.children(d).map(_.getFileName.toString), List("a", "b"))
    assertEquals(DirSize.children(d.resolve("none")), Nil)
