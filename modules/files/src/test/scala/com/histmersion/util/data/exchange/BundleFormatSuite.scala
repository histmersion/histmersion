package com.histmersion
package util.data.exchange

import com.histmersion.api.problem.Text.sentence
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.given
import com.histmersion.testsupport.ComparedByValue.given

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import com.histmersion.api.exchange.{Bundle, BundleMarkdown}
import com.histmersion.domain.library.Source
import com.histmersion.api.catalogue.Year
import com.histmersion.api.Sha256

/** The pure half of the `.hmx` bundle: the tar container, canonical lines and revs, the bundle id, manifest checks, the
  * bundle's kinds and the import report.
  */
class BundleFormatSuite extends munit.FunSuite:

  private val U1 = UUID.fromString("01920000-0000-7000-8000-000000000001")
  private val U2 = UUID.fromString("01920000-0000-7000-8000-000000000002")
  private val U3 = UUID.fromString("01920000-0000-7000-8000-000000000003")
  private val I = UUID.fromString("01910000-0000-7000-8000-0000000000aa")

  private def goal(
      slug: Bundle.Goal.Slug,
      updated: Bundle.Goal.UpdatedAt = Bundle.Goal.UpdatedAt("2026-10-03T09:00:00.000000Z"),
      notes: Option[Bundle.Goal.Notes] = None
  ) =
    Bundle.Goal(
      U1,
      I,
      slug,
      Bundle.Goal.Title("Title"),
      Bundle.Goal.Status("planned"),
      notes,
      List(Bundle.Goal.Tags("b"), Bundle.Goal.Tags("a")),
      Nil,
      Some(Bundle.Range(Some(Year(-1650)), None)),
      None,
      Bundle.Goal.CreatedAt("2026-10-01T00:00:00.000000Z"),
      updated
    )

  // ── tar ──────────────────────────────────────────────────────────────────────────────────────

  private def tar(entries: (BundleTar.Entry.Name, Array[Byte])*): Array[Byte] =
    val out = ByteArrayOutputStream()
    val w = BundleTar.Writer(out)
    entries.foreach((n, b) => w.bytes(n, b).fold(e => fail(e.sentence), identity))
    w.finish()
    out.toByteArray

  private def untar(b: Array[Byte]): List[(String, String)] =
    val r = BundleTar.Reader(ByteArrayInputStream(b))
    Iterator
      .continually(r.next().fold(e => fail(e.sentence), identity))
      .takeWhile(_.isDefined)
      .flatten
      .map(e => e.name.value -> String(r.readAll(), UTF_8))
      .toList

  test("tar: what is written is read back, in order, whatever the sizes (0, 1, 512, 513 bytes)"):
    val es = List("a" -> "", "b/c" -> "x", "d" -> ("y" * 512), "e" -> ("z" * 513))
    assertEquals(untar(tar(es.map((n, s) => BundleTar.Entry.Name(n) -> s.getBytes(UTF_8))*)), es)

  test("tar: the same entries give the same bytes (no timestamps, owners or modes vary)"):
    val es = List(
      BundleTar.Entry.Name("manifest.json") -> "{}".getBytes(UTF_8),
      BundleTar.Entry.Name(Bundle.blobPath(Sha256("ab" * 32))) -> Array[Byte](1, 2, 3)
    )
    assert(java.util.Arrays.equals(tar(es*), tar(es*)))

  test("tar: a header is ustar, 512 bytes, checksummed; a bad checksum or name is refused"):
    val h = BundleTar.header(BundleTar.Entry.Name("manifest.json"), ByteCount(10)).fold(e => fail(e.sentence), identity)
    assertEquals(h.length, 512)
    assertEquals(String(h.slice(257, 262), UTF_8), "ustar")
    assertEquals(
      BundleTar.parseHeader(h),
      Right(Some(BundleTar.Entry(BundleTar.Entry.Name("manifest.json"), BundleTar.Entry.Size(10))))
    )
    assert(BundleTar.parseHeader(h.updated(0, 'M'.toByte)).isLeft)
    assertEquals(BundleTar.parseHeader(new Array[Byte](512)), Right(None))
    assert(BundleTar.header(BundleTar.Entry.Name("x" * 101), ByteCount(1)).isLeft)
    assert(BundleTar.header(BundleTar.Entry.Name("x"), ByteCount.assume(-1L)).isLeft)

  test("tar: an entry that writes more or less than it declared is an error"):
    val w = BundleTar.Writer(ByteArrayOutputStream())
    assert(w.entry(BundleTar.Entry.Name("a"), ByteCount(3))(_.write(Array[Byte](1, 2))).isLeft)

  test("tar: a truncated archive is an error, not a silent short read"):
    val b = tar(BundleTar.Entry.Name("a") -> ("q" * 4000).getBytes(UTF_8))
    val r = BundleTar.Reader(ByteArrayInputStream(b.take(b.length / 2)))
    val outcome = scala.util.Try(r.next().flatMap(_ => scala.util.Try(r.readAll()).toEither.left.map(_.getMessage)))
    assert(outcome.isFailure || outcome.get.isLeft || r.next().isLeft)

  test("blob paths: content-addressed and fanned out; anything else is not a blob"):
    val sha = Sha256("0f" * 32)
    assertEquals(Bundle.blobPath(sha), s"blobs/sha256/0f/$sha")
    assertEquals(Bundle.blobSha(Bundle.blobPath(sha)), Some(sha))
    assertEquals(Bundle.blobSha(s"blobs/sha256/aa/$sha"), None)
    assertEquals(Bundle.blobSha("entities/sources.ndjson"), None)

  // ── lines and revs ───────────────────────────────────────────────────────────────────────────

  test("a line is canonical: keys sorted, no whitespace, absent fields left out, the rev added"):
    val s = BundleFormat.stamp(goal(Bundle.Goal.Slug("g")))
    assert(s.line.startsWith("""{"createdAt":"""), s.line)
    assert(!s.line.contains("notes"), s.line) // None is absent, not null
    assert(!s.line.contains(" "), s.line)
    assert(s.line.contains(s""""rev":"${s.rev}""""))
    assertEquals(s.rev.length, 64)

  test("the rev covers the content, not the timestamp: same content → same rev"):
    assertEquals(
      BundleFormat.stamp(goal(Bundle.Goal.Slug("g"))).rev,
      BundleFormat
        .stamp(goal(Bundle.Goal.Slug("g"), updated = Bundle.Goal.UpdatedAt("2030-01-01T00:00:00.000000Z")))
        .rev
    )
    assertNotEquals(
      BundleFormat.stamp(goal(Bundle.Goal.Slug("g"))).rev,
      BundleFormat.stamp(goal(Bundle.Goal.Slug("g"), notes = Some(Bundle.Goal.Notes("n")))).rev
    )
    // a rev field already set is replaced, never trusted
    assertEquals(
      BundleFormat.stamp(goal(Bundle.Goal.Slug("g")).modify(_.rev).setTo(Bundle.Goal.Rev("bogus"))).rev,
      BundleFormat.stamp(goal(Bundle.Goal.Slug("g"))).rev
    )

  test("decoding a line checks its rev; a tampered line is reported"):
    val line = BundleFormat.stamp(goal(Bundle.Goal.Slug("g"))).line
    assertEquals(BundleFormat.decodeLine[Bundle.Goal](line, stamped = true).map(_.slug), Right("g"))
    val tampered = line.replace("\"Title\"", "\"Other\"")
    assert(
      BundleFormat.decodeLine[Bundle.Goal](tampered, stamped = true).left.exists(_.sentence.contains("does not match"))
    )
    assert(BundleFormat.decodeLine[Bundle.Goal]("{not json", stamped = true).isLeft)

  test("content uuids: deterministic, version 8, RFC variant"):
    val u = BundleFormat.contentUuid(Seq("a", "b"))
    assertEquals(u, BundleFormat.contentUuid(Seq("a", "b")))
    assertNotEquals(u, BundleFormat.contentUuid(Seq("a", "c")))
    assertEquals(u.version, 8)
    assertEquals(u.variant, 2)

  test("compatibility: a newer format or schema is refused, an older one accepted, each reason given"):
    val m = Bundle.Manifest(
      Bundle.Manifest.Format(Bundle.Format),
      Bundle.Manifest.FormatVersion(Bundle.FormatVersion),
      Bundle.Manifest.SchemaVersion("33"),
      U1,
      Bundle.Instance(I),
      None,
      None,
      Nil,
      Bundle.Blobs(Bundle.Blobs.Count(0), com.histmersion.api.ByteCount(0)),
      Bundle.Reference(
        Bundle.Reference.Places(0),
        Bundle.Reference.PlacesDigest(""),
        Bundle.Reference.Periods(0),
        Bundle.Reference.PeriodsDigest("")
      )
    )
    assertEquals(BundleFormat.compatible(m, Some(Bundle.Manifest.SchemaVersion("33"))), Nil)
    assertEquals(BundleFormat.compatible(m, Some(Bundle.Manifest.SchemaVersion("40"))), Nil)
    assert(
      BundleFormat.compatible(m, Some(Bundle.Manifest.SchemaVersion("32"))).exists(_.contains("newer than this server"))
    )
    assertEquals(
      BundleFormat
        .compatible(
          m.modify(_.format)
            .setTo(Bundle.Manifest.Format("x"))
            .modify(_.formatVersion)
            .setTo(Bundle.Manifest.FormatVersion(99)),
          Some(Bundle.Manifest.SchemaVersion("33"))
        )
        .size,
      2
    )

  test("file checks list every missing, unexpected and differing file"):
    val f = Bundle.FileInfo(
      Bundle.FileInfo.Path("entities/goals.ndjson"),
      Bundle.FileInfo.Lines(1),
      com.histmersion.api.ByteCount(10),
      Sha256("a")
    )
    val m = Bundle.Manifest(
      Bundle.Manifest.Format(Bundle.Format),
      Bundle.Manifest.FormatVersion(1),
      Bundle.Manifest.SchemaVersion("33"),
      U1,
      Bundle.Instance(I),
      None,
      None,
      List(
        f,
        Bundle.FileInfo(
          Bundle.FileInfo.Path("entities/queue.ndjson"),
          Bundle.FileInfo.Lines(0),
          com.histmersion.api.ByteCount(0),
          Sha256("e")
        )
      ),
      Bundle.Blobs(Bundle.Blobs.Count(0), com.histmersion.api.ByteCount(0)),
      Bundle.Reference(
        Bundle.Reference.Places(0),
        Bundle.Reference.PlacesDigest(""),
        Bundle.Reference.Periods(0),
        Bundle.Reference.PeriodsDigest("")
      )
    )
    val ps = BundleFormat.checkFiles(
      m,
      Map(f.path -> f.modify(_.sha256).setTo(Sha256("b")), Bundle.FileInfo.Path("entities/x.ndjson") -> f)
    )
    assertEquals(ps.size, 3, ps.mkString("\n"))

  // ── entities ─────────────────────────────────────────────────────────────────────────────────

  test("kinds: wire names match the tombstone trigger's, files are distinct"):
    assertEquals(
      Bundle.Kind.values.toList.map(_.wire),
      List(
        "source",
        "finding",
        "article",
        "article-version",
        "asset",
        "asset-link",
        "attestation",
        "event",
        "goal",
        "queue-item",
        "place",
        "period"
      )
    )
    assertEquals(Bundle.Files.all.distinct.size, Bundle.Files.all.size)
    Bundle.Kind.values.foreach(k => assertEquals(Bundle.Kind.fromWire(k.wire), Some(k)))

  test("markdown: an import report says what it did, and lists every problem"):
    val r = Bundle.ImportResult(
      U1,
      Bundle.Instance(I, Some(Bundle.Instance.Label("work"))),
      dryRun = Bundle.ImportResult.DryRun(false),
      applied = Bundle.ImportResult.Applied(false),
      List(Bundle.KindCounts(Bundle.Kind.Source, inserted = Bundle.KindCounts.Inserted(2))),
      Bundle.ImportResult.Blobs(0),
      List(Bundle.ImportResult.Problems("source x: clash")),
      Nil,
      Nil
    )
    val md = BundleMarkdown.importResult(r)
    assert(md.startsWith("**Refused**"), md)
    assert(md.contains("- source x: clash"), md)
    assert(md.contains("work ("), md)
    assert(
      BundleMarkdown
        .importResult(
          r.modify(_.problems)
            .setTo(Nil)
            .modify(_.applied)
            .setTo(Bundle.ImportResult.Applied(true))
            .modify(_.counts)
            .setTo(Nil)
        )
        .contains("Nothing to change")
    )
