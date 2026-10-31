package com.histmersion.util.storage

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

/** The stored originals over a store, on disk and in memory alike: found by digest, stored only when the content is
  * its name, under a safe extension, never half written.
  */
class StoreContentsSuite extends munit.FunSuite:

  private def sha(b: Array[Byte]) = ContentStore.hex(java.security.MessageDigest.getInstance("SHA-256").digest(b))

  private def cases(name: String, store: () => Store): Unit =
    test(s"$name: content-addressed — stored only when the content is its name, found by digest"):
      val s = store()
      val contents = StoreContents(s)
      val data = "an original".getBytes(UTF_8)
      assertEquals(contents.find(sha(data)), None)
      assert(contents.store(sha(data), "pdf", java.io.ByteArrayInputStream("not it".getBytes(UTF_8))).isLeft)
      assertEquals(contents.find(sha(data)), None)
      assertEquals(s.list().size, 0)
      assertEquals(contents.store(sha(data), "../evil", java.io.ByteArrayInputStream(data)), Right(()))
      assertEquals(contents.find(sha(data)).map(_._2), Some("bin"))
      assertEquals(contents.store(sha(data), "pdf", java.io.ByteArrayInputStream(data)), Right(()))
      assertEquals(s.list().map(_.key.value), List(s"${sha(data)}.bin"))
      assertEquals(contents.find(sha(data)).map(_._1.read(_.readAllBytes()).toList), Some(data.toList))

  cases("on disk", () => Storage.at(Location.IngestOriginals, Files.createTempDirectory("histmersion-originals")))
  cases("in memory", () => Storage.InMemory().store(Location.IngestOriginals))
