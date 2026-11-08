package com.histmersion
package util.data.exchange

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.storage.{ContentDirectory, ContentStore}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.util.UUID
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.ByteCount

/** The pure parts of the bundle endpoints, and the stored-originals directory a bundle reads and fills. No database.
  */
class BundleControllersSuite extends munit.FunSuite:

  test("since: an ISO-8601 moment with its offset, or nothing; anything else is a readable 400"):
    assertEquals(BundleControllers.since(None), Right(None))
    assertEquals(BundleControllers.since(Some(BundleScope.Since.At("  "))), Right(None))
    assertEquals(
      BundleControllers.since(Some(BundleScope.Since.At("2026-10-03T09:12:00.123456Z"))),
      Right(Some("2026-10-03T09:12:00.123456Z"))
    )
    assert(BundleControllers.since(Some(BundleScope.Since.At("yesterday"))).left.exists(_.message.value.contains("ISO-8601")))
    assert(BundleControllers.since(Some(BundleScope.Since.At("2026-10-03"))).isLeft)

  test("the download is named after the instance and the moment it is as of"):
    val m = Bundle.Manifest(
      Bundle.Manifest.Format(Bundle.Format),
      Bundle.Manifest.FormatVersion(1),
      Bundle.Manifest.SchemaVersion("33"),
      UUID.randomUUID(),
      Bundle.Instance(UUID.fromString("01910000-0000-7000-8000-0000000000aa"), Some(Bundle.Instance.Label("my work"))),
      None,
      Some(Bundle.Manifest.AsOf("2026-10-03T09:12:00.123456Z")),
      Nil,
      Bundle.Blobs(Bundle.Blobs.Count(0), ByteCount(0)),
      Bundle.Reference(Bundle.Reference.Places(0), Bundle.Reference.PlacesDigest(""), Bundle.Reference.Periods(0), Bundle.Reference.PeriodsDigest(""))
    )
    assertEquals(BundleControllers.disposition(m), """attachment; filename="histmersion-my-work-20261003091200.hmx"""")
    assertEquals(
      BundleControllers.disposition(m.modify(_.instance).setTo(m.instance.modify(_.label).setTo(None)).modify(_.asOf).setTo(None)),
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
    val sha = Sha256.applyUnsafe(BundleFormat.sha256Hex(data).value)
    assertEquals(o.find(sha), None)
    assert(o.store(sha, ContentStore.Ext("pdf"), java.io.ByteArrayInputStream("not it".getBytes(UTF_8))).isLeft)
    assertEquals(o.find(sha), None)
    assertEquals(o.store(sha, ContentStore.Ext("../evil"), java.io.ByteArrayInputStream(data)), Right(()))
    assertEquals(o.find(sha).map(_._2), Some("bin")) // an unsafe extension is not used
    assertEquals(o.store(sha, ContentStore.Ext("pdf"), java.io.ByteArrayInputStream(data)), Right(())) // already there
    assertEquals(Files.list(dir).count(), 1L)
    assertEquals(ContentStore.safeExt(ContentStore.Ext("PDF")), "pdf")
