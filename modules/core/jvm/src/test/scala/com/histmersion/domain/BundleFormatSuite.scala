package com.histmersion.domain

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID

/** The pure half of the `.hmx` bundle: the tar container, canonical lines and revs, the bundle id,
  * manifest checks, the import decisions and the reader's problem reports. */
class BundleFormatSuite extends munit.FunSuite:

  private val U1 = UUID.fromString("01920000-0000-7000-8000-000000000001")
  private val U2 = UUID.fromString("01920000-0000-7000-8000-000000000002")
  private val U3 = UUID.fromString("01920000-0000-7000-8000-000000000003")
  private val I  = UUID.fromString("01910000-0000-7000-8000-0000000000aa")

  private def goal(slug: String, updated: String = "2026-10-03T09:00:00.000000Z", notes: Option[String] = None) =
    BundleGoal(U1, I, slug, "Title", "planned", notes, List("b", "a"), Nil, Some(BundleRange(Some(-1650), None)),
      None, "2026-10-01T00:00:00.000000Z", updated)

  // ── tar ──────────────────────────────────────────────────────────────────────────────────────

  private def tar(entries: (String, Array[Byte])*): Array[Byte] =
    val out = ByteArrayOutputStream()
    val w   = BundleTar.Writer(out)
    entries.foreach((n, b) => w.bytes(n, b).fold(e => fail(e), identity))
    w.finish()
    out.toByteArray

  private def untar(b: Array[Byte]): List[(String, String)] =
    val r = BundleTar.Reader(ByteArrayInputStream(b))
    Iterator.continually(r.next().fold(e => fail(e), identity)).takeWhile(_.isDefined).flatten
      .map(e => e.name -> String(r.readAll(), UTF_8)).toList

  test("tar: what is written is read back, in order, whatever the sizes (0, 1, 512, 513 bytes)"):
    val es = List("a" -> "", "b/c" -> "x", "d" -> ("y" * 512), "e" -> ("z" * 513))
    assertEquals(untar(tar(es.map((n, s) => n -> s.getBytes(UTF_8))*)), es)

  test("tar: the same entries give the same bytes (no timestamps, owners or modes vary)"):
    val es = List("manifest.json" -> "{}".getBytes(UTF_8), Bundle.blobPath("ab" * 32) -> Array[Byte](1, 2, 3))
    assert(java.util.Arrays.equals(tar(es*), tar(es*)))

  test("tar: a header is ustar, 512 bytes, checksummed; a bad checksum or name is refused"):
    val h = BundleTar.header("manifest.json", 10).fold(e => fail(e), identity)
    assertEquals(h.length, 512)
    assertEquals(String(h.slice(257, 262), UTF_8), "ustar")
    assertEquals(BundleTar.parseHeader(h), Right(Some(BundleTar.Entry("manifest.json", 10))))
    assert(BundleTar.parseHeader(h.updated(0, 'M'.toByte)).isLeft)
    assertEquals(BundleTar.parseHeader(new Array[Byte](512)), Right(None))
    assert(BundleTar.header("x" * 101, 1).isLeft)
    assert(BundleTar.header("x", -1).isLeft)

  test("tar: an entry that writes more or less than it declared is an error"):
    val w = BundleTar.Writer(ByteArrayOutputStream())
    assert(w.entry("a", 3)(_.write(Array[Byte](1, 2))).isLeft)

  test("tar: a truncated archive is an error, not a silent short read"):
    val b = tar("a" -> ("q" * 4000).getBytes(UTF_8))
    val r = BundleTar.Reader(ByteArrayInputStream(b.take(b.length / 2)))
    val outcome = scala.util.Try(r.next().flatMap(_ => scala.util.Try(r.readAll()).toEither.left.map(_.getMessage)))
    assert(outcome.isFailure || outcome.get.isLeft || r.next().isLeft)

  test("blob paths: content-addressed and fanned out; anything else is not a blob"):
    val sha = "0f" * 32
    assertEquals(Bundle.blobPath(sha), s"blobs/sha256/0f/$sha")
    assertEquals(Bundle.blobSha(Bundle.blobPath(sha)), Some(sha))
    assertEquals(Bundle.blobSha(s"blobs/sha256/aa/$sha"), None)
    assertEquals(Bundle.blobSha("entities/sources.ndjson"), None)

  // ── lines and revs ───────────────────────────────────────────────────────────────────────────

  test("a line is canonical: keys sorted, no whitespace, absent fields left out, the rev added"):
    val s = BundleFormat.stamp(goal("g"))
    assert(s.line.startsWith("""{"createdAt":"""), s.line)
    assert(!s.line.contains("notes"), s.line)      // None is absent, not null
    assert(!s.line.contains(" "), s.line)
    assert(s.line.contains(s""""rev":"${s.rev}""""))
    assertEquals(s.rev.length, 64)

  test("the rev covers the content, not the timestamp: same content → same rev"):
    assertEquals(BundleFormat.stamp(goal("g")).rev, BundleFormat.stamp(goal("g", updated = "2030-01-01T00:00:00.000000Z")).rev)
    assertNotEquals(BundleFormat.stamp(goal("g")).rev, BundleFormat.stamp(goal("g", notes = Some("n"))).rev)
    // a rev field already set is replaced, never trusted
    assertEquals(BundleFormat.stamp(goal("g").copy(rev = "bogus")).rev, BundleFormat.stamp(goal("g")).rev)

  test("decoding a line checks its rev; a tampered line is reported"):
    val line = BundleFormat.stamp(goal("g")).line
    assertEquals(BundleFormat.decodeLine[BundleGoal](line, stamped = true).map(_.slug), Right("g"))
    val tampered = line.replace("\"Title\"", "\"Other\"")
    assert(BundleFormat.decodeLine[BundleGoal](tampered, stamped = true).left.exists(_.contains("does not match")))
    assert(BundleFormat.decodeLine[BundleGoal]("{not json", stamped = true).isLeft)

  test("content uuids: deterministic, version 8, RFC variant"):
    val u = BundleFormat.contentUuid(Seq("a", "b"))
    assertEquals(u, BundleFormat.contentUuid(Seq("a", "b")))
    assertNotEquals(u, BundleFormat.contentUuid(Seq("a", "c")))
    assertEquals(u.version, 8)
    assertEquals(u.variant, 2)

  test("compatibility: a newer format or schema is refused, an older one accepted, each reason given"):
    val m = BundleManifest(Bundle.Format, Bundle.FormatVersion, "33", U1, BundleInstance(I), None, None, Nil,
      BundleBlobs(0, 0), BundleReference(0, "", 0, ""))
    assertEquals(BundleFormat.compatible(m, Some("33")), Nil)
    assertEquals(BundleFormat.compatible(m, Some("40")), Nil)
    assert(BundleFormat.compatible(m, Some("32")).exists(_.contains("newer than this server")))
    assertEquals(BundleFormat.compatible(m.copy(format = "x", formatVersion = 99), Some("33")).size, 2)

  test("file checks list every missing, unexpected and differing file"):
    val f = BundleFileInfo("entities/goals.ndjson", 1, 10, "a")
    val m = BundleManifest(Bundle.Format, 1, "33", U1, BundleInstance(I), None, None,
      List(f, BundleFileInfo("entities/queue.ndjson", 0, 0, "e")), BundleBlobs(0, 0), BundleReference(0, "", 0, ""))
    val ps = BundleFormat.checkFiles(m, Map(f.path -> f.copy(sha256 = "b"), "entities/x.ndjson" -> f))
    assertEquals(ps.size, 3, ps.mkString("\n"))

  // ── entities ─────────────────────────────────────────────────────────────────────────────────

  test("entities: blob references, dangling references and asOf"):
    val src = BundleSource(U1, I, "ck2001_a", "T", Nil, None, None, None, SourceStatus.Current, Nil, Nil, Nil, None,
      None, None, None, None, None, "2026-01-01", Some("b" * 64), Some("c" * 64), Some("pdf"), "2026-10-02T00:00:00.000000Z")
    val es = BundleEntities(
      sources = List(src),
      findings = List(BundleFinding(U2, I, U1, "ck2001_a#0", "c", None, None, None, None, None, Nil, None, None,
        "2026-10-03T00:00:00.000000Z")),
      edges = List(BundleEdge(U1, U3, "references")))
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

  private def event(extra: BundleEvent => BundleEvent = identity) = extra(BundleEvent(U1, I, "e1", "Eruption", "eruption",
    Some(-1600), None, None, Some(-1500), "attested", None, None, None, None, None, None, Nil, Nil, Nil,
    "2026-10-03T00:00:00.000000Z"))

  test("an event's catalogue fields are absent when empty: the rev of an event compiled from sources is unchanged"):
    // none of the new keys is written: the line (and so the rev) is the one written before they existed
    val plain = BundleFormat.stamp(event())
    List("catalogue", "names", "wikidata", "central", "plusMinus", "datingMethod").foreach(f =>
      assert(!plain.line.contains(s"\"$f\""), plain.line))
    assertEquals(BundleFormat.decodeLine[BundleEvent](plain.line, stamped = true).map(_.names), Right(None))
    val gvp = BundleFormat.stamp(event(_.copy(catalogue = Some("gvp"), names = Some(List("Thera", "Santorini")),
      wikidata = Some("Q1"), central = Some(-1560), plusMinus = Some(10), datingMethod = Some("radiocarbon"))))
    assertNotEquals(gvp.rev, plain.rev)
    assertEquals(BundleFormat.decodeLine[BundleEvent](gvp.line, stamped = true).map(e => (e.catalogue, e.names, e.plusMinus)),
      Right((Some("gvp"), Some(List("Thera", "Santorini")), Some(10L))))

  test("the event-catalogue sets travel in files of their own, round-trip, and are empty in an older bundle"):
    val at = "2026-10-05T00:00:00.000000Z"
    val es = BundleEntities(
      events = List(event()),
      eventCatalogue = List(BundleEventCatalogue(U1, "gvp", "212040", Some("https://volcano.si.edu/x"), "Santorini",
        "eruption", Some(-1610), None, None, Some(-1600), Some(36.4), Some(25.4), Some(7), None, None, "attested",
        io.circe.Json.obj("VEI" -> io.circe.Json.fromInt(7)), at)),
      eventCandidates = List(BundleEventCandidate(U1, U2, "ck2001_a", 0.75, "confirmed", List("ck2001_a#0"),
        io.circe.Json.arr(), List("near"), at)),
      eventChecks = List(BundleEventCheck(U1, at)),
      corrections = List(BundleCorrection("event", "e1", Some(U1), "label", "\"Thera\"", Some("gvp"), "\"Santorini\"",
        None, "active", at, "2026-10-06T00:00:00.000000Z", None)),
      siteTypes = List(BundleSiteType("pleiades:599928", Some("settlement"), Some(at))))
    val payloads = es.payloads.toMap
    List(BundleFiles.EventCatalogue, BundleFiles.EventCandidates, BundleFiles.EventChecks, BundleFiles.Corrections,
      BundleFiles.SiteTypes).foreach(f => assertEquals(BundleFormat.lines(payloads(f)), 1, f))
    val (back, problems) = BundleEntities.decode(payloads)
    assertEquals(problems, Nil)
    assertEquals(back.copy(events = Nil), es.copy(events = Nil))
    // the newest of them is the bundle's asOf (the next --since)
    assertEquals(es.asOf, Some("2026-10-06T00:00:00.000000Z"))
    // a bundle written before these files existed: they read as empty
    val (old, oldProblems) = BundleEntities.decode(payloads -- List(BundleFiles.EventCatalogue, BundleFiles.EventCandidates,
      BundleFiles.EventChecks, BundleFiles.Corrections, BundleFiles.SiteTypes))
    assertEquals(oldProblems, Nil)
    assertEquals((old.eventCatalogue, old.eventCandidates, old.eventChecks, old.corrections, old.siteTypes),
      (Nil, Nil, Nil, Nil, Nil))
    assertEquals(old.events.map(_.id), List("e1"))

  test("the event-catalogue sets localize: a matched event's uid is replaced by the local one"):
    val es = BundleEntities(eventCatalogue = List(BundleEventCatalogue(U1, "gvp", "1", None, "x", "eruption", None, None,
        None, None, None, None, None, None, None, "attested", io.circe.Json.obj(), "2026-10-05T00:00:00.000000Z")),
      eventCandidates = List(BundleEventCandidate(U1, U2, "ck", 1.0, "proposed", Nil, io.circe.Json.arr(), Nil, "2026-10-05T00:00:00.000000Z")),
      eventChecks = List(BundleEventCheck(U1, "2026-10-05T00:00:00.000000Z")),
      corrections = List(BundleCorrection("event", "e1", Some(U1), "label", "1", None, "2", None, "active",
        "2026-10-05T00:00:00.000000Z", "2026-10-05T00:00:00.000000Z", None)))
    val l = MergePlanner.localize(es, Map(U1 -> U3, U2 -> U1), Map.empty)
    assertEquals(l.eventCatalogue.map(_.eventUid), List(U3))
    assertEquals(l.eventCandidates.map(c => (c.eventUid, c.sourceUid)), List((U3, U1)))
    assertEquals(l.eventChecks.map(_.eventUid), List(U3))
    assertEquals(l.corrections.map(_.targetUid), List(Some(U3)))

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

  test("folding two events keeps one stated dating whole and both sets of names"):
    val w = event(_.copy(names = Some(List("Thera")), catalogue = None))
    val o = event(_.copy(uid = U2, names = Some(List("Santorini", "Thera")), catalogue = Some("gvp"), central = Some(-1560),
      plusMinus = Some(10), datingMethod = Some("radiocarbon")))
    val f = MergeFold.foldEvent(w, o)
    assertEquals(f.names, Some(List("Thera", "Santorini")))
    assertEquals(f.catalogue, Some("gvp"))
    assertEquals((f.central, f.plusMinus, f.datingMethod), (Some(-1560L), Some(10L), Some("radiocarbon")))
    val w2 = w.copy(plusMinus = Some(50))
    assertEquals(MergeFold.foldEvent(w2, o).central, None, "the winner's own statement is not mixed with the other's")

  test("the reader reports every problem of a bundle, not just the first"):
    val es  = BundleEntities(goals = List(goal("g")))
    val ps  = es.payloads
    val m   = BundleManifest(Bundle.Format, 1, "33", U1, BundleInstance(I), None, None,
      ps.map((p, b) => BundleFormat.fileInfo(p, b)), BundleBlobs(1, 3), BundleReference(0, "", 0, ""))
    val ok  = tar((Bundle.ManifestPath -> BundleFormat.manifestBytes(m)) :: ps.map(identity) ++
      List(Bundle.blobPath("ab" * 32) -> Array[Byte](1, 2, 3))*)
    val l   = BundleReader.read(ByteArrayInputStream(ok), hashBlobs = true)
    // the blob's content is not its name, and nothing references it
    assert(l.problems.exists(_.contains("hashes to")), l.problems.mkString("\n"))
    assert(l.problems.exists(_.contains("nothing references it")), l.problems.mkString("\n"))
    assertEquals(l.entities.goals.map(_.slug), List("g"))
    val noManifest = BundleReader.read(ByteArrayInputStream(tar(ps*)), hashBlobs = false)
    assert(noManifest.manifest.isEmpty)
    assert(noManifest.problems.exists(_.contains("first entry")))

  // ── decisions ────────────────────────────────────────────────────────────────────────────────

  import BundlePlan.{Decision, Ledger, Local}

  test("decisions: the whole table of a two-way merge with the ledger as ancestor"):
    def d(local: Option[String], tomb: Boolean = false, ledger: Option[Ledger] = None, own: Boolean = false) =
      BundlePlan.entity("F", Local(local, tomb, ledger), own)
    assertEquals(d(None), Decision.Insert)
    assertEquals(d(None, tomb = true), Decision.SkipDeleted)
    assertEquals(d(Some("F")), Decision.Unchanged)
    assertEquals(d(Some("L"), own = true), Decision.KeepLocal)
    assertEquals(d(Some("L"), ledger = Some(Ledger("F", Some("X")))), Decision.KeepLocal)     // theirs unchanged
    assertEquals(d(Some("L"), ledger = Some(Ledger("E", Some("L")))), Decision.FastForward)   // ours unchanged
    assertEquals(d(Some("L"), ledger = Some(Ledger("E", Some("X")))), Decision.Conflict)      // both changed
    assertEquals(d(Some("L")), Decision.Conflict)                                             // never merged

  test("tombstones: delete only what is still as the last merge left it; record the rest"):
    def t(local: Option[String], tomb: Boolean = false, ledger: Option[Ledger] = None) =
      BundlePlan.tombstone(Local(local, tomb, ledger), ownBundle = false)
    assertEquals(t(Some("L"), ledger = Some(Ledger("E", Some("L")))), Decision.Delete)
    assertEquals(t(Some("L"), ledger = Some(Ledger("E", Some("X")))), Decision.Conflict)
    assertEquals(t(Some("L")), Decision.Conflict)
    assertEquals(t(None), Decision.RecordTombstone)
    assertEquals(t(None, tomb = true), Decision.Unchanged)

  test("the ledger keeps the old ancestor for a kept row, so a later change over there is a conflict"):
    assertEquals(BundlePlan.ledgerAfter(Decision.KeepLocal, "F", tombstone = false), None)
    assertEquals(BundlePlan.ledgerAfter(Decision.Insert, "F", tombstone = false), Some(Ledger("F", Some("F"))))
    assertEquals(BundlePlan.ledgerAfter(Decision.Unchanged, "deleted", tombstone = true), Some(Ledger("deleted", None)))
    assertEquals(BundlePlan.ledgerAfter(Decision.Conflict, "F", tombstone = false), None)

  test("cascade: a line needing a row deleted here is skipped too, transitively; optional links are not"):
    val (src, asset, link, ev) = (U1, U2, UUID.randomUUID(), UUID.randomUUID())
    val refs = List(
      BundleEntities.Reference(BundleKind.Source, src, asset, "asset", required = true),
      BundleEntities.Reference(BundleKind.Asset, asset, link, "link", required = true),
      BundleEntities.Reference(BundleKind.Event, src, ev, "parent", required = false))
    val out = BundlePlan.cascade(Map(src -> Decision.SkipDeleted, asset -> Decision.Insert, link -> Decision.Insert,
      ev -> Decision.Insert), refs, _ => false)
    assertEquals(out(asset), Decision.SkipDeleted)
    assertEquals(out(link), Decision.SkipDeleted)
    assertEquals(out(ev), Decision.Insert)
    // a parent outside the bundle, tombstoned here, counts as gone too
    assertEquals(BundlePlan.cascade(Map(asset -> Decision.Insert), refs.take(1), _ == src)(asset), Decision.SkipDeleted)

  test("counts tally every decision under its kind"):
    val c = BundlePlan.counts(BundleKind.Goal, List(Decision.Insert, Decision.Insert, Decision.Conflict,
      Decision.RecordTombstone, Decision.Delete))
    assertEquals(c, BundleKindCounts(BundleKind.Goal, inserted = 2, conflicts = 1, unchanged = 1, deleted = 1))
    assertEquals(c.changed, 3)

  test("kinds: wire names match the tombstone trigger's, files are distinct"):
    assertEquals(BundleKind.values.toList.map(_.wire), List("source", "finding", "article", "article-version",
      "asset", "asset-link", "attestation", "event", "goal", "queue-item", "place", "period"))
    assertEquals(BundleFiles.all.distinct.size, BundleFiles.all.size)
    BundleKind.values.foreach(k => assertEquals(BundleKind.fromWire(k.wire), Some(k)))

  test("markdown: an import report says what it did, and lists every problem"):
    val r = BundleImportResult(U1, BundleInstance(I, Some("work")), dryRun = false, applied = false,
      List(BundleKindCounts(BundleKind.Source, inserted = 2)), 0, List("source x: clash"), Nil, Nil)
    val md = BundleMarkdown.importResult(r)
    assert(md.startsWith("**Refused**"), md)
    assert(md.contains("- source x: clash"), md)
    assert(md.contains("work ("), md)
    assert(BundleMarkdown.importResult(r.copy(problems = Nil, applied = true, counts = Nil)).contains("Nothing to change"))
