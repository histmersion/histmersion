package com.histmersion
package util.storage

import com.histmersion.testsupport.ComparedByValue.given

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import com.histmersion.lint.{LintRule, tolerated}

/** The stored originals over a store, on disk and in memory alike: found by digest, stored only when the content is its
  * name, under a safe extension, never half written.
  */
class StoreContentsSuite extends munit.FunSuite:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def sha(b: ByteSource.Content) =
    ContentStore.hex(ContentStore.Digest(java.security.MessageDigest.getInstance("SHA-256").digest(b.value)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def cases(name: String, store: () => Store): Unit =
    test(s"$name: content-addressed — stored only when the content is its name, found by digest"):
      val s = store()
      val contents = Store.Contents(s)
      val data = ByteSource.Content("an original".getBytes(UTF_8))
      assertEquals(contents.find(sha(data)), None)
      assert(
        contents
          .store(sha(data), ContentStore.Ext("pdf"), java.io.ByteArrayInputStream("not it".getBytes(UTF_8)))
          .isLeft
      )
      assertEquals(contents.find(sha(data)), None)
      assertEquals(s.list().size, 0)
      assertEquals(
        contents.store(sha(data), ContentStore.Ext("../evil"), java.io.ByteArrayInputStream(data.value)),
        Right(())
      )
      assertEquals(contents.find(sha(data)).map(_._2), Some("bin"))
      assertEquals(
        contents.store(sha(data), ContentStore.Ext("pdf"), java.io.ByteArrayInputStream(data.value)),
        Right(())
      )
      assertEquals(s.list().map(_.key.value), List(s"${sha(data)}.bin"))
      assertEquals(contents.find(sha(data)).map(_._1.read(_.readAllBytes()).toList), Some(data.value.toList))

  cases("on disk", () => Storage.at(Location.IngestOriginals, Files.createTempDirectory("histmersion-originals")))
  cases("in memory", () => Storage.InMemory().store(Location.IngestOriginals))
