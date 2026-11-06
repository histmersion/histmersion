package com.histmersion.files

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.given

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import com.histmersion.util.data.exchange.{BundleFormat, BundleReader, BundleTar}
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.library.Source

/** The `.hmx` bundle's content in memory: its entities (blob and dangling references, the event-catalogue sets, a
  * decode round trip) and the reader's problem reports.
  */
class BundleEntitiesSuite extends munit.FunSuite:

  private val U1 = UUID.fromString("01920000-0000-7000-8000-000000000001")
  private val U2 = UUID.fromString("01920000-0000-7000-8000-000000000002")
  private val U3 = UUID.fromString("01920000-0000-7000-8000-000000000003")
  private val I = UUID.fromString("01910000-0000-7000-8000-0000000000aa")

  private def goal(slug: String, updated: String = "2026-10-03T09:00:00.000000Z", notes: Option[String] = None) =
    Bundle.Goal(
      U1,
      I,
      slug,
      com.histmersion.api.exchange.Bundle.Goal.Title("Title"),
      com.histmersion.api.exchange.Bundle.Goal.Status("planned"),
      notes,
      List(com.histmersion.api.exchange.Bundle.Goal.Tags("b"), com.histmersion.api.exchange.Bundle.Goal.Tags("a")),
      Nil,
      Some(Bundle.Range(Some(com.histmersion.api.catalogue.Year(-1650)), None)),
      None,
      com.histmersion.api.exchange.Bundle.Goal.CreatedAt("2026-10-01T00:00:00.000000Z"),
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
    val src = Bundle.Source(
      U1,
      I,
      com.histmersion.api.library.Citekey("ck2001_a"),
      com.histmersion.api.library.Title("T"),
      Nil,
      None,
      None,
      None,
      Source.Status.Current,
      Nil,
      Nil,
      Nil,
      None,
      None,
      None,
      None,
      None,
      None,
      com.histmersion.api.exchange.Bundle.Source.AddedDate("2026-01-01"),
      Some("b" * 64),
      Some("c" * 64),
      Some(com.histmersion.api.exchange.Bundle.Source.OriginalExt("pdf")),
      com.histmersion.api.exchange.Bundle.Source.UpdatedAt("2026-10-02T00:00:00.000000Z")
    )
    val es = BundleEntities(
      sources = List(src),
      findings = List(
        Bundle.Finding(
          U2,
          I,
          U1,
          com.histmersion.api.exchange.Bundle.Finding.Id("ck2001_a#0"),
          com.histmersion.api.exchange.Bundle.Finding.Claim("c"),
          None,
          None,
          None,
          None,
          None,
          Nil,
          None,
          None,
          com.histmersion.api.exchange.Bundle.Finding.UpdatedAt("2026-10-03T00:00:00.000000Z")
        )
      ),
      edges = List(Bundle.Edge(U1, U3, com.histmersion.api.exchange.Bundle.Edge.Kind("references")))
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

  private def event(extra: Bundle.Event => Bundle.Event = identity) = extra(
    Bundle.Event(
      U1,
      I,
      com.histmersion.api.exchange.Bundle.Event.Id("e1"),
      com.histmersion.api.exchange.Bundle.Event.Label("Eruption"),
      com.histmersion.api.exchange.Bundle.Event.Kind("eruption"),
      Some(com.histmersion.api.exchange.Bundle.Event.NotBefore(-1600)),
      None,
      None,
      Some(com.histmersion.api.exchange.Bundle.Event.NotAfter(-1500)),
      com.histmersion.api.exchange.Bundle.Event.Historicity("attested"),
      None,
      None,
      None,
      None,
      None,
      None,
      Nil,
      Nil,
      Nil,
      com.histmersion.api.exchange.Bundle.Event.UpdatedAt("2026-10-03T00:00:00.000000Z")
    )
  )

  test("an event's catalogue fields are absent when empty: the rev of an event compiled from sources is unchanged"):
    // none of the new keys is written: the line (and so the rev) is the one written before they existed
    val plain = BundleFormat.stamp(event())
    List("catalogue", "names", "wikidata", "central", "plusMinus", "datingMethod").foreach(f =>
      assert(!plain.line.contains(s"\"$f\""), plain.line)
    )
    assertEquals(BundleFormat.decodeLine[Bundle.Event](plain.line, stamped = true).map(_.names), Right(None))
    val gvp = BundleFormat.stamp(
      event(
        _.modify(
          _.catalogue).setTo(Some("gvp")).modify(_.names).setTo(Some(List("Thera", "Santorini"))).modify(_.wikidata).setTo(Some("Q1")).modify(_.central).setTo((Some(-1560): _root_.scala.Option[_root_.scala.Long])).modify(_.plusMinus).setTo((Some(10): _root_.scala.Option[_root_.scala.Long])).modify(_.datingMethod).setTo(Some("radiocarbon")
        )
      )
    )
    assertNotEquals(gvp.rev, plain.rev)
    assertEquals(
      BundleFormat.decodeLine[Bundle.Event](gvp.line, stamped = true).map(e => (e.catalogue, e.names, e.plusMinus)),
      Right((Some("gvp"), Some(List("Thera", "Santorini")), Some(10L)))
    )

  test("the event-catalogue sets travel in files of their own, round-trip, and are empty in an older bundle"):
    val at = "2026-10-05T00:00:00.000000Z"
    val es = BundleEntities(
      events = List(event()),
      eventCatalogue = List(
        Bundle.EventCatalogue(
          U1,
          com.histmersion.api.exchange.Bundle.EventCatalogue.Provider("gvp"),
          com.histmersion.api.exchange.Bundle.EventCatalogue.ProviderId("212040"),
          Some(com.histmersion.api.exchange.Bundle.EventCatalogue.Url("https://volcano.si.edu/x")),
          com.histmersion.api.exchange.Bundle.EventCatalogue.Label("Santorini"),
          com.histmersion.api.exchange.Bundle.EventCatalogue.Kind("eruption"),
          Some(com.histmersion.api.exchange.Bundle.EventCatalogue.NotBefore(-1610)),
          None,
          None,
          Some(com.histmersion.api.exchange.Bundle.EventCatalogue.NotAfter(-1600)),
          Some(36.4),
          Some(25.4),
          Some(com.histmersion.api.exchange.Bundle.EventCatalogue.Vei(7)),
          None,
          None,
          com.histmersion.api.exchange.Bundle.EventCatalogue.Historicity("attested"),
          io.circe.Json.obj("VEI" -> io.circe.Json.fromInt(7)),
          at
        )
      ),
      eventCandidates = List(
        Bundle.EventCandidate(
          U1,
          U2,
          com.histmersion.api.library.Citekey("ck2001_a"),
          0.75,
          com.histmersion.api.exchange.Bundle.EventCandidate.Status("confirmed"),
          List(com.histmersion.api.exchange.Bundle.EventCandidate.Findings("ck2001_a#0")),
          io.circe.Json.arr(),
          List(com.histmersion.api.exchange.Bundle.EventCandidate.Reasons("near")),
          at
        )
      ),
      eventChecks = List(Bundle.EventCheck(U1, at)),
      corrections = List(
        Bundle.Correction(
          com.histmersion.api.exchange.Bundle.Correction.TargetKind("event"),
          com.histmersion.api.exchange.Bundle.Correction.TargetId("e1"),
          Some(U1),
          com.histmersion.api.exchange.Bundle.Correction.Field("label"),
          com.histmersion.api.exchange.Bundle.Correction.Value("\"Thera\""),
          Some(com.histmersion.api.exchange.Bundle.Correction.Upstream("gvp")),
          com.histmersion.api.exchange.Bundle.Correction.UpstreamValue("\"Santorini\""),
          None,
          com.histmersion.api.exchange.Bundle.Correction.Status("active"),
          at,
          com.histmersion.api.exchange.Bundle.Correction.UpdatedAt("2026-10-06T00:00:00.000000Z"),
          None
        )
      ),
      siteTypes = List(Bundle.SiteType(com.histmersion.api.exchange.Bundle.SiteType.PlaceId("pleiades:599928"), Some(com.histmersion.api.exchange.Bundle.SiteType.Value("settlement")), Some(at)))
    )
    val payloads = es.payloads.toMap
    List(
      Bundle.Files.EventCatalogue,
      Bundle.Files.EventCandidates,
      Bundle.Files.EventChecks,
      Bundle.Files.EventCorrections,
      Bundle.Files.PlaceSiteTypes
    ).foreach(f => assertEquals(BundleFormat.lines(payloads(f)), 1, f))
    val (back, problems) = BundleEntities.decode(payloads)
    assertEquals(problems, Nil)
    assertEquals(back.modify(_.events).setTo(Nil), es.modify(_.events).setTo(Nil))
    // the newest of them is the bundle's asOf (the next --since)
    assertEquals(es.asOf, Some("2026-10-06T00:00:00.000000Z"))
    // a bundle written before these files existed: they read as empty
    val (old, oldProblems) = BundleEntities.decode(
      payloads -- List(
        Bundle.Files.EventCatalogue,
        Bundle.Files.EventCandidates,
        Bundle.Files.EventChecks,
        Bundle.Files.EventCorrections,
        Bundle.Files.PlaceSiteTypes
      )
    )
    assertEquals(oldProblems, Nil)
    assertEquals(
      (old.eventCatalogue, old.eventCandidates, old.eventChecks, old.corrections, old.siteTypes),
      (Nil, Nil, Nil, Nil, Nil)
    )
    assertEquals(old.events.map(_.id), List("e1"))

  test("merging the sets: a decision is kept over a proposal, never a proposal over a decision; the newer row wins"):
    import com.histmersion.api.exchange.Bundle.EventSets.*
    assertEquals(candidateStatus(com.histmersion.api.exchange.Bundle.EventCandidate.Status("proposed"), com.histmersion.api.exchange.Bundle.EventCandidate.Status("confirmed")), "confirmed")
    assertEquals(candidateStatus(com.histmersion.api.exchange.Bundle.EventCandidate.Status("proposed"), com.histmersion.api.exchange.Bundle.EventCandidate.Status("rejected")), "rejected")
    assertEquals(candidateStatus(com.histmersion.api.exchange.Bundle.EventCandidate.Status("proposed"), com.histmersion.api.exchange.Bundle.EventCandidate.Status("proposed")), "proposed")
    assertEquals(candidateStatus(com.histmersion.api.exchange.Bundle.EventCandidate.Status("confirmed"), com.histmersion.api.exchange.Bundle.EventCandidate.Status("proposed")), "confirmed")
    assertEquals(candidateStatus(com.histmersion.api.exchange.Bundle.EventCandidate.Status("rejected"), com.histmersion.api.exchange.Bundle.EventCandidate.Status("confirmed")), "rejected")
    assert(newer(None, Some(com.histmersion.api.exchange.Bundle.UpdatedAt("2026-10-05T00:00:00.000000Z"))))
    assert(newer(Some(com.histmersion.api.exchange.Bundle.UpdatedAt("2026-10-05T00:00:00.000000Z")), Some(com.histmersion.api.exchange.Bundle.UpdatedAt("2026-10-05T00:00:00.000001Z"))))
    assert(!newer(Some(com.histmersion.api.exchange.Bundle.UpdatedAt("2026-10-05T00:00:00.000000Z")), Some(com.histmersion.api.exchange.Bundle.UpdatedAt("2026-10-05T00:00:00.000000Z"))))
    assert(!newer(Some(com.histmersion.api.exchange.Bundle.UpdatedAt("2026-10-06T00:00:00.000000Z")), Some(com.histmersion.api.exchange.Bundle.UpdatedAt("2026-10-05T00:00:00.000000Z"))))
    assert(!newer(Some(com.histmersion.api.exchange.Bundle.UpdatedAt("2026-10-05T00:00:00.000000Z")), None))
    assert(!newer(None, None))

  test("the reader reports every problem of a bundle, not just the first"):
    val es = BundleEntities(goals = List(goal("g")))
    val ps = es.payloads
    val m = Bundle.Manifest(
      Bundle.Format,
      com.histmersion.api.exchange.Bundle.Manifest.FormatVersion(1),
      com.histmersion.api.exchange.Bundle.Manifest.SchemaVersion("33"),
      U1,
      Bundle.Instance(I),
      None,
      None,
      ps.map((p, b) => BundleFormat.fileInfo(p, b)),
      Bundle.Blobs(com.histmersion.api.exchange.Bundle.Blobs.Count(1), com.histmersion.api.ByteCount(3)),
      Bundle.Reference(com.histmersion.api.exchange.Bundle.Reference.Places(0), com.histmersion.api.exchange.Bundle.Reference.PlacesDigest(""), com.histmersion.api.exchange.Bundle.Reference.Periods(0), com.histmersion.api.exchange.Bundle.Reference.PeriodsDigest(""))
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
