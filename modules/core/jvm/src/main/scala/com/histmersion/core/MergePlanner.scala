package com.histmersion.core

import BundlePlan.{Decision, Ledger, Local}
import MergeText.MinHash
import SourceMatcher.{Outcome, Pair, Side}

import io.circe.{Decoder, Encoder, Json, JsonObject}
import java.util.UUID

/** `merge plan` as a pure function (docs/research-merge-import.md §5–§6, milestone M3): of the
  * bundle's entities, what this collection knows (its rows, labels, ledger, redirects and
  * tombstones), the OCR-body sketches and the resolutions. The database layer gathers the [[Input]];
  * nothing here reads or writes anything.
  *
  * The stages never stop early — every issue of every stage is listed in one run:
  *
  *  1. **identity**: a bundle uid that a redirect (`uid_aliases`) or the ledger maps onto a row here
  *     is THAT row ([[mapUid]]); the bundle's lines are rewritten into this collection's uids and
  *     labels ([[localize]]) before anything is compared;
  *  2. **sources**: the rest are matched ([[SourceMatcher]]): auto-merge, needs-decision (a
  *     [[IssueKind.SameWork]] issue), or new — a new one whose citekey is taken here is a
  *     [[IssueKind.CitekeyCollision]];
  *  3. **fields** of every pair that is (or may be) merged: DOI, year, status, title, authors,
  *     summary, notes, period, OCR body. Under an open SameWork issue they are CONDITIONAL
  *     (`if S-… = map`), and the collision a `both` would cause is conditional too;
  *  4. **rows**: every line as M2 decides it (the ledger as common ancestor, [[BundlePlan]]); a row
  *     changed on both sides, or deleted there and changed here, is an issue; a label a different
  *     record has here is an issue;
  *  5. what cannot be imported whatever is decided (a reference to nothing, a gazetteer place this
  *     collection lacks, a newer format) is a problem.
  *
  * Issue ids hash the issue's kind and the uids and revs it is about ([[issueId]]): they stay the same
  * while those records do not change, and a resolution whose id is no longer in the plan is reported,
  * never re-applied. */
