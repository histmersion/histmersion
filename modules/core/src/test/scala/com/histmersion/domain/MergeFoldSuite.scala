package com.histmersion.domain

import com.histmersion.domain.catalogue.events.EventPlan

import com.histmersion.api.*
import com.histmersion.files.*
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import MergeFixture.{home, src, uid, work}
import MergeFold.{ArticleOutcome, FindingMatch}
import com.histmersion.values.{Confidence, DecisionKind, GoalStatus, IssueKind, SourceStatus}
import com.histmersion.util.data.exchange.BundleFormat

/** Folding two records that are one into one (docs/research-merge-import.md §5.3–§5.4): sources field by field,
  * findings, goals, queue items, events and articles.
  */
class MergeFoldSuite extends munit.FunSuite:

  private val At = "2026-10-01T09:00:00.000000Z"
  private def finding(
      id: String,
      ck: String,
      claim: String,
      period: Option[BundleRange] = Some(BundleRange(Some(-1400), Some(-1200))),
      lonLat: Option[(Double, Double)] = Some((34.6, 40.0)),
      tags: List[String] = Nil,
      confidence: Option[Confidence] = Some(Confidence.High),
      region: Option[String] = Some("Anatolia")
  ): BundleFinding =
    BundleFinding(
      uid(id),
      work,
      uid(ck),
      id,
      claim,
      region,
      lonLat.map(_._1),
      lonLat.map(_._2),
      period,
      None,
      tags,
      confidence,
      None,
      At
    )

  test(
    "a source folded: the richer title and authors, unions of tags and topics, the decisions where the sides differ"
  ):
    val l = src(
      "h",
      "guterbock1997_hittite",
      "Perspectives on Hittite civilization",
      List("Güterbock, H. G."),
      Some(1997),
      notes = Some("home's"),
      origin = home
    ).copy(tags = List("hittites", "Storm-God"), topics = List("Anatolia"))
    val r = src(
      "w",
      "guterbock1997_perspectives",
      "Perspectives on Hittite Civilization: Selected Writings",
      List("Güterbock, Hans Gustav"),
      Some(1997),
      doi = Some("10.5555/oip26"),
      notes = Some("work's")
    )
      .copy(
        tags = List("storm_god", "religion"),
        topics = List("anatolia", "Religion"),
        status = SourceStatus.Contested
      )
    val f = MergeFold.foldSource(
      l,
      r,
      Map(IssueKind.NotesDiffers -> MergeDecision.of(DecisionKind.Concat)),
      "guterbock1997_hittite"
    )
    assertEquals(f.uid, l.uid)
    assertEquals(f.origin, home)
    assertEquals(f.citekey, "guterbock1997_hittite")
    assertEquals(f.title, "Perspectives on Hittite Civilization: Selected Writings")
    assertEquals(f.authors, List("Güterbock, Hans Gustav"))
    assertEquals(f.doi, Some("10.5555/oip26")) // a DOI on one side only is carried over
    assertEquals(f.tags, List("hittites", "Storm-God", "religion"))
    assertEquals(f.topics, List("Anatolia", "Religion"))
    assertEquals(f.notes, Some("home's\n\nwork's"))
    assertEquals(f.status, l.status) // two verdicts: ours until decided…
    val taken =
      MergeFold.foldSource(l, r, Map(IssueKind.StatusDiffers -> MergeDecision.of(DecisionKind.KeepRight)), l.citekey)
    assertEquals(taken.status, SourceStatus.Contested) // …keep-right takes theirs
    assertEquals(taken.notes, Some("home's")) // no decision on the notes: ours
    // an unassessed side never wins over a verdict
    assertEquals(
      MergeFold.foldSource(l.copy(status = SourceStatus.Unassessed), r, Map.empty, l.citekey).status,
      SourceStatus.Contested
    )

  test("a source folded: the period by hull, the body and original from the side decided"):
    val l = src("h", "a2000_x", "X", Nil, Some(2000), body = Some("ours"), sha = Some("1" * 64), origin = home)
      .copy(period = Some(BundleRange(Some(-1500), Some(-1300))))
    val r = src("w", "a2000_y", "X", Nil, Some(2000), body = Some("theirs"), sha = Some("2" * 64))
      .copy(period = Some(BundleRange(Some(-1400), Some(-1100))))
    val hull =
      MergeFold.foldSource(l, r, Map(IssueKind.PeriodDiffers -> MergeDecision.of(DecisionKind.Hull)), l.citekey)
    assertEquals(hull.period, Some(BundleRange(Some(-1500), Some(-1100))))
    assertEquals(hull.bodySha256, l.bodySha256)
    val right =
      MergeFold.foldSource(l, r, Map(IssueKind.BodyDiffers -> MergeDecision.of(DecisionKind.KeepRight)), l.citekey)
    assertEquals(right.bodySha256, r.bodySha256)
    assertEquals(right.originalSha256, r.originalSha256)
    // one side without a body: the other's
    assertEquals(MergeFold.foldSource(l.copy(bodySha256 = None), r, Map.empty, l.citekey).bodySha256, r.bodySha256)

  test("findings: the same claim folds, a near duplicate is offered, the rest are new — one to one"):
    val lefts = List(
      finding("h#1", "h", "Tarhunna is the storm god of Hatti", tags = List("home")),
      finding("h#2", "h", "The storm god Tarhunna ruled Hatti")
    )
    val rights = List(
      finding("w#1", "w", "the storm god of Hatti is Tarhunna", tags = List("work"), confidence = Some(Confidence.Low)),
      finding("w#2", "w", "Tarhunna the storm god ruled Hatti and Arinna"),
      finding("w#3", "w", "Hattusa burned around 1180 BC"),
      finding("w#4", "w", "Tarhunna is the storm god of Hatti", period = Some(BundleRange(Some(-500), Some(-400))))
    )
    val ms = MergeFold.matchFindings(lefts, rights, 50, 0.6)
    assertEquals(ms.collect { case FindingMatch.Same(r, l) => r.id -> l.id }, List("w#1" -> "h#1"))
    assertEquals(ms.collect { case FindingMatch.Near(r, l, _) => r.id -> l.id }, List("w#2" -> "h#2"))
    // a different period is not the same finding
    assertEquals(ms.collect { case FindingMatch.Unmatched(r) => r.id }, List("w#3", "w#4"))
    val f = MergeFold.foldFinding(lefts.head, rights.head)
    assertEquals(f.uid, lefts.head.uid)
    assertEquals(f.tags, List("home", "work"))
    assertEquals(f.confidence, Some(Confidence.Low)) // the more cautious
    // far apart, and in different regions: not the same place
    val far =
      finding("w#5", "w", "Tarhunna is the storm god of Hatti", lonLat = Some((10.0, 50.0)), region = Some("Bavaria"))
    assert(!MergeFold.samePlace(lefts.head, far, 50))
    assert(MergeFold.samePlace(lefts.head, far.copy(region = Some("anatolia")), 50))

  test("finding ids continue after the highest"):
    assertEquals(
      MergeFold.nextFindingIds("a2000_x", Set("a2000_x#1", "a2000_x#7", "b2000_y#9"), 2),
      List("a2000_x#8", "a2000_x#9")
    )
    assertEquals(MergeFold.nextFindingIds("a2000_x", Set.empty, 1), List("a2000_x#1"))

  test("goals, queue items and events folded"):
    val gl = MergeFixture.goal("g1", "zz_g", "Goal", home, Some("home's plan")).copy(status = GoalStatus.Seeding)
    val gr = MergeFixture.goal("g2", "zz_g", "Goal", work, Some("work's plan")).copy(status = GoalStatus.Compiled)
    val g = MergeFold.foldGoal(gl, gr, "work")
    assertEquals(g.status, GoalStatus.Compiled)
    assertEquals(g.notes, Some("home's plan\n\n### From work\n\nwork's plan"))
    assertEquals(MergeFold.foldGoal(gl, gl.copy(uid = gr.uid), "work").notes, gl.notes)
    val q = BundleQueueItem(uid("q1"), home, "tasks", "Find the Kuhrt reprint", None, Queue.Open, None, At, None, At)
    val qd = q.copy(uid = uid("q2"), text = "find the  kuhrt reprint", status = Queue.Done, doneAt = Some(At))
    assertEquals(MergeFold.queueKey(q.text), MergeFold.queueKey(qd.text))
    assertEquals(MergeFold.foldQueue(q, qd).status, Queue.Done)
    assertEquals(MergeFold.foldQueue(q, qd).doneAt, Some(At))
    val e1 = MergeFixture.event("e1", "zz_e", "Here", home)
    val e2 = MergeFixture
      .event("e2", "zz_e", "There", work)
      .copy(datings = List(BundleEventDating("a2000_x", None, Some(-1200), None, None, Some(-1190), None, None)))
    assert(EventPlan.sameCore(e1, e2))
    assert(!EventPlan.sameCore(e1, e2.copy(notAfter = Some(-1100))))
    val e = EventPlan.foldEvent(e1, e2)
    assertEquals(e.label, "Here")
    assertEquals(e.datings.map(_.citekey), List("a2000_x"))

  test("articles: the same, ours ahead, theirs ahead, a clean three-way merge, a conflict, no base"):
    val base = "Intro.\n\nMiddle.\n\nEnd."
    def sha(s: String) = BundleFormat.sha256Hex(s)
    val ours = "Intro (home).\n\nMiddle.\n\nEnd."
    val theirs = "Intro.\n\nMiddle.\n\nEnd (work)."
    assertEquals(MergeFold.articleOutcome(ours, Nil, ours, Set.empty), ArticleOutcome.Same)
    assertEquals(MergeFold.articleOutcome(ours, List(sha(base) -> base), base, Set.empty), ArticleOutcome.Ours)
    assertEquals(MergeFold.articleOutcome(base, Nil, theirs, Set(sha(base))), ArticleOutcome.Theirs)
    assertEquals(
      MergeFold.articleOutcome(ours, List(sha(base) -> base), theirs, Set(sha(base))),
      ArticleOutcome.Merged("Intro (home).\n\nMiddle.\n\nEnd (work).", sha(base))
    )
    assertEquals(
      MergeFold.articleOutcome(ours, List(sha(base) -> base), "Intro (work).\n\nMiddle.\n\nEnd.", Set(sha(base))),
      ArticleOutcome.Conflict(1, sha(base))
    )
    assertEquals(MergeFold.articleOutcome(ours, List(sha(base) -> base), theirs, Set.empty), ArticleOutcome.NoBase)
    // the newest common version is the base
    val older = "Intro.\n\nEnd."
    assertEquals(
      MergeFold.articleOutcome(ours, List(sha(base) -> base, sha(older) -> older), theirs, Set(sha(older), sha(base))),
      ArticleOutcome.Merged("Intro (home).\n\nMiddle.\n\nEnd (work).", sha(base))
    )
