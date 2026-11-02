package com.histmersion.files

import com.histmersion.api.*
import com.histmersion.api.given

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import com.histmersion.values.{BundleKind, SourceStatus}
import com.histmersion.util.data.exchange.{BundleFormat, BundleReader, BundleTar}

/** The `.hmx` bundle's content in memory: its entities (blob and dangling references, the event-catalogue sets, a
  * decode round trip) and the reader's problem reports.
  */
class BundleEntitiesSuite extends munit.FunSuite:

  private val U1 = UUID.fromString("01920000-0000-7000-8000-000000000001")
  private val U2 = UUID.fromString("01920000-0000-7000-8000-000000000002")
  private val U3 = UUID.fromString("01920000-0000-7000-8000-000000000003")
  private val I = UUID.fromString("01910000-0000-7000-8000-0000000000aa")

  private def goal(slug: String, updated: String = "2026-10-03T09:00:00.000000Z", notes: Option[String] = None) =
    BundleGoal(
      U1,
      I,
      slug,
      "Title",
      "planned",
      notes,
      List("b", "a"),
      Nil,
      Some(BundleRange(Some(-1650), None)),
      None,
      "2026-10-01T00:00:00.000000Z",
      updated
    )

  // ── tar ──────────────────────────────────────────────────────────────────────────────────────

  private def tar(entries: (String, Array[Byte])*): Array[Byte] =
    val out = ByteArrayOutputStream()
    val w = BundleTar.Writer(out)
    entries.foreach((n, b) => w.bytes(n, b).fold(e => fail(e), identity))
    w.finish()
    out.toByteArray

  private def untar(b: Array[Byte]): List[(String, String)] =
    val r = BundleTar.Reader(ByteArrayInputStream(b))
    Iterator
      .continually(r.next().fold(e => fail(e), identity))
      .takeWhile(_.isDefined)
      .flatten
      .map(e => e.name -> String(r.readAll(), UTF_8))
      .toList

  test("entities: blob references, dangling references and asOf"):
    val src = BundleSource(
      U1,
      I,
      "ck2001_a",
      "T",
      Nil,
      None,
      None,
      None,
      SourceStatus.Current,
      Nil,
      Nil,
      Nil,
      None,
      None,
      None,
      None,
      None,
      None,
      "2026-01-01",
      Some("b" * 64),
      Some("c" * 64),
      Some("pdf"),
      "2026-10-02T00:00:00.000000Z"
    )
    val es = BundleEntities(
      sources = List(src),
      findings = List(
        BundleFinding(
          U2,
          I,
          U1,
          "ck2001_a#0",
          "c",
          None,
          None,
          None,
          None,
          None,
          Nil,
          None,
          None,
          "2026-10-03T00:00:00.000000Z"
        )
      ),
      edges = List(BundleEdge(U1, U3, "references"))
    )
    assertEquals(es.blobRefs.keySet, Set("b" * 64, "c" * 64))
    assertEquals(es.asOf, Some("2026-10-03T00:00:00.000000Z"))
    // the edge points at a source the bundle does not carry; an edge target is optional
    assertEquals(es.dangling.map(_.uid), List(U3))
    assertEquals(es.dangling.map(_.required), List(false))
    val (back, problems) = BundleEntities.decode(es.payloads.toMap)
    assertEquals(problems, Nil)
    // decoded lines carry their revs; re-encoded they are the same bytes
    assertEquals(back.payloads.map((p, b) => p -> String(b, UTF_8)), es.payloads.map((p, b) => p -> String(b, UTF_8)))
    assertEquals(back.findings.map(_.rev), List(BundleFormat.stamp(es.findings.head).rev))

  // ── the event-catalogue sets (#1647) ─────────────────────────────────────────────────────────

  private def event(extra: BundleEvent => BundleEvent = identity) = extra(
    BundleEvent(
      U1,
      I,
      "e1",
      "Eruption",
      "eruption",
      Some(-1600),
      None,
      None,
      Some(-1500),
      "attested",
      None,
      None,
      None,
      None,
      None,
      None,
      Nil,
      Nil,
      Nil,
      "2026-10-03T00:00:00.000000Z"
    )
  )

  test("an event's catalogue fields are absent when empty: the rev of an event compiled from sources is unchanged"):
    // none of the new keys is written: the line (and so the rev) is the one written before they existed
    val plain = BundleFormat.stamp(event())
    List("catalogue", "names", "wikidata", "central", "plusMinus", "datingMethod").foreach(f =>
      assert(!plain.line.contains(s"\"$f\""), plain.line)
    )
    assertEquals(BundleFormat.decodeLine[BundleEvent](plain.line, stamped = true).map(_.names), Right(None))
    val gvp = BundleFormat.stamp(
      event(
        _.copy(
          catalogue = Some("gvp"),
          names = Some(List("Thera", "Santorini")),
          wikidata = Some("Q1"),
          central = Some(-1560),
          plusMinus = Some(10),
          datingMethod = Some("radiocarbon")
        )
      )
    )
    assertNotEquals(gvp.rev, plain.rev)
    assertEquals(
      BundleFormat.decodeLine[BundleEvent](gvp.line, stamped = true).map(e => (e.catalogue, e.names, e.plusMinus)),
      Right((Some("gvp"), Some(List("Thera", "Santorini")), Some(10L)))
    )

  test("the event-catalogue sets travel in files of their own, round-trip, and are empty in an older bundle"):
    val at = "2026-10-05T00:00:00.000000Z"
    val es = BundleEntities(
      events = List(event()),
      eventCatalogue = List(
        BundleEventCatalogue(
          U1,
          "gvp",
          "212040",
          Some("https://volcano.si.edu/x"),
          "Santorini",
          "eruption",
          Some(-1610),
          None,
          None,
          Some(-1600),
          Some(36.4),
          Some(25.4),
          Some(7),
          None,
          None,
          "attested",
          io.circe.Json.obj("VEI" -> io.circe.Json.fromInt(7)),
          at
        )
      ),
      eventCandidates = List(
        BundleEventCandidate(
          U1,
          U2,
          "ck2001_a",
          0.75,
          "confirmed",
          List("ck2001_a#0"),
          io.circe.Json.arr(),
          List("near"),
          at
        )
      ),
      eventChecks = List(BundleEventCheck(U1, at)),
      corrections = List(
        BundleCorrection(
          "event",
          "e1",
          Some(U1),
          "label",
          "\"Thera\"",
          Some("gvp"),
          "\"Santorini\"",
          None,
          "active",
          at,
          "2026-10-06T00:00:00.000000Z",
          None
        )
      ),
      siteTypes = List(BundleSiteType("pleiades:599928", Some("settlement"), Some(at)))
    )
    val payloads = es.payloads.toMap
    List(
      BundleFiles.EventCatalogue,
      BundleFiles.EventCandidates,
      BundleFiles.EventChecks,
      BundleFiles.EventCorrections,
      BundleFiles.PlaceSiteTypes
    ).foreach(f => assertEquals(BundleFormat.lines(payloads(f)), 1, f))
    val (back, problems) = BundleEntities.decode(payloads)
    assertEquals(problems, Nil)
    assertEquals(back.copy(events = Nil), es.copy(events = Nil))
    // the newest of them is the bundle's asOf (the next --since)
    assertEquals(es.asOf, Some("2026-10-06T00:00:00.000000Z"))
    // a bundle written before these files existed: they read as empty
    val (old, oldProblems) = BundleEntities.decode(
      payloads -- List(
        BundleFiles.EventCatalogue,
        BundleFiles.EventCandidates,
        BundleFiles.EventChecks,
        BundleFiles.EventCorrections,
        BundleFiles.PlaceSiteTypes
      )
    )
    assertEquals(oldProblems, Nil)
    assertEquals(
      (old.eventCatalogue, old.eventCandidates, old.eventChecks, old.corrections, old.siteTypes),
      (Nil, Nil, Nil, Nil, Nil)
    )
    assertEquals(old.events.map(_.id), List("e1"))

  test("merging the sets: a decision is kept over a proposal, never a proposal over a decision; the newer row wins"):
    import BundleEventSets.*
    assertEquals(candidateStatus("proposed", "confirmed"), "confirmed")
    assertEquals(candidateStatus("proposed", "rejected"), "rejected")
    assertEquals(candidateStatus("proposed", "proposed"), "proposed")
    assertEquals(candidateStatus("confirmed", "proposed"), "confirmed")
    assertEquals(candidateStatus("rejected", "confirmed"), "rejected")
    assert(newer(None, Some("2026-10-05T00:00:00.000000Z")))
    assert(newer(Some("2026-10-05T00:00:00.000000Z"), Some("2026-10-05T00:00:00.000001Z")))
    assert(!newer(Some("2026-10-05T00:00:00.000000Z"), Some("2026-10-05T00:00:00.000000Z")))
    assert(!newer(Some("2026-10-06T00:00:00.000000Z"), Some("2026-10-05T00:00:00.000000Z")))
    assert(!newer(Some("2026-10-05T00:00:00.000000Z"), None))
    assert(!newer(None, None))

  test("the reader reports every problem of a bundle, not just the first"):
    val es = BundleEntities(goals = List(goal("g")))
    val ps = es.payloads
    val m = BundleManifest(
      Bundle.Format,
      1,
      "33",
      U1,
      BundleInstance(I),
      None,
      None,
      ps.map((p, b) => BundleFormat.fileInfo(p, b)),
      BundleBlobs(1, 3),
      BundleReference(0, "", 0, "")
    )
    val ok = tar(
      (Bundle.ManifestPath -> BundleFormat.manifestBytes(m)) :: ps.map(identity) ++
        List(Bundle.blobPath("ab" * 32) -> Array[Byte](1, 2, 3))*
    )
    val l = BundleReader.read(ByteArrayInputStream(ok), hashBlobs = true)
    // the blob's content is not its name, and nothing references it
    assert(l.problems.exists(_.contains("hashes to")), l.problems.mkString("\n"))
    assert(l.problems.exists(_.contains("nothing references it")), l.problems.mkString("\n"))
    assertEquals(l.entities.goals.map(_.slug), List("g"))
    val noManifest = BundleReader.read(ByteArrayInputStream(tar(ps*)), hashBlobs = false)
    assert(noManifest.manifest.isEmpty)
    assert(noManifest.problems.exists(_.contains("first entry")))

  // ── decisions ────────────────────────────────────────────────────────────────────────────────
