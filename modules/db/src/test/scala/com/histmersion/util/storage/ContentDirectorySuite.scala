package com.histmersion
package util.storage

import com.histmersion.testsupport.ComparedByValue.given

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import com.histmersion.lint.{tolerated, LintRule}

/** A content-addressed directory: found by digest, stored only when the content is its name, an unsafe extension
  * replaced; and the bytes of a file or an array read again from the start.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class ContentDirectorySuite extends munit.FunSuite:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def sha(b: ByteSource.Content) =
    ContentStore.hex(ContentStore.Digest(java.security.MessageDigest.getInstance("SHA-256").digest(b.value)))

  test("stored under its digest, found by it, read back; a wrong digest is refused and leaves nothing"):
    val dir = Files.createTempDirectory("histmersion-contents")
    val store = ContentDirectory(dir)
    val data = ByteSource.Content("an original".getBytes(UTF_8))
    assertEquals(store.find(sha(data)), None)
    assert(
      store.store(sha(data), ContentStore.Ext("pdf"), java.io.ByteArrayInputStream("not it".getBytes(UTF_8))).isLeft
    )
    assertEquals(Files.list(dir).count(), 0L)
    assertEquals(store.store(sha(data), ContentStore.Ext("PDF"), java.io.ByteArrayInputStream(data.value)), Right(()))
    val (file, ext) = store.find(sha(data)).get
    assertEquals(ext, "pdf")
    assertEquals(file.size, data.value.length.toLong)
    assertEquals(file.read(_.readAllBytes()).toList, data.value.toList)
    assertEquals(
      store.store(sha(data), ContentStore.Ext("txt"), java.io.ByteArrayInputStream(data.value)),
      Right(())
    ) // already there
    assertEquals(Files.list(dir).count(), 1L)

  test("an extension is kept only when short and alphanumeric"):
    assertEquals(ContentStore.safeExt(ContentStore.Ext(" PDF ")), "pdf")
    assertEquals(ContentStore.safeExt(ContentStore.Ext("../evil")), "bin")
    assertEquals(ContentStore.safeExt(ContentStore.Ext("")), "bin")
    assertEquals(ContentStore.safeExt(ContentStore.Ext("abcdefghi")), "bin")

  test("bytes in memory read from the start each time"):
    val source = ByteSource.bytes(ByteSource.Content("abc".getBytes(UTF_8)))
    assertEquals(source.size, 3L)
    assertEquals(source.read(in => String(in.readAllBytes(), UTF_8)), "abc")
    assertEquals(source.read(in => String(in.readAllBytes(), UTF_8)), "abc")
