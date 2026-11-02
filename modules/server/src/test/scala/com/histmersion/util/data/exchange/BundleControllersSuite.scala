package com.histmersion.util.data.exchange

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.storage.{ContentDirectory, ContentStore}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.util.UUID
import com.histmersion.api.exchange.{Bundle, BundleBlobs, BundleInstance, BundleManifest, BundleReference}

/** The pure parts of the bundle endpoints, and the stored-originals directory a bundle reads and fills. No database.
  */
class BundleControllersSuite extends munit.FunSuite:

  test("since: an ISO-8601 moment with its offset, or nothing; anything else is a readable 400"):
    assertEquals(BundleControllers.since(None), Right(None))
    assertEquals(BundleControllers.since(Some("  ")), Right(None))
    assertEquals(
      BundleControllers.since(Some("2026-10-03T09:12:00.123456Z")),
      Right(Some("2026-10-03T09:12:00.123456Z"))
    )
    assert(BundleControllers.since(Some("yesterday")).left.exists(_.message.contains("ISO-8601")))
    assert(BundleControllers.since(Some("2026-10-03")).isLeft)

  test("the download is named after the instance and the moment it is as of"):
    val m = BundleManifest(
      Bundle.Format,
      1,
      "33",
      UUID.randomUUID(),
      BundleInstance(UUID.fromString("01910000-0000-7000-8000-0000000000aa"), Some("my work")),
      None,
      Some("2026-10-03T09:12:00.123456Z"),
      Nil,
      BundleBlobs(0, 0),
      BundleReference(0, "", 0, "")
    )
    assertEquals(BundleControllers.disposition(m), """attachment; filename="histmersion-my-work-20261003091200.hmx"""")
    assertEquals(
      BundleControllers.disposition(m.copy(instance = m.instance.copy(label = None), asOf = None)),
      """attachment; filename="histmersion-01910000-empty.hmx""""
    )

  test("an uploaded bundle is named by its sha256 alone — nothing else reaches the file system"):
    val h = "ab" * 32
    assertEquals(BundleControllers.sha256(h.toUpperCase), Right(h))
    assert(BundleControllers.sha256("../../etc/passwd").isLeft)
    assert(BundleControllers.sha256("ab" * 31).isLeft)
    assert(BundleControllers.sha256("g" * 64).isLeft)

  test("originals: content-addressed — stored only when the content is its name, found by digest"):
    val dir = Files.createTempDirectory("histmersion-originals")
    val o = ContentDirectory(dir)
    val data = "an original".getBytes(UTF_8)
    val sha = BundleFormat.sha256Hex(data)
    assertEquals(o.find(sha), None)
    assert(o.store(sha, "pdf", java.io.ByteArrayInputStream("not it".getBytes(UTF_8))).isLeft)
    assertEquals(o.find(sha), None)
    assertEquals(o.store(sha, "../evil", java.io.ByteArrayInputStream(data)), Right(()))
    assertEquals(o.find(sha).map(_._2), Some("bin")) // an unsafe extension is not used
    assertEquals(o.store(sha, "pdf", java.io.ByteArrayInputStream(data)), Right(())) // already there
    assertEquals(Files.list(dir).count(), 1L)
    assertEquals(ContentStore.safeExt("PDF"), "pdf")
