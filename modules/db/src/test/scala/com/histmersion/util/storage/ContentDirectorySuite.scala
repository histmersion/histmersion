package com.histmersion.util.storage

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

/** A content-addressed directory: found by digest, stored only when the content is its name, an unsafe extension
  * replaced; and the bytes of a file or an array read again from the start.
  */
class ContentDirectorySuite extends munit.FunSuite:

  private def sha(b: Array[Byte]) = ContentStore.hex(java.security.MessageDigest.getInstance("SHA-256").digest(b))

  test("stored under its digest, found by it, read back; a wrong digest is refused and leaves nothing"):
    val dir = Files.createTempDirectory("histmersion-contents")
    val store = ContentDirectory(dir)
    val data = "an original".getBytes(UTF_8)
    assertEquals(store.find(sha(data)), None)
    assert(store.store(sha(data), "pdf", java.io.ByteArrayInputStream("not it".getBytes(UTF_8))).isLeft)
    assertEquals(Files.list(dir).count(), 0L)
    assertEquals(store.store(sha(data), "PDF", java.io.ByteArrayInputStream(data)), Right(()))
    val (file, ext) = store.find(sha(data)).get
    assertEquals(ext, "pdf")
    assertEquals(file.size, data.length.toLong)
    assertEquals(file.read(_.readAllBytes()).toList, data.toList)
    assertEquals(store.store(sha(data), "txt", java.io.ByteArrayInputStream(data)), Right(())) // already there
    assertEquals(Files.list(dir).count(), 1L)

  test("an extension is kept only when short and alphanumeric"):
    assertEquals(ContentStore.safeExt(" PDF "), "pdf")
    assertEquals(ContentStore.safeExt("../evil"), "bin")
    assertEquals(ContentStore.safeExt(""), "bin")
    assertEquals(ContentStore.safeExt("abcdefghi"), "bin")

  test("bytes in memory read from the start each time"):
    val source = ByteSource.bytes("abc".getBytes(UTF_8))
    assertEquals(source.size, 3L)
    assertEquals(source.read(in => String(in.readAllBytes(), UTF_8)), "abc")
    assertEquals(source.read(in => String(in.readAllBytes(), UTF_8)), "abc")