object MergePlanner:

  /** One row of `merge_ledger`: the foreign rev merged, the local uid it maps to, the local rev left. */
  final case class LedgerEntry(foreignRev: String, localUid: UUID, localRevAfter: Option[String])

  /** Who has which label here: citekeys and old citekeys (aliases) → source uid; finding ids, article
    * slugs, event ids, goal slugs → uid; (source uid, asset name) → asset uid. */
  final case class Labels(
      citekeys: Map[String, UUID] = Map.empty,
      aliases: Map[String, UUID] = Map.empty,
      findings: Map[String, UUID] = Map.empty,
      slugs: Map[String, UUID] = Map.empty,
      events: Map[String, UUID] = Map.empty,
      goals: Map[String, UUID] = Map.empty,
      assets: Map[(UUID, String), UUID] = Map.empty
  )

  final case class Input(
      manifest: BundleManifest,
      here: BundleInstance,
      /** Format and schema against this server (`BundleFormat.compatible`). */
      compat: List[String],
      /** What reading the bundle found wrong (bad lines, checksums, missing blobs). */
      readProblems: List[String],
      foreign: BundleEntities,
      uidAliases: Map[UUID, UUID],
      ledger: Map[UUID, LedgerEntry],
      /** Tombstones here, among the uids the plan asks about. */
      tombstoned: Set[UUID],
      /** This collection's rows for every uid the bundle names (after [[mapUid]]) and for every
        * article whose slug the bundle uses — as bundle lines, so revs compare. */
      local: BundleEntities,
      /** Every source here, for the matcher. */
      sources: List[BundleSource],
      labels: Labels,
      /** Uids alive here, among those the bundle's lines refer to. */
      live: Set[UUID],
      /** Gazetteer ids present here, among those the bundle's events name. */
      places: Set[String],
      reference: BundleReference,
      /** Old citekeys dropped here by renaming back (V050), with when — ISO-8601 UTC, as bundle
        * timestamps are written, so the two compare as strings. */
      aliasTombstones: Map[String, String] = Map.empty
  ):
    def ownBundle: Boolean = manifest.instance.id == here.id

  /** A line's fate, for the importer: its uid in the bundle and here, its revs, the decision. */
  final case class Row(kind: BundleKind, foreignUid: UUID, uid: UUID, label: String, foreignRev: String,
      localizedRev: String, localRev: Option[String], decision: Decision, tombstone: Boolean)

  final case class Result(
      plan: MergePlan,
      /** The bundle in this collection's uids and labels. */
      localized: BundleEntities,
      rows: List[Row],
      /** Decisions an import cannot carry out (they need `merge apply`), one line each. */
      needsApply: List[String],
      /** Old citekeys of the bundle not to import (a resolved alias conflict, or one dropped here). */
      skipAliases: Set[String],
      /** Optional links the import leaves out, and other notes for the import report. */
      notes: List[String]
  )

  // ── identity ────────────────────────────────────────────────────────────────────────────────

  /** The local uid of a bundle uid: a redirect (followed to its end), else the ledger's local uid,
    * else itself. */
  def mapUid(aliases: Map[UUID, UUID], ledger: Map[UUID, LedgerEntry])(f: UUID): UUID =
    @annotation.tailrec
    def follow(u: UUID, seen: Set[UUID]): UUID =
      aliases.get(u) match
        case Some(n) if !seen(n) => follow(n, seen + n)
        case _                   => u
    if aliases.contains(f) then follow(f, Set(f))
    else ledger.get(f).map(_.localUid).filter(_ != f).map(u => follow(u, Set(u))).getOrElse(f)

  // ── localization ────────────────────────────────────────────────────────────────────────────

  private def mapJson(m: Map[String, String])(j: Json): Json =
    j.fold(j, _ => j, _ => j, s => m.get(s).fold(j)(Json.fromString),
      arr => Json.fromValues(arr.map(mapJson(m))),
      obj => Json.fromJsonObject(obj.mapValues(mapJson(m))))

  /** The bundle in this collection's terms: every uid a redirect or the ledger maps (an own uid or a
    * reference) replaced by the local one, and a mapped row's label by its local label — after a
    * merge the local citekey (slug, id) wins. Lines with no mapping are left as they are. */
  def localize(es: BundleEntities, mapped: Map[UUID, UUID], localLabel: Map[UUID, String]): BundleEntities =
    if mapped.isEmpty then es
    else
      val m = mapped.map((f, l) => f.toString -> l.toString)
      def go[A](as: List[A], labelField: Option[String])(using enc: Encoder.AsObject[A], dec: Decoder[A]): List[A] =
        as.map { a =>
          val obj = enc.encodeObject(a)
          val own = obj("uid").flatMap(_.asString).flatMap(u => scala.util.Try(UUID.fromString(u)).toOption)
          val relabelled = (for
            f     <- own
            l     <- mapped.get(f)
            field <- labelField
            lab   <- localLabel.get(l)
          yield obj.add(field, Json.fromString(lab))).getOrElse(obj)
          dec.decodeJson(mapJson(m)(Json.fromJsonObject(relabelled))).getOrElse(a)
        }
      es.copy(
        sources = go(es.sources, Some("citekey")),
        findings = go(es.findings, Some("id")),
        edges = go(es.edges, None),
        unresolvedEdges = go(es.unresolvedEdges, None),
        assessments = go(es.assessments, None),
        aliases = go(es.aliases, None),
        articles = go(es.articles, Some("slug")),
        versions = go(es.versions, None),
        assets = go(es.assets, Some("name")),
        assetLinks = go(es.assetLinks, None),
        attestations = go(es.attestations, None),
        events = go(es.events, Some("id")),
        goals = go(es.goals, Some("slug")),
        queue = go(es.queue, None),
        tombstones = go(es.tombstones, None))

  // ── issue ids ───────────────────────────────────────────────────────────────────────────────

  /** The letter of an issue id: the entity it is about (O for an OCR body, L for an alias). */
  def letter(kind: IssueKind, entity: BundleKind): String = kind match
    case IssueKind.BodyDiffers   => "O"
    case IssueKind.AliasConflict => "L"
    case _ => entity match
      case BundleKind.Source         => "S"
      case BundleKind.Finding        => "F"
      case BundleKind.Article        => "A"
      case BundleKind.ArticleVersion => "V"
      case BundleKind.Asset          => "X"
      case BundleKind.AssetLink      => "K"
      case BundleKind.Attestation    => "T"
      case BundleKind.Event          => "E"
      case BundleKind.Goal           => "G"
      case BundleKind.QueueItem      => "Q"

  /** `<letter>-<8 hex>`: sha256 of the kind, the sorted bundle uids, the sorted local uids, the two
    * revs and anything else that tells the issue apart (an alias). The same records unchanged → the
    * same id; either side changed → a new id, and the old resolution goes stale. Pure. */
  def issueId(kind: IssueKind, entity: BundleKind, right: List[UUID], left: List[UUID], rightRev: String,
      leftRev: String, extra: String = ""): String =
    val text = List(IssueKind.wireOf(kind), right.map(_.toString).sorted.mkString(","),
      left.map(_.toString).sorted.mkString(","), rightRev, leftRev, extra).mkString("\u0000")
    s"${letter(kind, entity)}-${BundleFormat.sha256Hex(text).take(8)}"

  // ── describing records ──────────────────────────────────────────────────────────────────────

  private def describe(s: BundleSource): List[String] =
    val who = s.authors.take(2).mkString("; ") + (if s.authors.length > 2 then " et al." else "")
    List(s"\"${s.title}\"",
      List(Some(who).filter(_.nonEmpty), s.year.map(_.toString), Some(s.doi.fold("no DOI")(d => s"DOI $d")),
        Some(s.status.wire)).flatten.mkString(" · "))

  private def side(s: BundleSource): IssueSide = IssueSide(s.citekey, s.uid, describe(s))

  private def rangeText(r: Option[BundleRange]): String =
    r.fold("none")(b => s"${b.start.fold("…")(_.toString)}..${b.end.fold("…")(_.toString)}")

  // ── sketches ────────────────────────────────────────────────────────────────────────────────

  /** The OCR bodies (by sha256) whose MinHash could decide a pair: those of every blocked pair whose
    * bodies differ — the database computes them before [[plan]]. */
  def sketchesWanted(in: Input, cfg: MergeConfig): Set[String] =
    val (rights, lefts) = matchSides(in)
    val byKey = lefts.flatMap(l => SourceMatcher.blockingKeys(l).map(_ -> l)).groupMap(_._1)(_._2)
    rights.flatMap { r =>
      SourceMatcher.blockingKeys(r).toList.flatMap(k => byKey.getOrElse(k, Nil)).distinctBy(_.uid).flatMap { l =>
        (r.source.bodySha256, l.source.bodySha256) match
          case (Some(a), Some(b)) if a != b => List(a, b)
          case _                            => Nil
      }
    }.toSet

  /** The bundle's sources that need matching (no identity here), and the sources here they may match
    * (not already some bundle source's own, not deleted). */
  private def matchSides(in: Input): (List[Side], List[Side]) =
    val idOf      = mapUid(in.uidAliases, in.ledger)
    val localUids = in.local.sources.map(_.uid).toSet
    val unknown = in.foreign.sources.filter { s =>
      val l = idOf(s.uid)
      l == s.uid && !localUids(l) && !in.tombstoned(l)
    }
    val claimed = in.foreign.sources.map(s => idOf(s.uid)).toSet
    val foreignAliases = in.foreign.aliases.groupMap(_.sourceUid)(_.alias)
    val localAliases   = in.labels.aliases.toList.groupMap(_._2)(_._1)
    val rights = unknown.map(s => Side(s, foreignAliases.getOrElse(s.uid, Nil).toSet))
    val lefts = in.sources.filterNot(s => claimed(s.uid) || in.tombstoned(s.uid))
      .map(s => Side(s, localAliases.getOrElse(s.uid, Nil).toSet))
    (rights, lefts)

  // ── the plan ────────────────────────────────────────────────────────────────────────────────

  /** What became of one of the bundle's sources. */
  private enum Fate:
    case Same(local: UUID, byLedger: Boolean)
    case Deleted
    case Auto(pair: Pair)
    case Gate(pair: Pair, issue: String)
    case New

  /** An issue before resolutions, with the decision kinds an import can carry out itself. */
  private final case class Draft(issue: MergeIssue, importable: Set[DecisionKind])

  def plan(in: Input, sketches: Map[String, MinHash], resolutions: MergeResolutions, cfg: MergeConfig): Result =
    val idOf    = mapUid(in.uidAliases, in.ledger)
    val fes     = in.foreign
    val rawRevs = fes.revs
    val mapped  = (rawRevs.keySet ++ fes.tombstones.map(_.uid)).toList.map(f => f -> idOf(f)).filter((f, l) => f != l).toMap
    val localRevs = in.local.revs
    val localized = localize(fes, mapped, localRevs.view.mapValues(_.label).toMap)
    val locRevs   = localized.revs
    val toLocal: UUID => UUID = u => mapped.getOrElse(u, u)
    val own = in.ownBundle
    val policy = resolutions.policy

    // ── 2. sources ──
    val (rights, lefts) = matchSides(in)
    val matching = SourceMatcher.assign(rights, lefts, sketches, cfg, policy.editions)
    val sourcesByUid = in.sources.map(s => s.uid -> s).toMap
    def leftRev(u: UUID): String = sourcesByUid.get(u).map(s => BundleFormat.stamp(s).rev).getOrElse("")
    def rightRev(u: UUID): String = rawRevs.get(u).map(_.rev).getOrElse("")

    val gateIds: Map[UUID, String] = matching.pairs.filter(_.outcome == Outcome.NeedsDecision).map(p =>
      p.right.uid -> issueId(IssueKind.SameWork, BundleKind.Source, List(p.right.uid), List(p.left.uid),
        rightRev(p.right.uid), leftRev(p.left.uid))).toMap
    val fates: Map[UUID, Fate] = fes.sources.map { s =>
      val l = idOf(s.uid)
      s.uid -> (
        if in.tombstoned(l) && !localRevs.contains(l) then Fate.Deleted
        else if localRevs.contains(l) then Fate.Same(l, l != s.uid)
        else matching.pairs.find(_.right.uid == s.uid) match
          case Some(p) if p.outcome == Outcome.AutoMerge => Fate.Auto(p)
          case Some(p)                                   => Fate.Gate(p, gateIds(s.uid))
          case None                                      => Fate.New)
    }.toMap

    // ── the drafts: every issue, conditional ones included ──
    val drafts = List.newBuilder[Draft]
    def add(d: Draft): Unit = drafts += d
    val km   = DecisionKind
    val lr   = List(km.KeepLeft, km.KeepRight)

    // the gates
    matching.pairs.filter(_.outcome == Outcome.NeedsDecision).foreach { p =>
      val ev = p.evidence
      val title =
        if p.flags.nonEmpty then s"the same work? ${p.flags.mkString("; ")} (score ${SourceMatcher.signed(ev.score)})"
        else s"possibly the same work (score ${SourceMatcher.signed(ev.score)})"
      add(Draft(MergeIssue(gateIds(p.right.uid), IssueKind.SameWork, IssueSeverity.Blocking, BundleKind.Source, title,
        Some(side(p.left.source)), Some(side(p.right.source)),
        ev.why ++ p.alsoLike.take(3).map((ck, sc) => s"also resembles $ck (score ${SourceMatcher.signed(sc)})"),
        List(km.Map, km.Both),
        Option.when(ev.score >= cfg.autoMerge)(MergeDecision.withArg(km.Map, p.left.source.citekey))),
        Set(km.Both)))
    }

    // the fields of a pair that is (or may be) merged
    def fieldIssues(p: Pair, cond: Option[IssueCondition]): List[Draft] =
      val (r, l) = (p.right.source, p.left.source)
      val (fu, lu) = (List(r.uid), List(l.uid))
      val (rr, ll) = (rightRev(r.uid), leftRev(l.uid))
      def mk(kind: IssueKind, title: String, opts: List[DecisionKind], suggested: Option[MergeDecision] = None,
          sev: IssueSeverity = IssueSeverity.Blocking, why: List[String] = Nil): Draft =
        Draft(MergeIssue(issueId(kind, BundleKind.Source, fu, lu, rr, ll), kind, sev, BundleKind.Source, title,
          Some(side(l)), Some(side(r)), why, opts, suggested, cond), Set.empty)
      val doiR = r.doi.flatMap(MergeText.doiKey)
      val doiL = l.doi.flatMap(MergeText.doiKey)
      val sameDoi = doiR.isDefined && doiR == doiL
      List(
        Option.when(doiR.isDefined && doiL.isDefined && doiR != doiL)(
          mk(IssueKind.DoiDiffers, s"DOI differs: ${l.doi.get} here, ${r.doi.get} in the bundle", lr,
            why = p.evidence.strong.map(k => s"matched by $k: one record has the wrong DOI"))),
        r.year.zip(l.year).filter((a, b) => a != b && !(math.abs(a - b) == 1 && sameDoi)).map { (a, b) =>
          val earliest = if b <= a then km.KeepLeft else km.KeepRight
          mk(IssueKind.YearDiffers, s"year differs: $b here, $a in the bundle", lr,
            Some(MergeDecision.of(earliest)), why = List("the suggestion keeps the earliest year (the first edition)"))
        },
        Option.when(r.status != l.status && r.status != SourceStatus.Unassessed && l.status != SourceStatus.Unassessed)(
          mk(IssueKind.StatusDiffers, s"status differs: ${l.status.wire} here, ${r.status.wire} in the bundle", lr)),
        Option.when(p.evidence.titleDiffers)(
          mk(IssueKind.TitleDiffers, "title differs", lr, why = List(s"here: ${l.title}", s"bundle: ${r.title}"))),
        Option.when(p.evidence.authors == MergeText.AuthorAgreement.Disjoint)(
          mk(IssueKind.AuthorsDiffers, "no author in common", lr,
            why = List(s"here: ${l.authors.mkString("; ")}", s"bundle: ${r.authors.mkString("; ")}"))),
        text(r.summary, l.summary).map(_ =>
          mk(IssueKind.SummaryDiffers, "summary differs", List(km.KeepLeft, km.KeepRight, km.Concat))),
        text(r.notes, l.notes).map(_ =>
          mk(IssueKind.NotesDiffers, "notes differ", List(km.KeepLeft, km.KeepRight, km.Concat))),
        r.period.zip(l.period).filter((a, b) => a != b).map((a, b) =>
          mk(IssueKind.PeriodDiffers, s"period differs: ${rangeText(Some(b))} here, ${rangeText(Some(a))} in the bundle",
            List(km.KeepLeft, km.KeepRight, km.Hull), Some(MergeDecision.of(km.Hull)))),
        r.bodySha256.zip(l.bodySha256).filter((a, b) => a != b).map { (a, b) =>
          val sim = sketches.get(a).zip(sketches.get(b)).map((x, y) => s" (MinHash ${SourceMatcher.fixed(x.similarity(y), 2)})").getOrElse("")
          mk(IssueKind.BodyDiffers, s"OCR bodies differ$sim", lr, Some(MergeDecision.of(km.KeepLeft)), IssueSeverity.Default,
            List("by default this collection's body stays; keep-right takes the bundle's (and its figures)"))
        }
      ).flatten

    // a bundle source that would be NEW: is its citekey taken here?
    def collision(s: BundleSource, cond: Option[IssueCondition]): Option[Draft] =
      val owner = in.labels.citekeys.get(s.citekey).orElse(in.labels.aliases.get(s.citekey))
        .filterNot(in.tombstoned)
      owner.flatMap(sourcesByUid.get).map { o =>
        val asAlias = !in.labels.citekeys.contains(s.citekey)
        val score = lefts.find(_.uid == o.uid).map(l =>
          SourceMatcher.evidence(Side(s, Set.empty), l, sketches, cfg.weights).score)
        val title =
          if asAlias then s"citekey ${s.citekey} is an old citekey (alias) of another source here"
          else s"same citekey, different works" + score.fold("")(sc => s" (score ${SourceMatcher.signed(sc)})")
        val suggestion = renameSuggestion(s, in, fes)
        Draft(MergeIssue(issueId(IssueKind.CitekeyCollision, BundleKind.Source, List(s.uid), List(o.uid),
          rightRev(s.uid), leftRev(o.uid)), IssueKind.CitekeyCollision, IssueSeverity.Blocking, BundleKind.Source,
          title, Some(side(o)), Some(side(s)), Nil, List(km.RenameRight, km.RenameLeft),
          Some(MergeDecision.withArg(km.RenameRight, suggestion)), cond), Set.empty)
      }

    fes.sources.foreach { s =>
      fates(s.uid) match
        case Fate.Auto(p)    => fieldIssues(p, None).foreach(add)
        case Fate.Gate(p, g) =>
          fieldIssues(p, Some(IssueCondition(g, km.Map))).foreach(add)
          collision(s, Some(IssueCondition(g, km.Both))).foreach(add)
        case Fate.New        => collision(s, None).foreach(add)
        case _               => ()
    }

    // old citekeys of the bundle that name a different source here (issue id → the alias)
    val aliasIssues = Map.newBuilder[String, String]
    fes.aliases.foreach { a =>
      val owner = in.labels.aliases.get(a.alias).orElse(in.labels.citekeys.get(a.alias)).filterNot(in.tombstoned)
      owner.foreach { o =>
        val asCitekey = in.labels.citekeys.get(a.alias).contains(o)
        def conflict(target: UUID, cond: Option[IssueCondition]): Option[Draft] =
          Option.when(o != target) {
            val src = fes.sources.find(_.uid == a.sourceUid)
            val id  = issueId(IssueKind.AliasConflict, BundleKind.Source, List(a.sourceUid), List(o), "", "", a.alias)
            aliasIssues += id -> a.alias
            Draft(MergeIssue(id,
              IssueKind.AliasConflict, IssueSeverity.Blocking, BundleKind.Source,
              s"old citekey ${a.alias} names ${src.fold(a.sourceUid.toString)(_.citekey)} in the bundle, " +
                s"but ${sourcesByUid.get(o).fold(o.toString)(_.citekey)} here" + (if asCitekey then " (its citekey)" else ""),
              sourcesByUid.get(o).map(side), src.map(side),
              List("keep-left drops the bundle's old citekey; this collection's meaning stays"),
              if asCitekey then List(km.KeepLeft) else lr, Some(MergeDecision.of(km.KeepLeft)), cond), Set(km.KeepLeft))
          }
        fates.get(a.sourceUid) match
          case Some(Fate.Same(l, _)) => conflict(l, None).foreach(add)
          case Some(Fate.Auto(p))    => conflict(p.left.uid, None).foreach(add)
          case Some(Fate.Gate(p, g)) =>
            (conflict(p.left.uid, Some(IssueCondition(g, km.Map))), conflict(a.sourceUid, Some(IssueCondition(g, km.Both)))) match
              case (Some(x), Some(_)) => add(x.copy(issue = x.issue.copy(condition = None)))
              case (x, y)             => (x ++ y).foreach(add)
          case Some(Fate.New) | None => conflict(a.sourceUid, None).foreach(add)
          case Some(Fate.Deleted)    => ()
      }
    }

    // ── 4. rows ──
    val ledgerOf: UUID => Option[Ledger] = f =>
      if own then None else in.ledger.get(f).map(e => Ledger(e.foreignRev, e.localRevAfter))
    def localOf(f: UUID): Local =
      val l = toLocal(f)
      Local(localRevs.get(l).map(_.rev), in.tombstoned(l), ledgerOf(f))
    // the source a line belongs to (its fate decides whether the line is held for merge apply)
    val assetSource = fes.assets.map(a => a.uid -> a.sourceUid).toMap
    def ownerSource(f: UUID, kind: BundleKind): Option[UUID] = kind match
      case BundleKind.Source      => Some(f)
      case BundleKind.Finding     => fes.findings.find(_.uid == f).map(_.sourceUid)
      case BundleKind.Asset       => assetSource.get(f)
      case BundleKind.AssetLink   => fes.assetLinks.find(_.uid == f).flatMap(l => assetSource.get(l.assetUid))
      case BundleKind.Attestation => fes.attestations.find(_.uid == f).map(_.sourceUid)
      case _                      => None
    def heldBy(f: UUID, kind: BundleKind): Option[Fate] =
      ownerSource(f, kind).flatMap(fates.get).filter {
        case Fate.Auto(_) | Fate.Gate(_, _) => true
        case _                             => false
      }

    val initialRows: List[Row] =
      rawRevs.toList.sortBy((u, r) => (r.kind.ordinal, u.toString)).map { (f, r) =>
        val l   = toLocal(f)
        val loc = locRevs.get(l).map(_.rev).getOrElse(r.rev)
        val lab = locRevs.get(l).map(_.label).getOrElse(r.label)
        val d =
          if heldBy(f, r.kind).isDefined then Decision.Held
          else BundlePlan.entity(r.rev, loc, localOf(f), own)
        Row(r.kind, f, l, lab, r.rev, loc, localRevs.get(l).map(_.rev), d, tombstone = false)
      } ++ fes.tombstones.map { t =>
        val l = toLocal(t.uid)
        Row(t.kind, t.uid, l, localRevs.get(l).map(_.label).getOrElse(t.uid.toString), BundlePlan.DeletedRev,
          BundlePlan.DeletedRev, localRevs.get(l).map(_.rev), BundlePlan.tombstone(localOf(t.uid), own), tombstone = true)
      }

    // changed on both sides; deleted there and changed here
    initialRows.filter(_.decision == Decision.Conflict).foreach { r =>
      val left  = Some(IssueSide(r.label, r.uid))
      val right = Option.when(!r.tombstone)(IssueSide(rawRevs.get(r.foreignUid).map(_.label).getOrElse(r.label), r.foreignUid))
      val (kind, title, opts, importable) =
        if r.tombstone then
          (IssueKind.DeletedThereChangedHere, "deleted in the bundle, changed here since the last import",
            lr, Set(km.KeepLeft, km.KeepRight))
        else if r.kind == BundleKind.Article then
          (IssueKind.ChangedBothSides, "changed on both sides since the last import (no common base kept)",
            List(km.KeepLeft, km.KeepRight, km.Both, km.File), Set(km.KeepLeft, km.KeepRight))
        else
          (IssueKind.ChangedBothSides, "changed on both sides since the last import",
            lr, Set(km.KeepLeft, km.KeepRight))
      add(Draft(MergeIssue(issueId(kind, r.kind, List(r.foreignUid), List(r.uid), r.foreignRev, r.localRev.getOrElse("")),
        kind, IssueSeverity.Blocking, r.kind, title, left, right, Nil, opts), importable))
    }

    // labels a different record has here — for the lines that would be written
    val deleting: Set[UUID] = initialRows.filter(r => r.tombstone && r.decision == Decision.Delete).map(_.uid).toSet
    val writesInitially: Set[UUID] =
      initialRows.filter(r => r.decision == Decision.Insert || r.decision == Decision.FastForward).map(_.uid).toSet
    val collidingSources: Set[UUID] = fes.sources.filter(s => fates.get(s.uid).contains(Fate.New) &&
      (in.labels.citekeys.contains(s.citekey) || in.labels.aliases.contains(s.citekey))).map(_.uid).toSet
    def taken(owner: Option[UUID], self: UUID): Option[UUID] = owner.filter(o => o != self && !deleting(o))
    val notes = List.newBuilder[String]
    val fatal = List.newBuilder[String]
    def labelTaken[A](kind: BundleKind, rows: List[A], uid: A => UUID, label: A => String, owner: A => Option[UUID],
        what: String, renamable: Boolean, explained: A => Boolean = (_: A) => false): Unit =
      rows.filter(a => writesInitially(uid(a))).foreach { a =>
        val l = label(a)
        taken(owner(a), uid(a)).filterNot(_ => explained(a)).foreach { o =>
          val f = mapped.collectFirst { case (k, v) if v == uid(a) => k }.getOrElse(uid(a))
          add(Draft(MergeIssue(issueId(IssueKind.LabelTaken, kind, List(f), List(o), rawRevs.get(f).map(_.rev).getOrElse(""),
            localRevs.get(o).map(_.rev).getOrElse("")), IssueKind.LabelTaken, IssueSeverity.Blocking, kind,
            s"here that $what belongs to a different record",
            Some(IssueSide(l, o)), Some(IssueSide(l, f)),
            List("keep-left leaves the bundle's record out (and whatever needs it)"), lr), Set(km.KeepLeft)))
        }
        // renamed over there: a bundle import follows a source's rename, nothing else's yet
        localRevs.get(uid(a)).map(_.label).filter(old => old != l && !renamable).foreach(old =>
          fatal += s"${kind.wire} ${uid(a)}: renamed from '$old' to '$l' over there — a bundle import cannot rename a ${kind.wire} yet")
      }
    labelTaken(BundleKind.Source, localized.sources.filter(s => localRevs.contains(s.uid)), _.uid, _.citekey,
      s => in.labels.citekeys.get(s.citekey).orElse(in.labels.aliases.get(s.citekey)), "citekey", renamable = true)
    labelTaken(BundleKind.Finding, localized.findings, _.uid, _.id, f => in.labels.findings.get(f.id), "finding id",
      renamable = true, f => collidingSources(f.sourceUid))
    labelTaken(BundleKind.Event, localized.events, _.uid, _.id, e => in.labels.events.get(e.id), "event id", renamable = false)
    labelTaken(BundleKind.Goal, localized.goals, _.uid, _.slug, g => in.labels.goals.get(g.slug), "goal slug", renamable = false)
    labelTaken(BundleKind.Asset, localized.assets, _.uid, _.name, a => in.labels.assets.get((a.sourceUid, a.name)),
      "asset name (in this source)", renamable = true)
    // an article: the same slug, a different article
    localized.articles.filter(a => writesInitially(a.uid)).foreach { a =>
      taken(in.labels.slugs.get(a.slug), a.uid).foreach { o =>
        val mine = in.local.articles.find(_.uid == o)
        val same = mine.exists(m => m.body == a.body && m.title == a.title)
        val f = mapped.collectFirst { case (k, v) if v == a.uid => k }.getOrElse(a.uid)
        val issue =
          if same then MergeIssue(issueId(IssueKind.ArticleDiverged, BundleKind.Article, List(f), List(o), rightRev(f),
              localRevs.get(o).map(_.rev).getOrElse("")), IssueKind.ArticleDiverged, IssueSeverity.Default,
            BundleKind.Article, "the same text was written on both sides",
            Some(IssueSide(a.slug, o)), Some(IssueSide(a.slug, f)), Nil, List(km.KeepLeft, km.Both),
            Some(MergeDecision.of(km.KeepLeft)))
          else MergeIssue(issueId(IssueKind.ArticleDiverged, BundleKind.Article, List(f), List(o), rightRev(f),
              localRevs.get(o).map(_.rev).getOrElse("")), IssueKind.ArticleDiverged, IssueSeverity.Blocking,
            BundleKind.Article, "two different articles under one slug (no common base)",
            Some(IssueSide(a.slug, o, mine.map(m => s"\"${m.title}\" · ${m.body.length} characters").toList)),
            Some(IssueSide(a.slug, f, List(s"\"${a.title}\" · ${a.body.length} characters"))),
            List(s"both: the bundle's becomes ${a.slug}-${in.manifest.instance.label.getOrElse("bundle")}"),
            List(km.KeepLeft, km.KeepRight, km.Both, km.File))
        add(Draft(issue, Set(km.KeepLeft)))
      }
    }

    // ── resolutions ──
    val all = drafts.result()
    val byId: Map[String, Draft] = all.map(d => d.issue.id -> d).toMap
    val errors = List.newBuilder[String]
    val labelsHere = in.labels.citekeys.keySet ++ in.labels.aliases.keySet ++ fes.sources.map(_.citekey)
    val chosen: Map[String, MergeDecision] = resolutions.resolve.flatMap { r =>
      val id = r.issue.trim
      (byId.get(id), MergeDecision.parse(r.decision)) match
        case (None, _) =>
          errors += s"$id: not an issue of this plan — stale (the records changed since it was decided) or never one"
          None
        case (_, Left(e)) => errors += s"$id: $e"; None
        case (Some(d), Right(dec)) =>
          val i = d.issue
          val argOk: Either[String, Unit] = dec.kind match
            case km.Map =>
              val allowed = List(i.left, i.right).flatten.map(_.label)
              Either.cond(dec.argument.exists(allowed.contains), (),
                s"map takes one of the two citekeys: ${allowed.map(c => s"map:$c").mkString(" or ")}")
            case km.RenameRight | km.RenameLeft =>
              val ck = dec.argument.getOrElse("")
              if !Citekeys.valid(ck) then Left(s"'$ck' is not a citekey (lower-case letters, digits and _)")
              else if labelsHere(ck) then Left(s"'$ck' is already a citekey (or an old one) here or in the bundle")
              else Right(())
            case _ => Right(())
          if !i.options.contains(dec.kind) then
            errors += s"$id: ${dec.wire} is not a decision for this issue (${i.options.map(DecisionKind.wireOf).mkString(" | ")})"
            None
          else argOk match
            case Left(e)  => errors += s"$id: $e"; None
            case Right(_) => Some(id -> dec)
    }.toMap
    val gateDecision: Map[String, MergeDecision] = chosen.filter((id, _) => byId(id).issue.kind == IssueKind.SameWork)
    val live: List[Draft] = all.flatMap { d =>
      d.issue.condition match
        case None => Some(d)
        case Some(c) => gateDecision.get(c.issue) match
          case None                         => Some(d)
          case Some(g) if g.kind == c.when  => Some(d.copy(issue = d.issue.copy(condition = None)))
          case Some(_)                      => None
    }
    val liveIds = live.map(_.issue.id).toSet
    chosen.keys.toList.sorted.filterNot(liveIds).foreach { id =>
      val c = byId(id).issue.condition.get
      errors += s"$id: applies only if ${c.issue} = ${DecisionKind.wireOf(c.when)}, and ${c.issue} is ${gateDecision(c.issue).wire}"
    }
    val decided: List[Draft] = live.map { d =>
      val i = d.issue
      val (dec, by) = chosen.get(i.id) match
        case Some(x) => (Some(x), Some(DecidedBy.Resolution))
        case None if i.kind == IssueKind.YearDiffers && policy.editions.contains(EditionsPolicy.Merge) =>
          (i.suggested, Some(DecidedBy.Policy))
        case None if i.severity == IssueSeverity.Default => (i.suggested, Some(DecidedBy.Default))
        case None => (None, None)
      d.copy(issue = i.copy(decision = dec, decidedBy = by.filter(_ => dec.isDefined)))
    }

    // ── final row decisions ──
    val issueOfRow: Map[UUID, MergeIssue] = decided.map(_.issue).filter(i =>
      i.kind == IssueKind.ChangedBothSides || i.kind == IssueKind.DeletedThereChangedHere ||
        i.kind == IssueKind.LabelTaken || i.kind == IssueKind.ArticleDiverged)
      .flatMap(i => i.right.map(_.uid).orElse(i.left.map(_.uid)).map(toLocal).map(_ -> i)).toMap
    val gateBoth: Set[UUID] = fes.sources.filter(s => fates(s.uid) match
      case Fate.Gate(_, g) => gateDecision.get(g).exists(_.kind == km.Both)
      case _               => false).map(_.uid).toSet
    val resolvedRows = initialRows.map { r =>
      val owner = ownerSource(r.foreignUid, r.kind)
      if r.decision == Decision.Held && owner.exists(gateBoth) then r.copy(decision = Decision.Insert)
      else
        issueOfRow.get(r.uid).flatMap(i => i.decision.map(i -> _)) match
          case Some((i, d)) => (i.kind, d.kind, r.tombstone) match
            case (IssueKind.ChangedBothSides, km.KeepLeft, _)         => r.copy(decision = Decision.Kept)
            case (IssueKind.ChangedBothSides, km.KeepRight, _)        => r.copy(decision = Decision.FastForward)
            case (IssueKind.DeletedThereChangedHere, km.KeepLeft, _)  => r.copy(decision = Decision.Kept)
            case (IssueKind.DeletedThereChangedHere, km.KeepRight, _) => r.copy(decision = Decision.Delete)
            case (IssueKind.LabelTaken | IssueKind.ArticleDiverged, km.KeepLeft, _) => r.copy(decision = Decision.Skip)
            case _                                                    => r
          case None => r
    }

    // what needs a deleted (or skipped, or held) row is not written either
    val refs = localized.references
    val decisionOf0 = resolvedRows.map(r => r.uid -> r.decision).toMap
    val cascaded = BundlePlan.cascade(decisionOf0, refs, u => in.tombstoned(u) && !in.live(u))
    val rows = resolvedRows.map(r => r.copy(decision = cascaded.getOrElse(r.uid, r.decision)))
    val decisionOf = rows.map(r => r.uid -> r.decision).toMap
    def writes(u: UUID): Boolean = decisionOf.get(u).exists(d => d == Decision.Insert || d == Decision.FastForward)
    val deletingNow = rows.filter(r => r.tombstone && r.decision == Decision.Delete).map(_.uid).toSet

    // references a written line makes
    val inBundleLive: UUID => Boolean = u => decisionOf.get(u).exists {
      case Decision.Insert | Decision.FastForward | Decision.Unchanged | Decision.KeepLocal | Decision.Kept |
          Decision.Conflict => true
      case _ => false
    }
    val written = refs.filter(r => writes(r.owner))
    val (_, missing) = written.partition(r => inBundleLive(r.uid) || (in.live(r.uid) && !deletingNow(r.uid)))
    val (required, optional) = missing.partition(_.required)
    required.map(r => s"${r.from}: needs ${r.kind.wire} ${r.uid}, which is neither in the bundle nor here").distinct
      .foreach(fatal += _)
    optional.map(r => s"${r.from}: ${r.kind.wire} ${r.uid} is not here — that link is left out").distinct.foreach(notes += _)
    // gazetteer places
    localized.events.filter(e => writes(e.uid)).flatMap(e => e.placeId.map(e.id -> _)).filterNot((_, p) => in.places(p))
      .foreach((e, p) => fatal += s"event $e: place '$p' is not in this gazetteer — run `histmersion init` (or import that place) first")
    // two bundle lines onto one row here
    mapped.toList.groupBy(_._2).filter(_._2.size > 1).keys.toList.map(_.toString).sorted.foreach(l =>
      fatal += s"several records of the bundle map onto $l here")

    rows.filter(_.decision == Decision.SkipDeleted).foreach(r =>
      notes += s"${r.kind.wire} ${r.label} (${r.uid}): deleted here — not brought back")
    if in.reference != in.manifest.reference then
      notes += s"the gazetteer/periods differ from the bundle's (bundle: ${in.manifest.reference.places} places, " +
        s"${in.manifest.reference.periods} periods) — events point into them by id"
    if own then notes += "this instance's own bundle: rows changed here since are kept"
    // old citekeys of the bundle that were dropped here AFTER the bundle's alias was made (#827)
    val droppedAliases = fes.aliases.filter(a => in.aliasTombstones.get(a.alias).exists(a.createdAt <= _))
    droppedAliases.foreach(a =>
      notes += s"old citekey ${a.alias}: dropped here (renamed back) on ${in.aliasTombstones(a.alias)} — not brought back")

    // ── what an import cannot do ──
    val issues = decided.map(_.issue)
    val needsApply = List.newBuilder[String]
    fes.sources.foreach { s =>
      fates(s.uid) match
        case Fate.Auto(p) =>
          needsApply += s"source ${s.citekey}: the same work as ${p.left.source.citekey} here " +
            s"(${if p.evidence.strongKey then p.evidence.strong.mkString(", ") else s"score ${SourceMatcher.signed(p.evidence.score)}"})" +
            " — folding the two into one is `merge apply`, not built yet"
        case Fate.Gate(p, g) if gateDecision.get(g).exists(_.kind == km.Map) =>
          needsApply += s"source ${s.citekey}: $g decided ${gateDecision(g).wire} — folding it into ${p.left.source.citekey} is `merge apply`, not built yet"
        case _ => ()
    }
    decided.foreach { d =>
      d.issue.decision.foreach { dec =>
        val pairIssue = d.issue.kind match
          case IssueKind.DoiDiffers | IssueKind.YearDiffers | IssueKind.StatusDiffers | IssueKind.TitleDiffers |
              IssueKind.AuthorsDiffers | IssueKind.SummaryDiffers | IssueKind.NotesDiffers | IssueKind.PeriodDiffers |
              IssueKind.BodyDiffers | IssueKind.SameWork => true
          case _ => false
        if !pairIssue && !d.importable(dec.kind) then
          needsApply += s"${d.issue.id} = ${dec.wire}: carrying that out is `merge apply`, not built yet"
      }
    }

    // ── counts and info ──
    val counts = fes.sources.foldLeft(MatchCounts(sources = fes.sources.size)) { (c, s) =>
      fates(s.uid) match
        case Fate.Same(_, false) => c.copy(byUid = c.byUid + 1)
        case Fate.Same(_, true)  => c.copy(byLedger = c.byLedger + 1)
        case Fate.Deleted        => c.copy(deletedHere = c.deletedHere + 1)
        case Fate.Auto(p) if p.evidence.strongKey => c.copy(byStrongKey = c.byStrongKey + 1)
        case Fate.Auto(_)        => c.copy(byScore = c.byScore + 1)
        case Fate.Gate(_, _)     => c.copy(undecided = c.undecided + 1)
        case Fate.New            => c.copy(unmatched = c.unmatched + 1)
    }
    val matches: List[SourceMatch] = fes.sources.sortBy(_.citekey).flatMap { s =>
      fates(s.uid) match
        case Fate.Same(_, _) => None
        case Fate.Deleted    => Some(SourceMatch(s.uid, s.citekey, None, None, MatchMethod.Unmatched, MatchOutcome.DeletedHere))
        case Fate.New        => Some(SourceMatch(s.uid, s.citekey, None, None, MatchMethod.Unmatched, MatchOutcome.New))
        case Fate.Auto(p) =>
          Some(SourceMatch(s.uid, s.citekey, Some(p.left.uid), Some(p.left.source.citekey),
            if p.evidence.strongKey then MatchMethod.StrongKey else MatchMethod.Score, MatchOutcome.AutoMerge,
            Some(p.evidence.score), p.evidence.why))
        case Fate.Gate(p, g) =>
          Some(SourceMatch(s.uid, s.citekey, Some(p.left.uid), Some(p.left.source.citekey), MatchMethod.Score,
            MatchOutcome.NeedsDecision, Some(p.evidence.score), p.evidence.why, Some(g)))
    }
    val held = rows.filter(r => r.decision == Decision.Held && r.kind != BundleKind.Source).groupMapReduce(_.kind)(_ => 1)(_ + _)
    val info = List.newBuilder[String]
    if held.nonEmpty then
      info += s"${held.toList.sortBy(_._1.ordinal).map((k, n) => Plural.count(n, k.wire)).mkString(", ")} belong to " +
        "matched sources: they are matched inside each pair when the pair is merged"
    fes.sources.foreach { s =>
      fates(s.uid) match
        case Fate.Auto(p) if p.left.source.citekey != s.citekey =>
          in.labels.citekeys.get(s.citekey).orElse(in.labels.aliases.get(s.citekey)).filter(_ != p.left.uid).foreach(o =>
            info += s"source ${s.citekey}: merged into ${p.left.source.citekey}, its citekey stays with " +
              s"${sourcesByUid.get(o).fold(o.toString)(_.citekey)} here (not kept as an old citekey)")
        case _ => ()
    }
    val problems = (in.compat ++ in.readProblems ++ fatal.result()).distinct

    val sorted = issues.sortBy(i => (i.severity.ordinal, i.condition.isDefined, i.entity.ordinal, i.id))
    val hash = BundleFormat.sha256Hex((
      List(in.manifest.bundleId.toString) ++
        matches.map(m => s"${m.rightUid}>${m.leftUid.fold("")(_.toString)}:${MatchOutcome.wireOf(m.outcome)}") ++
        sorted.map(i => s"${i.id}=${i.decision.fold("")(_.wire)}") ++ problems
    ).mkString("\n")).take(8)
    val usedResolutions = chosen.keySet.intersect(liveIds).size

    val plan = MergePlan(in.manifest.bundleId, in.manifest.instance, in.here, in.manifest.formatVersion,
      in.manifest.schemaVersion, in.manifest.asOf, hash, counts, matches, sorted,
      info.result() ++ notes.result(), problems, errors.result().distinct, usedResolutions)
    val aliasOf = aliasIssues.result()
    Result(plan, localized, rows, needsApply.result().distinct,
      issues.filter(i => i.kind == IssueKind.AliasConflict && i.decision.exists(_.kind == km.KeepLeft))
        .flatMap(i => aliasOf.get(i.id)).toSet ++ droppedAliases.map(_.alias),
      notes.result())

  /** Both texts present and different (after trimming). */
  private def text(a: Option[String], b: Option[String]): Option[Unit] =
    a.map(_.trim).filter(_.nonEmpty).zip(b.map(_.trim).filter(_.nonEmpty)).filter((x, y) => x != y).map(_ => ())

  /** A new citekey for a bundle source whose own is taken: its key plus the next significant title
    * word (`smith2019_ritual` → `smith2019_ritualkingship`), else a letter (`smith2019_ritualb`).
    * Never one in use here or in the bundle. Pure. */
  def renameSuggestion(s: BundleSource, in: Input, fes: BundleEntities): String =
    val usedHere = in.labels.citekeys.keySet ++ in.labels.aliases.keySet ++ fes.sources.map(_.citekey) ++
      fes.aliases.map(_.alias)
    val suffix  = s.citekey.dropWhile(_ != '_').drop(1)
    val words   = MergeText.significant(MergeText.tokens(s.title)).map(_.filter(c => c.isLetterOrDigit && c < 128))
      .filter(w => w.nonEmpty && !suffix.contains(w))
    val byWord  = words.headOption.map(w => s.citekey + w).filterNot(usedHere)
    byWord.getOrElse(Iterator.from(0).map(i => s.citekey + ('b' + i).toChar).find(c => !usedHere(c)).get)

/** Citekey shape for a rename. */
object Citekeys:
  private val Shape = "^[a-z][a-z0-9_]*$".r
  def valid(ck: String): Boolean = Shape.matches(ck)
