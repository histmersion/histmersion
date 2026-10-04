package com.histmersion.core

import java.util.UUID

/** How two records that are one are folded into one (docs/research-merge-import.md §5.3–§5.4,
  * milestones M4–M5). The survivor is always THIS collection's row (left): it keeps its uid, and the
  * bundle's (right) uid becomes a redirect to it. Pure; the planner decides which records are one and
  * what the user chose, these functions only compute the result.
  *
  * Left is this collection, right the bundle — as everywhere in the merge. */
object MergeFold:

  // ── small rules ─────────────────────────────────────────────────────────────────────────────

  /** The richer of two spellings of one value: more words (a subtitle), then more letters outside
    * ASCII (diacritics kept), then more capitals (title case); a tie keeps the left. */
  def richer(l: String, r: String): String =
    def score(s: String) = (MergeText.tokens(s).size, s.count(c => c.isLetter && c > 127), s.count(_.isUpper), s.length)
    if summon[Ordering[(Int, Int, Int, Int)]].gt(score(r), score(l)) then r else l

  /** The richer author list: as many names, then the longer written-out names (`Hans G.` over `H. G.`). */
  def richerAuthors(l: List[String], r: List[String]): List[String] =
    def score(as: List[String]) =
      (as.size, as.flatMap(MergeText.person).map(p => p.forenames.map(_.length).sum).sum,
        as.map(_.count(c => c.isLetter && c > 127)).sum)
    if summon[Ordering[(Int, Int, Int)]].gt(score(r), score(l)) then r else l

  /** Left's values, then right's that are not already there (compared after `key`). */
  def union(l: List[String], r: List[String], key: String => String): List[String] =
    val seen = l.map(key).toSet
    (l ++ r.filterNot(x => seen(key(x)))).distinctBy(key)

  /** Tags compared after the canonical-tag normaliser (`non-canonical-tag`). */
  def unionTags(l: List[String], r: List[String]): List[String] = union(l, r, Lint.normaliseTag)

  /** Topics and regions compared folded. */
  def unionNames(l: List[String], r: List[String]): List[String] = union(l, r, s => MergeText.fold(s.trim))

  private def text(o: Option[String]): Option[String] = o.map(_.trim).filter(_.nonEmpty)

  /** The smallest range covering both; an open bound stays open. */
  def hull(a: BundleRange, b: BundleRange): BundleRange =
    BundleRange(a.start.zip(b.start).map((x, y) => math.min(x, y)), a.end.zip(b.end).map((x, y) => math.max(x, y)))

  /** Two ranges share a year (an open bound reaches any year; no range is compatible with any). */
  def overlap(a: Option[BundleRange], b: Option[BundleRange]): Boolean =
    a.zip(b).forall { (x, y) =>
      x.start.zip(y.end).forall((s, e) => s <= e) && y.start.zip(x.end).forall((s, e) => s <= e)
    }

  /** The narrower of two ranges (an open one is the widest); a missing one is no choice. */
  def narrower(a: Option[BundleRange], b: Option[BundleRange]): Option[BundleRange] =
    def width(r: BundleRange): Long = r.start.zip(r.end).map((s, e) => e - s).getOrElse(Long.MaxValue)
    (a, b) match
      case (Some(x), Some(y)) => Some(if width(y) < width(x) then y else x)
      case _                  => a.orElse(b)

  // ── sources (§5.3) ──────────────────────────────────────────────────────────────────────────

  /** One source out of a pair, field by field. `decided` holds the decision of each field issue of
    * the pair (doi, year, status, title, authors, summary, notes, period, body); a field with no issue
    * follows §5.3's automatic rule. `citekey` is the label the survivor keeps (`map:<citekey>`). */
  def foldSource(l: BundleSource, r: BundleSource, decided: Map[IssueKind, MergeDecision], citekey: String): BundleSource =
    def pick[A](k: IssueKind, auto: => A, left: => A, right: => A): A = decided.get(k).map(_.kind) match
      case Some(DecisionKind.KeepRight) => right
      case Some(DecisionKind.KeepLeft)  => left
      case _                            => auto
    def prose(k: IssueKind, a: Option[String], b: Option[String]): Option[String] =
      (text(a), text(b)) match
        case (Some(x), Some(y)) if x != y => decided.get(k).map(_.kind) match
          case Some(DecisionKind.KeepRight) => b
          case Some(DecisionKind.Concat)    => Some(s"$x\n\n$y")
          case _                            => a
        case (Some(_), _) => a
        case (_, Some(_)) => b
        case _            => a
    val keepRightBody = decided.get(IssueKind.BodyDiffers).exists(_.kind == DecisionKind.KeepRight) ||
      (l.bodySha256.isEmpty && r.bodySha256.isDefined)
    val period = (l.period, r.period) match
      case (Some(a), Some(b)) if a != b => decided.get(IssueKind.PeriodDiffers).map(_.kind) match
        case Some(DecisionKind.KeepRight) => Some(b)
        case Some(DecisionKind.Hull)      => Some(hull(a, b))
        case _                            => Some(a)
      case (a, b) => a.orElse(b)
    val status =
      if l.status == r.status || r.status == SourceStatus.Unassessed then l.status
      else if l.status == SourceStatus.Unassessed then r.status
      else pick(IssueKind.StatusDiffers, l.status, l.status, r.status)
    l.copy(
      citekey = citekey,
      title = pick(IssueKind.TitleDiffers, richer(l.title, r.title), l.title, r.title),
      authors = pick(IssueKind.AuthorsDiffers, richerAuthors(l.authors, r.authors), l.authors, r.authors),
      doi = pick(IssueKind.DoiDiffers, l.doi.orElse(r.doi), l.doi.orElse(r.doi), r.doi.orElse(l.doi)),
      year = pick(IssueKind.YearDiffers, l.year.orElse(r.year), l.year.orElse(r.year), r.year.orElse(l.year)),
      venue = (text(l.venue), text(r.venue)) match
        case (Some(a), Some(b)) if MergeText.fingerprint(a) == MergeText.fingerprint(b) => Some(richer(a, b))
        case (a, b)                                                                     => a.orElse(b),
      status = status,
      topics = unionNames(l.topics, r.topics),
      regions = unionNames(l.regions, r.regions),
      tags = unionTags(l.tags, r.tags),
      period = period,
      summary = prose(IssueKind.SummaryDiffers, l.summary, r.summary),
      notes = prose(IssueKind.NotesDiffers, l.notes, r.notes),
      provenance = l.provenance.orElse(r.provenance),
      citedByCount = l.citedByCount.zip(r.citedByCount).map((a, b) => math.max(a, b)).orElse(l.citedByCount).orElse(r.citedByCount),
      influentialCitations = l.influentialCitations.zip(r.influentialCitations).map((a, b) => math.max(a, b))
        .orElse(l.influentialCitations).orElse(r.influentialCitations),
      addedDate = if r.addedDate < l.addedDate then r.addedDate else l.addedDate,
      bodySha256 = if keepRightBody then r.bodySha256 else l.bodySha256.orElse(r.bodySha256),
      originalSha256 = if keepRightBody then r.originalSha256.orElse(l.originalSha256) else l.originalSha256.orElse(r.originalSha256),
      originalExt = if keepRightBody then r.originalExt.orElse(l.originalExt) else l.originalExt.orElse(r.originalExt),
      isbns = Option(unionNames(l.isbns.getOrElse(Nil), r.isbns.getOrElse(Nil)).sorted).filter(_.nonEmpty),
      rev = "")

  // ── findings (§5.4) ─────────────────────────────────────────────────────────────────────────

  /** The claim as compared: folded, stopwords out, de-duplicated, sorted. */
  def claimKey(claim: String): String = MergeText.significant(MergeText.tokens(claim)).distinct.sorted.mkString(" ")

  private def claimWords(claim: String): Set[String] = MergeText.significant(MergeText.tokens(claim)).toSet

  /** Great-circle distance in km. */
  def km(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double =
    val (p1, p2) = (math.toRadians(lat1), math.toRadians(lat2))
    val dp = p2 - p1
    val dl = math.toRadians(lon2 - lon1)
    val a  = math.pow(math.sin(dp / 2), 2) + math.cos(p1) * math.cos(p2) * math.pow(math.sin(dl / 2), 2)
    6371.0 * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

  /** The same place, as far as two findings say: points within `withinKm`, or the same region name
    * (fingerprint), or one of them says nothing about where. */
  def samePlace(a: BundleFinding, b: BundleFinding, withinKm: Double): Boolean =
    val points = for { x <- a.lon; y <- a.lat; u <- b.lon; v <- b.lat } yield km(x, y, u, v) <= withinKm
    val regions = text(a.region).zip(text(b.region)).map((x, y) => MergeText.fingerprint(x) == MergeText.fingerprint(y))
    points.orElse(regions).forall(identity) || points.contains(true) || regions.contains(true)

  /** Caution ranks: contested is the least sure, then low, medium, high. */
  private def caution(c: Confidence): Int = c match
    case Confidence.Contested => 0
    case Confidence.Low       => 1
    case Confidence.Medium    => 2
    case Confidence.High      => 3

  /** How a bundle finding of a merged pair relates to this collection's findings of the survivor. */
  enum FindingMatch:
    /** The same claim (and a compatible time and place): one finding. */
    case Same(right: BundleFinding, left: BundleFinding)
    /** Nearly the same claim at an overlapping time: kept apart unless the user says `merge`. */
    case Near(right: BundleFinding, left: BundleFinding, jaccard: Double)
    /** Nothing here like it: a new finding of the survivor. */
    case Unmatched(right: BundleFinding)

  /** Pair each bundle finding with at most one finding here, one-to-one: the same claim first, then
    * the nearest near-duplicate (token Jaccard ≥ `nearAtLeast` and overlapping periods). */
  def matchFindings(lefts: List[BundleFinding], rights: List[BundleFinding], withinKm: Double,
      nearAtLeast: Double): List[FindingMatch] =
    val rs = rights.sortBy(_.id)
    val ls = lefts.sortBy(_.id)
    val same = rs.foldLeft((Map.empty[UUID, BundleFinding], Set.empty[UUID])) { case ((acc, taken), r) =>
      ls.find(l => !taken(l.uid) && claimKey(l.claim) == claimKey(r.claim) && claimKey(r.claim).nonEmpty &&
        overlap(l.period, r.period) && samePlace(l, r, withinKm)) match
        case Some(l) => (acc + (r.uid -> l), taken + l.uid)
        case None    => (acc, taken)
    }
    val near = rs.filterNot(r => same._1.contains(r.uid)).foldLeft((Map.empty[UUID, (BundleFinding, Double)], same._2)) {
      case ((acc, taken), r) =>
        val w = claimWords(r.claim)
        ls.filterNot(l => taken(l.uid)).filter(l => l.period.isDefined && r.period.isDefined && overlap(l.period, r.period))
          .map(l => l -> MergeText.jaccard(w, claimWords(l.claim))).filter(_._2 >= nearAtLeast)
          .sortBy((l, j) => (-j, l.id)).headOption match
          case Some((l, j)) => (acc + (r.uid -> (l, j)), taken + l.uid)
          case None         => (acc, taken)
    }._1
    rs.map { r =>
      same._1.get(r.uid).map(FindingMatch.Same(r, _))
        .orElse(near.get(r.uid).map((l, j) => FindingMatch.Near(r, l, j)))
        .getOrElse(FindingMatch.Unmatched(r))
    }

  /** One finding out of two (§5.4): left's identity and claim, tags unioned, the more specific period
    * and the point where only one has it, the more cautious confidence. */
  def foldFinding(l: BundleFinding, r: BundleFinding): BundleFinding =
    val period = narrower(l.period, r.period)
    val conf = (l.confidence, r.confidence) match
      case (Some(a), Some(b)) => Some(if caution(b) < caution(a) then b else a)
      case (a, b)             => a.orElse(b)
    val point = if l.lon.isDefined && l.lat.isDefined then (l.lon, l.lat) else (r.lon, r.lat)
    l.copy(region = text(l.region).orElse(text(r.region)), lon = point._1, lat = point._2, period = period,
      periodName = if period == l.period then l.periodName.orElse(r.periodName) else r.periodName.orElse(l.periodName),
      tags = unionTags(l.tags, r.tags), confidence = conf, significance = l.significance.orElse(r.significance), rev = "")

  /** `<citekey>#<n>` labels for `count` new findings: after the highest number in use, never one
    * that is taken. */
  def nextFindingIds(citekey: String, taken: Set[String], count: Int): List[String] =
    val Num = s"""^${java.util.regex.Pattern.quote(citekey)}#(\\d+)$$""".r
    val top = taken.toList.collect { case Num(n) => n.toInt }.maxOption.getOrElse(0)
    Iterator.from(top + 1).map(n => s"$citekey#$n").filterNot(taken).take(count).toList

  // ── assets ──────────────────────────────────────────────────────────────────────────────────

  /** An asset name not taken in the survivor: `<stem>-<label>.<ext>` (then `-2`, `-3`…). `~` of the
    * design is not link-safe in Markdown, so a hyphen. */
  def freeAssetName(name: String, label: String, taken: Set[String]): String =
    val dot  = name.lastIndexOf('.')
    val (stem, ext) = if dot > 0 then (name.take(dot), name.drop(dot)) else (name, "")
    val tag  = AssetLinks.safeName(label)
    Iterator.from(1).map(i => s"$stem-$tag${if i == 1 then "" else s"-$i"}$ext").find(n => !taken(n)).get

  // ── goals ───────────────────────────────────────────────────────────────────────────────────

  /** One goal out of two (§5.4): the status further along `planned → … → done`, notes joined under a
    * header naming where the bundle's came from, tags and regions unioned; the links unioned, the
    * schedules this collection's ([[keepLocalSchedules]]), the run cap ours unless we have none, and
    * the plan as [[planOutcome]] says — `planDecision` answers a [[IssueKind.GoalPlanDiffers]]. */
  def foldGoal(l: BundleGoal, r: BundleGoal, from: String, planDecision: Option[DecisionKind] = None): BundleGoal =
    def rank(s: String) = GoalStatus.all.indexOf(s)
    val notes = (text(l.notes), text(r.notes)) match
      case (Some(a), Some(b)) if a == b || a.contains(b) => Some(a)
      case (Some(a), Some(b))                            => Some(s"$a\n\n### From $from\n\n$b")
      case (a, b)                                        => a.orElse(b)
    val (plan, approved) = planOutcome(l, r) match
      case PlanOutcome.Theirs                                           => (r.plan, r.planApprovedAt)
      case PlanOutcome.Differ if planDecision.contains(DecisionKind.KeepRight) => (r.plan, r.planApprovedAt)
      case _                                                            => (l.plan, l.planApprovedAt)
    keepLocalSchedules(l.copy(status = if rank(r.status) > rank(l.status) then r.status else l.status, notes = notes,
      tags = unionTags(l.tags, r.tags), regions = unionNames(l.regions, r.regions), period = l.period.orElse(r.period),
      parentUid = l.parentUid.orElse(r.parentUid), plan = plan, planApprovedAt = approved,
      budgetUsd = l.budgetUsd.orElse(r.budgetUsd), rev = ""), r)

  /** What two approved plans of one goal come to. */
  enum PlanOutcome:
    /** The same plan, or only ours: ours stays. */
    case Ours
    /** Only the bundle's, or the bundle's was approved later: take it. */
    case Theirs
    /** Two different plans and neither is known to be the newer: a person decides. */
    case Differ

  /** "Newer approved wins" (#881): equal or one missing is no question; two different plans are
    * decided by their approval times when both have one and they differ, else they are a question. */
  def planOutcome(l: BundleGoal, r: BundleGoal): PlanOutcome =
    (l.plan, r.plan) match
      case (_, None)                     => PlanOutcome.Ours
      case (None, Some(_))               => PlanOutcome.Theirs
      case (Some(a), Some(b)) if a == b  => PlanOutcome.Ours
      case _ =>
        (l.planApprovedAt, r.planApprovedAt) match
          case (Some(a), Some(b)) if b > a => PlanOutcome.Theirs
          case (Some(a), Some(b)) if a > b => PlanOutcome.Ours
          case _                           => PlanOutcome.Differ

  /** `l`'s links and `r`'s that `l` lacks (one link per kind and target: the uid when it has one, else
    * the label), in a stable order; `l`'s schedules, and `r`'s of a kind `l` has none of — this
    * collection's schedules are kept (#881), differences are reported by the plan. Absent when empty. */
  def keepLocalSchedules(l: BundleGoal, r: BundleGoal): BundleGoal =
    def key(k: BundleGoalLink) = (k.kind, k.refUid.fold(k.ref)(_.toString))
    val ls = l.links.getOrElse(Nil)
    val links = (ls ++ r.links.getOrElse(Nil).filterNot(x => ls.exists(y => key(y) == key(x))))
      .sortBy(k => (k.kind, k.ref, k.refUid.fold("")(_.toString)))
    val ss = l.schedules.getOrElse(Nil)
    val schedules = (ss ++ r.schedules.getOrElse(Nil).filterNot(x => ss.exists(_.kind == x.kind))).sortBy(_.kind)
    l.copy(links = Option(links).filter(_.nonEmpty), schedules = Option(schedules).filter(_.nonEmpty))

  /** The schedules of one kind that both goals have and that differ: (kind, ours, theirs). */
  def scheduleDifferences(l: BundleGoal, r: BundleGoal): List[(String, BundleGoalSchedule, BundleGoalSchedule)] =
    l.schedules.getOrElse(Nil).flatMap(a => r.schedules.getOrElse(Nil).find(_.kind == a.kind).filter(_ != a).map(b => (a.kind, a, b)))

  // ── queue items ─────────────────────────────────────────────────────────────────────────────

  /** A queue item's text as compared: folded, whitespace collapsed. */
  def queueKey(t: String): String = MergeText.fold(t).trim.replaceAll("\\s+", " ")

  /** One queue item out of two: `done` anywhere is done. */
  def foldQueue(l: BundleQueueItem, r: BundleQueueItem): BundleQueueItem =
    val done = l.status == Queue.Done || r.status == Queue.Done
    l.copy(status = if done then Queue.Done else l.status,
      doneAt = if l.status == Queue.Done then l.doneAt else if done then r.doneAt else l.doneAt,
      topic = l.topic.orElse(r.topic), reason = l.reason.orElse(r.reason), rev = "")

  // ── events ──────────────────────────────────────────────────────────────────────────────────

  /** Two events with one id that say the same about when and where (§5.4): they are one. */
  def sameCore(l: BundleEvent, r: BundleEvent): Boolean =
    l.notBefore == r.notBefore && l.beginBy == r.beginBy && l.endFrom == r.endFrom && l.notAfter == r.notAfter &&
      l.geom == r.geom && l.placeId == r.placeId

  /** Two events under DIFFERENT ids that are one (§5.4): the same label (fingerprint), outer spans
    * that share a year, and the same place — the same gazetteer id, or the same geometry, or neither
    * side placing it. */
  def sameEventAcrossIds(l: BundleEvent, r: BundleEvent): Boolean =
    def outer(e: BundleEvent) = Some(BundleRange(e.notBefore.orElse(e.beginBy), e.notAfter.orElse(e.endFrom)))
    val place = (l.placeId, r.placeId, l.geom, r.geom) match
      case (Some(a), Some(b), _, _)       => a == b
      case (None, None, Some(a), Some(b)) => a == b
      case (None, None, None, None)       => true
      case _                              => false
    MergeText.fingerprint(l.label).nonEmpty && MergeText.fingerprint(l.label) == MergeText.fingerprint(r.label) &&
      overlap(outer(l), outer(r)) && place

  /** One event out of two: `winner`'s fields, the datings (per citekey), participants and outgoing
    * edges of both — the winner's where both have one. */
  def foldEvent(winner: BundleEvent, other: BundleEvent): BundleEvent =
    winner.copy(
      datings = (winner.datings ++ other.datings.filterNot(d => winner.datings.exists(_.citekey == d.citekey))).sortBy(_.citekey),
      participants = (winner.participants ++ other.participants.filterNot(p =>
        winner.participants.exists(w => w.actor == p.actor && w.role == p.role))).sortBy(p => (p.actor, p.role)),
      edges = (winner.edges ++ other.edges.filterNot(e => winner.edges.exists(w => w.dstUid == e.dstUid && w.kind == e.kind)))
        .sortBy(e => (e.dstUid.toString, e.kind)),
      description = winner.description.orElse(other.description), citekey = winner.citekey.orElse(other.citekey),
      sourceUid = if winner.citekey.isDefined then winner.sourceUid else other.sourceUid,
      parentUid = winner.parentUid.orElse(other.parentUid), rev = "")

  // ── places and named periods ────────────────────────────────────────────────────────────────

  /** What decides whether two records of one place id are the same place: where and when. */
  def samePlace(l: BundlePlace, r: BundlePlace): Boolean = l.geom == r.geom && l.period == r.period

  /** One place out of two that are the same place: ours, with the bundle's names, parents, external
    * ids and types that ours lacks. */
  def foldPlace(l: BundlePlace, r: BundlePlace): BundlePlace =
    l.copy(
      names = (l.names ++ r.names.filterNot(n => l.names.exists(m => m.name == n.name && m.lang == n.lang)))
        .sortBy(n => (n.name, n.lang.getOrElse(""))),
      relations = (l.relations ++ r.relations.filterNot(x => l.relations.exists(y => y.parentId == x.parentId && y.kind == x.kind)))
        .sortBy(x => (x.parentId, x.kind)),
      links = (l.links ++ r.links.filterNot(x => l.links.exists(y => y.scheme == x.scheme && y.externalId == x.externalId)))
        .sortBy(x => (x.scheme, x.externalId)),
      types = (l.types ++ r.types.filterNot(x => l.types.exists(_.typeLabel == x.typeLabel))).sortBy(_.typeLabel),
      source = l.source.orElse(r.source), properties = l.properties.orElse(r.properties), rev = "")

  /** Two records of one period id that say the same: the same span and label. */
  def samePeriod(l: BundlePeriod, r: BundlePeriod): Boolean =
    l.period == r.period && MergeText.fingerprint(l.label) == MergeText.fingerprint(r.label)

  // ── articles (§5.4) ─────────────────────────────────────────────────────────────────────────

  /** What two versions of one article come to, given what each side's history holds. */
  enum ArticleOutcome:
    /** The same text. */
    case Same
    /** The bundle's text is an earlier version of ours: ours stays. */
    case Ours
    /** Ours is an earlier version of the bundle's: fast-forward to it. */
    case Theirs
    /** Both changed since a common base, and the three-way merge is clean. */
    case Merged(body: String, baseSha: String)
    /** Both changed since a common base, and `conflicts` regions changed differently on each side. */
    case Conflict(conflicts: Int, baseSha: String)
    /** Nothing in the two histories is shared: no base to merge from. */
    case NoBase

  /** `localHistory` is this collection's recorded versions of the article (sha256 of the body, the
    * body), newest first; `foreignHistory` the sha256s of the bundle's. The common base is the newest
    * of ours the bundle's history also has. */
  def articleOutcome(localBody: String, localHistory: List[(String, String)], foreignBody: String,
      foreignHistory: Set[String]): ArticleOutcome =
    val (ls, fs) = (BundleFormat.sha256Hex(localBody), BundleFormat.sha256Hex(foreignBody))
    if ls == fs then ArticleOutcome.Same
    else if localHistory.exists(_._1 == fs) then ArticleOutcome.Ours
    else if foreignHistory(ls) then ArticleOutcome.Theirs
    else
      localHistory.find((sha, _) => foreignHistory(sha)) match
        case None => ArticleOutcome.NoBase
        case Some((sha, base)) =>
          val m = Diff3.merge(base, localBody, foreignBody)
          if m.clean then ArticleOutcome.Merged(m.text, sha) else ArticleOutcome.Conflict(m.conflicts, sha)

  /** The fields of an article folded around a chosen body: left's title and kind, the summary that is
    * there, tags and regions unioned. */
  def foldArticle(l: BundleArticle, r: BundleArticle, body: String): BundleArticle =
    l.copy(body = body, summary = if text(l.summary).isDefined then l.summary else r.summary.orElse(l.summary),
      tags = unionTags(l.tags, r.tags), regions = unionNames(l.regions, r.regions), period = l.period.orElse(r.period),
      rev = "")
