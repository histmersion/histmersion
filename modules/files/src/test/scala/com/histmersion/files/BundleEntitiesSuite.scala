package com.histmersion
package files
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.given
import com.histmersion.testsupport.ComparedByValue.given

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import com.histmersion.util.data.exchange.{BundleFormat, BundleReader, BundleTar}
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.library.Source
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.ByteCount
import com.histmersion.api.Sha256

/** The `.hmx` bundle's content in memory: its entities (blob and dangling references, the event-catalogue sets, a
  * decode round trip) and the reader's problem reports.
  */
class BundleEntitiesSuite extends munit.FunSuite:

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

  private def tar(entries: (String, Array[Byte])*): Array[Byte] =
    val out = ByteArrayOutputStream()
    val w = BundleTar.Writer(out)
    entries.foreach((n, b) => w.bytes(BundleTar.Entry.Name(n), b).fold(e => fail(e.sentence), identity))
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

  test("entities: blob references, dangling references and asOf"):
    val src = Bundle.Source(
      U1,
      I,
      Citekey("ck2001_a"),
      Title("T"),
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
      Bundle.Source.AddedDate("2026-01-01"),
      Some(Bundle.Source.BodySha256("b" * 64)),
      Some(Bundle.Source.OriginalSha256("c" * 64)),
      Some(Bundle.Source.OriginalExt("pdf")),
      Bundle.Source.UpdatedAt("2026-10-02T00:00:00.000000Z")
    )
    val es = BundleEntities(
      sources = List(src),
      findings = List(
        Bundle.Finding(
          U2,
          I,
          U1,
          Bundle.Finding.Id("ck2001_a#0"),
          Bundle.Finding.Claim("c"),
          None,
          None,
          None,
          None,
          None,
          Nil,
          None,
          None,
          Bundle.Finding.UpdatedAt("2026-10-03T00:00:00.000000Z")
        )
      ),
      edges = List(Bundle.Edge(U1, U3, Bundle.Edge.Kind("references")))
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
      Bundle.Event.Id("e1"),
      Bundle.Event.Label("Eruption"),
      Bundle.Event.Kind("eruption"),
      Some(Bundle.Event.NotBefore(-1600)),
      None,
      None,
      Some(Bundle.Event.NotAfter(-1500)),
      Bundle.Event.Historicity("attested"),
      None,
      None,
      None,
      None,
      None,
      None,
      Nil,
      Nil,
      Nil,
      Bundle.Event.UpdatedAt("2026-10-03T00:00:00.000000Z")
    )
  )

  test("an event's catalogue fields are absent when empty: the rev of an event compiled from sources is unchanged"):
    // none of the new keys is written: the line (and so the rev) is the one written before they existed
    val plain = BundleFormat.stamp(event())
    List("catalogue", "names", "wikidata", "central", "plusMinus", "datingMethod").foreach(f =>
      assert(!plain.line.contains(s"\"$f\""), plain.line)
    )
    assertEquals(
      BundleFormat.decodeLine[Bundle.Event](plain.line, stamped = true, Problem.Where("e:1")).map(_.names),
      Right(None)
    )
    val gvp = BundleFormat.stamp(
      event(
        _.modify(_.catalogue)
          .setTo(Some(Bundle.Event.Catalogue("gvp")))
          .modify(_.names)
          .setTo(Some(List(Bundle.Event.Names("Thera"), Bundle.Event.Names("Santorini"))))
          .modify(_.wikidata)
          .setTo(Some(Bundle.Event.Wikidata("Q1")))
          .modify(_.central)
          .setTo(Some(Bundle.Event.Central(-1560L)))
          .modify(_.plusMinus)
          .setTo(Some(Bundle.Event.PlusMinus(10L)))
          .modify(_.datingMethod)
          .setTo(Some(Bundle.Event.DatingMethod("radiocarbon")))
      )
    )
    assertNotEquals(gvp.rev, plain.rev)
    assertEquals(
      BundleFormat
        .decodeLine[Bundle.Event](gvp.line, stamped = true, Problem.Where("e:1"))
        .map(e => (e.catalogue, e.names, e.plusMinus)),
      Right((Some("gvp"), Some(List("Thera", "Santorini")), Some(10L)))
    )

  test("the event-catalogue sets travel in files of their own, round-trip, and are empty in an older bundle"):
    val at = "2026-10-05T00:00:00.000000Z"
    val es = BundleEntities(
      events = List(event()),
      eventCatalogue = List(
        Bundle.EventCatalogue(
          U1,
          Bundle.EventCatalogue.Provider("gvp"),
          Bundle.EventCatalogue.ProviderId("212040"),
          Some(Bundle.EventCatalogue.Url("https://volcano.si.edu/x")),
          Bundle.EventCatalogue.Label("Santorini"),
          Bundle.EventCatalogue.Kind("eruption"),
          Some(Bundle.EventCatalogue.NotBefore(-1610)),
          None,
          None,
          Some(Bundle.EventCatalogue.NotAfter(-1600)),
          Some(Latitude(36.4)),
          Some(Longitude(25.4)),
          Some(Bundle.EventCatalogue.Vei(7)),
          None,
          None,
          Bundle.EventCatalogue.Historicity("attested"),
          Json.obj("VEI" -> Json.fromInt(7)),
          Bundle.EventCatalogue.RetrievedAt(at)
        )
      ),
      eventCandidates = List(
        Bundle.EventCandidate(
          U1,
          U2,
          Citekey("ck2001_a"),
          Bundle.EventCandidate.Score(0.75),
          Bundle.EventCandidate.Status("confirmed"),
          List(Bundle.EventCandidate.Findings("ck2001_a#0")),
          Json.arr(),
          List(Bundle.EventCandidate.Reasons("near")),
          Bundle.EventCandidate.ComputedAt(at)
        )
      ),
      eventChecks = List(Bundle.EventCheck(U1, Bundle.EventCheck.CheckedAt(at))),
      corrections = List(
        Bundle.Correction(
          Bundle.Correction.TargetKind("event"),
          Bundle.Correction.TargetId("e1"),
          Some(U1),
          Bundle.Correction.Field("label"),
          Bundle.Correction.Value("\"Thera\""),
          Some(Bundle.Correction.Upstream("gvp")),
          Bundle.Correction.UpstreamValue("\"Santorini\""),
          None,
          Bundle.Correction.Status("active"),
          Bundle.Correction.CreatedAt(at),
          Bundle.Correction.UpdatedAt("2026-10-06T00:00:00.000000Z"),
          None
        )
      ),
      siteTypes = List(
        Bundle.SiteType(
          Bundle.SiteType.PlaceId("pleiades:599928"),
          Some(Bundle.SiteType.Value("settlement")),
          Some(Bundle.SiteType.CheckedAt(at))
        )
      )
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
    assertEquals(
      candidateStatus(Bundle.EventCandidate.Status("proposed"), Bundle.EventCandidate.Status("confirmed")),
      "confirmed"
    )
    assertEquals(
      candidateStatus(Bundle.EventCandidate.Status("proposed"), Bundle.EventCandidate.Status("rejected")),
      "rejected"
    )
    assertEquals(
      candidateStatus(Bundle.EventCandidate.Status("proposed"), Bundle.EventCandidate.Status("proposed")),
      "proposed"
    )
    assertEquals(
      candidateStatus(Bundle.EventCandidate.Status("confirmed"), Bundle.EventCandidate.Status("proposed")),
      "confirmed"
    )
    assertEquals(
      candidateStatus(Bundle.EventCandidate.Status("rejected"), Bundle.EventCandidate.Status("confirmed")),
      "rejected"
    )
    assert(newer(None, Some(Bundle.UpdatedAt("2026-10-05T00:00:00.000000Z"))))
    assert(
      newer(
        Some(Bundle.UpdatedAt("2026-10-05T00:00:00.000000Z")),
        Some(Bundle.UpdatedAt("2026-10-05T00:00:00.000001Z"))
      )
    )
    assert(
      !newer(
        Some(Bundle.UpdatedAt("2026-10-05T00:00:00.000000Z")),
        Some(Bundle.UpdatedAt("2026-10-05T00:00:00.000000Z"))
      )
    )
    assert(
      !newer(
        Some(Bundle.UpdatedAt("2026-10-06T00:00:00.000000Z")),
        Some(Bundle.UpdatedAt("2026-10-05T00:00:00.000000Z"))
      )
    )
    assert(!newer(Some(Bundle.UpdatedAt("2026-10-05T00:00:00.000000Z")), None))
    assert(!newer(None, None))

  test("the reader reports every problem of a bundle, not just the first"):
    val es = BundleEntities(goals = List(goal(Bundle.Goal.Slug("g"))))
    val ps = es.payloads
    val m = Bundle.Manifest(
      Bundle.Manifest.Format(Bundle.Format),
      Bundle.Manifest.FormatVersion(1),
      Bundle.Manifest.SchemaVersion("33"),
      U1,
      Bundle.Instance(I),
      None,
      None,
      ps.map((p, b) => BundleFormat.fileInfo(Bundle.FileInfo.Path(p), b)),
      Bundle.Blobs(Bundle.Blobs.Count(1), ByteCount(3)),
      Bundle.Reference(
        Bundle.Reference.Places(0),
        Bundle.Reference.PlacesDigest(""),
        Bundle.Reference.Periods(0),
        Bundle.Reference.PeriodsDigest("")
      )
    )
    val ok = tar(
      (Bundle.ManifestPath -> BundleFormat.manifestBytes(m)) :: ps.map(identity) ++
        List(Bundle.blobPath(Sha256("ab" * 32)) -> Array[Byte](1, 2, 3))*
    )
    val l = BundleReader.read(ByteArrayInputStream(ok), hashBlobs = true)
    // the blob's content is not its name, and nothing references it
    assertEquals(
      l.problems.map(_.sentence),
      List(
        "the bundle data at blobs/sha256/ab/abababababababababababababababababababababababababababababababab fails its checksum",
        "blobs/sha256/ab/abababababababababababababababababababababababababababababababab is in the bundle, but nothing refers to it"
      )
    )
    assertEquals(l.entities.goals.map(_.slug), List("g"))
    val noManifest = BundleReader.read(ByteArrayInputStream(tar(ps*)), hashBlobs = false)
    assert(noManifest.manifest.isEmpty)
    assertEquals(
      noManifest.problems.headOption.map(_.sentence),
      Some(s"${ps.head._1} comes first in the bundle, where manifest.json belongs")
    )

  // ── decisions ────────────────────────────────────────────────────────────────────────────────
