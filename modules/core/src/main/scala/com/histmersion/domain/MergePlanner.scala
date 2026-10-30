package com.histmersion.domain

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.{
  ArticleLinks,
  AssetLinks,
  BundleAlias,
  BundleArticle,
  BundleAsset,
  BundleAssetLink,
  BundleAttestation,
  BundleEdge,
  BundleEvent,
  BundleFinding,
  BundleGoal,
  BundleGoalLink,
  BundleInstance,
  BundleManifest,
  BundlePeriod,
  BundlePlace,
  BundleQueueItem,
  BundleReference,
  BundleSource,
  IssueCondition,
  IssueDetail,
  IssueField,
  IssueHunk,
  IssuePlace,
  IssueSide,
  IssueText,
  MatchCounts,
  MergeDecision,
  MergeIssue,
  MergeIssueDetail,
  MergePlan,
  MergeResolutions,
  MergeTally,
  Plural,
  Queue,
  SourceMatch
}
import com.histmersion.files.*
import com.histmersion.api.given

import BundlePlan.{Decision, Ledger, Local}
import MergeFold.{ArticleOutcome, FindingMatch}
import MergeText.MinHash
import SourceMatcher.{Outcome, Pair, Side}
import TextAnchor.Anchor

import io.circe.{Decoder, Encoder, Json}
import java.util.UUID
import com.histmersion.values.{
  BundleKind,
  DecidedBy,
  DecisionKind,
  EditionsPolicy,
  IssueKind,
  IssueSeverity,
  MatchMethod,
  MatchOutcome,
  SourceStatus
}

/** `merge plan` as a pure function (docs/research-merge-import.md §5–§6, milestones M3–M5): of the bundle's entities,
  * what this collection knows (its rows, labels, ledger, redirects, tombstones, article histories and remembered
  * decisions), the OCR-body sketches and texts, and the resolutions. The database layer gathers the [[Input]]; nothing
  * here reads or writes anything.
  *
  * The stages never stop early — every issue of every stage is listed in one run:
  *
  *   1. **identity**: a bundle uid that a redirect (`uid_aliases`) or the ledger maps onto a row here is THAT row
  *      ([[mapUid]]); the bundle's lines are rewritten into this collection's uids and labels ([[localize]]) before
  *      anything is compared;
  *   2. **sources**: the rest are matched ([[SourceMatcher]]): auto-merge, needs-decision (a [[IssueKind.SameWork]]
  *      issue), or new — a new one whose citekey is taken here is a [[IssueKind.CitekeyCollision]];
  *   3. **fields** of every pair that is (or may be) merged: DOI, year, status, title, authors, summary, notes, period,
  *      OCR body. Under an open SameWork issue they are CONDITIONAL (`if S-… = map`), and the collision a `both` would
  *      cause is conditional too;
  *   4. **what a pair owns** (M4): findings matched inside the pair (the same claim folds, a near duplicate is a
  *      default `both`), assets by sha256, attestations re-anchored into the body the merge keeps — under each
  *      alternative of the body question, so nothing new appears after it is answered;
  *   5. **rows**: every line as M2 decides it (the ledger as common ancestor, [[BundlePlan]]); a row changed on both
  *      sides, or deleted there and changed here, is an issue — except where M4 can merge it: an article with a common
  *      base in the two histories (fast-forward, or a clean three-way merge), a goal, a queue item, an event whose
  *      dating and place agree; a label a different record has here is an issue, or a fold (goals, agreeing events, the
  *      same queue item), or a renumbering (a finding id, an asset name);
  *   6. what cannot be imported whatever is decided (a reference to nothing, a gazetteer place this collection lacks, a
  *      newer format) is a problem.
  *
  * Then the decisions in force turn into what `merge apply` writes ([[Result.localized]], the [[Row]]s and [[Apply]]):
  * every folded record becomes this collection's row, field by field, and the bundle's uid a redirect to it.
  *
  * Issue ids hash the issue's kind and the uids and revs it is about ([[issueId]]): they stay the same while those
  * records do not change, and a resolution whose id is no longer in the plan is reported, never re-applied.
  */
object MergePlanner:

  /** One row of `merge_ledger`: the foreign rev merged, the local uid it maps to, the local rev left. */
  final case class LedgerEntry(foreignRev: String, localUid: UUID, localRevAfter: Option[String])

  /** Who has which label here: citekeys and old citekeys (aliases) → source uid; finding ids, article slugs, event ids,
    * goal slugs → uid; (source uid, asset name) → asset uid.
    */
  final case class Labels(
      citekeys: Map[String, UUID] = Map.empty,
      aliases: Map[String, UUID] = Map.empty,
      findings: Map[String, UUID] = Map.empty,
      slugs: Map[String, UUID] = Map.empty,
      events: Map[String, UUID] = Map.empty,
      goals: Map[String, UUID] = Map.empty,
      assets: Map[(UUID, String), UUID] = Map.empty,
      /** Old article slugs here (`article_aliases`) → the article's uid. */
      articleAliases: Map[String, UUID] = Map.empty,
      /** Old event ids and goal slugs here (V053) → the record's uid. */
      eventAliases: Map[String, UUID] = Map.empty,
      goalAliases: Map[String, UUID] = Map.empty,
      /** Place ids here among the names the bundle uses (its places' ids and old ids) → uid, and the old place ids here
        * (V055) → the place's uid.
        */
      places: Map[String, UUID] = Map.empty,
      placeAliases: Map[String, UUID] = Map.empty
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
      /** This collection's rows — as bundle lines, so revs compare. The database gathers all of them (a merged pair
        * needs the survivor's findings, assets, attestations and edges).
        */
      local: BundleEntities,
      /** Every source here, for the matcher. */
      sources: List[BundleSource],
      labels: Labels,
      /** Uids alive here, among those the bundle's lines refer to. */
      live: Set[UUID],
      /** Gazetteer ids present here, among those the bundle's events name. */
      places: Set[String],
      reference: BundleReference,
      /** Old citekeys dropped here by renaming back (V050), with when — ISO-8601 UTC, as bundle timestamps are written,
        * so the two compare as strings.
        */
      aliasTombstones: Map[String, String] = Map.empty,
      /** OCR bodies (sha256 → text) of the pairs whose attestations must be re-anchored ([[bodiesWanted]]).
        */
      bodies: Map[String, String] = Map.empty,
      /** This collection's recorded versions of its articles (article uid → (sha256, body)), newest first: where a
        * common base for a three-way merge is looked for.
        */
      articleHistory: Map[UUID, List[(String, String)]] = Map.empty,
      /** Decisions an earlier `merge apply` carried out, by issue id (`merge_decisions`). */
      remembered: Map[String, MergeDecision] = Map.empty,
      /** `limits.chunk-chars`: to place a re-anchored attestation in its FTS chunk (0 leaves it unplaced). */
      chunkChars: Int = 0,
      /** A bundle place (by uid) whose id is not here, but whose label and geometry name a place here (the database
        * compares the geometries): that place's id.
        */
      placeMatches: Map[UUID, String] = Map.empty,
      /** Likewise for a named period: the same label and an overlapping span. */
      periodMatches: Map[UUID, String] = Map.empty
  ):
    def ownBundle: Boolean = manifest.instance.id == here.id

    /** How the other collection is named in what the merge writes ("work"). */
    def fromLabel: String =
      manifest.instance.label.map(_.trim).filter(_.nonEmpty).getOrElse(manifest.instance.id.toString.take(8))

  /** A line's fate, for the importer: its uid in the bundle and here, its revs, the decision. */
  final case class Row(
      kind: BundleKind,
      foreignUid: UUID,
      uid: UUID,
      label: String,
      foreignRev: String,
      localizedRev: String,
      localRev: Option[String],
      decision: Decision,
      tombstone: Boolean
  )

  /** A record of the bundle folded into one here: its uid becomes a redirect (`uid_aliases`). */
  final case class Fold(kind: BundleKind, foreignUid: UUID, localUid: UUID)

  /** One of THIS collection's attestations, moved because the merge keeps the bundle's body: its new place, or `None`
    * (kept without a place — `keep-unanchored`); `drop` deletes it.
    */
  final case class AttestationFix(uid: UUID, anchor: Option[Anchor], chunkOrd: Option[Int], drop: Boolean)

  /** A version to record for an article here: the bundle's text, so the next merge finds it in this collection's
    * history (a common base).
    */
  final case class ExtraVersion(articleUid: UUID, body: String, summary: Option[String], versionOf: String)

  /** What `merge apply` does besides writing the decided lines. */
  final case class Apply(
      folds: List[Fold] = Nil,
      /** Sources here given another citekey first (`rename-left`): uid, old, new. */
      renames: List[(UUID, String, String)] = Nil,
      /** Articles here whose current text is recorded as a version before the merge replaces it. */
      snapshots: Set[UUID] = Set.empty,
      versions: List[ExtraVersion] = Nil,
      /** Articles added beside one here (`both` on an article changed on both sides): a new record. */
      copies: List[BundleArticle] = Nil,
      attestations: List[AttestationFix] = Nil,
      /** Bundle records left out for good (an attestation dropped): recorded as deleted here, so the next plan does not
        * bring them back. (uid, kind, origin.)
        */
      tombstones: List[(UUID, BundleKind, UUID)] = Nil,
      /** Asset references to rewrite in a body the merge takes from the bundle: source uid → (old name, new name), for
        * its figures renamed because the name was taken.
        */
      bodyRenames: Map[UUID, List[(String, String)]] = Map.empty,
      /** Articles (their slugs as they will be) to queue for `/review-article`. */
      reviews: List[String] = Nil,
      /** The decisions to remember for the next plan (an issue id → its decision). */
      remember: List[(String, MergeDecision)] = Nil,
      /** The rows here whose new content the merge made (a fold, a three-way merge): their change clock moves to the
        * moment of the apply, so the next export carries them.
        */
      touch: Set[UUID] = Set.empty,
      tally: MergeTally = MergeTally()
  )

  final case class Result(
      plan: MergePlan,
      /** The bundle in this collection's uids and labels, folded records already merged. */
      localized: BundleEntities,
      rows: List[Row],
      /** What an import cannot carry out (it needs `merge apply`), one line each. */
      needsApply: List[String],
      /** Old citekeys of the bundle not to import (a resolved alias conflict, or one dropped here). */
      skipAliases: Set[String],
      /** Optional links the import leaves out, and other notes for the import report. */
      notes: List[String],
      apply: Apply = Apply()
  )

  // ── identity ────────────────────────────────────────────────────────────────────────────────

  /** The local uid of a bundle uid: a redirect (followed to its end), else the ledger's local uid, else itself.
    */
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
    j.fold(
      j,
      _ => j,
      _ => j,
      s => m.get(s).fold(j)(Json.fromString),
      arr => Json.fromValues(arr.map(mapJson(m))),
      obj => Json.fromJsonObject(obj.mapValues(mapJson(m)))
    )

  /** The bundle in this collection's terms: every uid a redirect or the ledger maps (an own uid or a reference)
    * replaced by the local one, and a mapped row's label by its local label — after a merge the local citekey (slug,
    * id) wins. Lines with no mapping are left as they are.
    */
  def localize(es: BundleEntities, mapped: Map[UUID, UUID], localLabel: Map[UUID, String]): BundleEntities =
    if mapped.isEmpty then es
    else
      val m = mapped.map((f, l) => f.toString -> l.toString)
      def go[A](as: List[A], labelField: Option[String])(using enc: Encoder.AsObject[A], dec: Decoder[A]): List[A] =
        as.map { a =>
          val obj = enc.encodeObject(a)
          val own = obj("uid").flatMap(_.asString).flatMap(u => scala.util.Try(UUID.fromString(u)).toOption)
          val relabelled = (for
            f <- own
            l <- mapped.get(f)
            field <- labelField
            lab <- localLabel.get(l)
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
        tombstones = go(es.tombstones, None),
        places = go(es.places, Some("id")),
        periods = go(es.periods, Some("id")),
        articleAliases = go(es.articleAliases, None),
        eventAliases = go(es.eventAliases, None),
        goalAliases = go(es.goalAliases, None),
        placeAliases = go(es.placeAliases, None),
        eventCatalogue = go(es.eventCatalogue, None),
        eventCandidates = go(es.eventCandidates, None),
        eventChecks = go(es.eventChecks, None),
        corrections = go(es.corrections, None)
      )

  // ── issue ids ───────────────────────────────────────────────────────────────────────────────

  /** The letter of an issue id: the entity it is about (O for an OCR body, L for an alias). */
  def letter(kind: IssueKind, entity: BundleKind): String = kind match
    case IssueKind.BodyDiffers   => "O"
    case IssueKind.AliasConflict => "L"
    case _                       =>
      entity match
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
        case BundleKind.Place          => "P"
        case BundleKind.Period         => "N"

  /** `<letter>-<8 hex>`: sha256 of the kind, the sorted bundle uids, the sorted local uids, the two revs and anything
    * else that tells the issue apart (an alias). The same records unchanged → the same id; either side changed → a new
    * id, and the old resolution goes stale. Pure.
    */
  def issueId(
      kind: IssueKind,
      entity: BundleKind,
      right: List[UUID],
      left: List[UUID],
      rightRev: String,
      leftRev: String,
      extra: String = ""
  ): String =
    val text = List(
      IssueKind.wireOf(kind),
      right.map(_.toString).sorted.mkString(","),
      left.map(_.toString).sorted.mkString(","),
      rightRev,
      leftRev,
      extra
    ).mkString("\u0000")
    s"${letter(kind, entity)}-${BundleFormat.sha256Hex(text).take(8)}"

  /** Two versions of an article, with the three-way merge's regions over their common version (or the whole text as one
    * region when there is none).
    */
  private[domain] def articleText(base: Option[String], left: String, right: String): IssueText =
    val hunks = base match
      case Some(b) =>
        Diff3.merge(b, left, right).hunks.map {
          case Diff3.Hunk.Agreed(ls)        => IssueHunk(conflict = false, left = ls.toList)
          case Diff3.Hunk.Conflict(o, a, c) => IssueHunk(conflict = true, o.toList, a.toList, c.toList)
        }
      case None => List(IssueHunk(conflict = true, Nil, left.split("\n", -1).toList, right.split("\n", -1).toList))
    IssueText(base, left, right, hunks)

  /** Events and places point at places by id, places and periods at their parents by id: a bundle place (period)
    * matched to one here under another id is pointed at under that id. Pure.
    */
  def repoint(es: BundleEntities, places: Map[String, String], periods: Map[String, String]): BundleEntities =
    if places.isEmpty && periods.isEmpty then es
    else
      es.copy(
        events = es.events.map(e => e.placeId.flatMap(places.get).fold(e)(p => e.copy(placeId = Some(p)))),
        places = es.places.map(p =>
          p.copy(relations = p.relations.map(r => places.get(r.parentId).fold(r)(x => r.copy(parentId = x))))
        ),
        periods = es.periods.map(p => p.parentId.flatMap(periods.get).fold(p)(x => p.copy(parentId = Some(x))))
      )

  /** The bundle's places that are one here under another id — the other collection renamed them (the same uid, a new
    * id): each with our id, and whether the new id is a DIFFERENT place's here (then the rename cannot be followed). A
    * place a redirect maps (`mapped`) is localized already. Pure.
    */
  def renamedPlaces(
      bundle: List[BundlePlace],
      here: List[BundlePlace],
      mapped: Set[UUID]
  ): List[(BundlePlace, String, Boolean)] =
    val ourId = here.map(p => p.uid -> p.id).toMap
    val owner = here.map(p => p.id -> p.uid).toMap
    bundle
      .filterNot(p => mapped(p.uid))
      .flatMap(p =>
        ourId.get(p.uid).filter(_ != p.id).map { ours =>
          (p, ours, owner.get(p.id).exists(_ != p.uid))
        }
      )

  /** A goal's links name what they link by label; the label of a linked source or article is the one it has here once
    * the merge is done (a merged source's survivor citekey, a renamed article).
    */
  private def relinkGoals(es: BundleEntities, in: Input): BundleEntities =
    val citekeys = in.sources.map(s => s.uid -> s.citekey).toMap ++ es.sources.map(s => s.uid -> s.citekey)
    val slugs = in.local.articles.map(a => a.uid -> a.slug).toMap ++ es.articles.map(a => a.uid -> a.slug)
    def label(k: BundleGoalLink): Option[String] = k.kind match
      case "source"  => k.refUid.flatMap(citekeys.get)
      case "article" => k.refUid.flatMap(slugs.get)
      case _         => None
    es.copy(goals = es.goals.map { g =>
      val links = g.links.map(_.map(k => label(k).filter(_ != k.ref).fold(k)(r => k.copy(ref = r))))
      if links == g.links then g else g.copy(links = links, rev = "")
    })

  // ── sketches and bodies ─────────────────────────────────────────────────────────────────────

  /** The OCR bodies (by sha256) whose MinHash could decide a pair: those of every blocked pair whose bodies differ —
    * the database computes them before [[plan]].
    */
  def sketchesWanted(in: Input, cfg: MergeSettings): Set[String] =
    val (rights, lefts) = matchSides(in)
    val byKey = lefts.flatMap(l => SourceMatcher.blockingKeys(l).map(_ -> l)).groupMap(_._1)(_._2)
    rights.flatMap { r =>
      SourceMatcher.blockingKeys(r).toList.flatMap(k => byKey.getOrElse(k, Nil)).distinctBy(_.uid).flatMap { l =>
        (r.source.bodySha256, l.source.bodySha256) match
          case (Some(a), Some(b)) if a != b => List(a, b)
          case _                            => Nil
      }
    }.toSet

  /** An attestation that points into the text (a quote, or offsets): it must move with a body. */
  private def anchored(t: BundleAttestation): Boolean = t.quote.exists(_.nonEmpty) || t.charStart.isDefined

  /** The OCR bodies (by sha256) the plan must read to re-anchor attestations: those of every matched pair whose bodies
    * differ and whose sources carry attestations.
    */
  def bodiesWanted(
      in: Input,
      sketches: Map[String, MinHash],
      resolutions: MergeResolutions,
      cfg: MergeSettings
  ): Set[String] =
    val (rights, lefts) = matchSides(in)
    val pairs = SourceMatcher.assign(rights, lefts, sketches, cfg, resolutions.policy.editions).pairs
    val withAtts = (in.foreign.attestations ++ in.local.attestations).filter(anchored).map(_.sourceUid).toSet
    pairs.flatMap { p =>
      val (a, b) = (p.right.source.bodySha256, p.left.source.bodySha256)
      if a != b && (withAtts(p.right.uid) || withAtts(p.left.uid)) then a.toList ++ b.toList else Nil
    }.toSet

  /** The bundle's sources that need matching (no identity here), and the sources here they may match (not already some
    * bundle source's own, not deleted).
    */
  private def matchSides(in: Input): (List[Side], List[Side]) =
    val idOf = mapUid(in.uidAliases, in.ledger)
    val localUids = in.local.sources.map(_.uid).toSet ++ in.sources.map(_.uid)
    val unknown = in.foreign.sources.filter { s =>
      val l = idOf(s.uid)
      l == s.uid && !localUids(l) && !in.tombstoned(l)
    }
    val claimed = in.foreign.sources.map(s => idOf(s.uid)).toSet
    val foreignAliases = in.foreign.aliases.groupMap(_.sourceUid)(_.alias)
    val localAliases = in.labels.aliases.toList.groupMap(_._2)(_._1)
    val rights = unknown.map(s => Side(s, foreignAliases.getOrElse(s.uid, Nil).toSet))
    val lefts = in.sources
      .filterNot(s => claimed(s.uid) || in.tombstoned(s.uid))
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
  final private case class Draft(issue: MergeIssue, importable: Set[DecisionKind])

  /** Whether an issue applies, given the decisions of the issues it is conditional on. */
  private enum Standing:
    case Live, Conditional, Dropped

  /** An attestation of a pair tried against the body one alternative keeps. */
  final private case class AnchorTry(
      att: BundleAttestation,
      foreign: Boolean,
      anchor: Option[Anchor],
      chunkOrd: Option[Int],
      issue: Option[String]
  )

  /** What M4 merges by itself, decided before the resolutions. */
  private enum Merges:
    /** This collection's article already contains the bundle's text: ours stays. */
    case ArticleKeep

    /** The bundle's article continues ours: fast-forward. */
    case ArticleTheirs

    /** Both changed since a common version, merged three-way without a conflict. */
    case ArticleMerged(body: String)
    case GoalFold, QueueFold, EventFold

    /** Two events under different ids that are one ([[MergeFold.sameEventAcrossIds]]). */
    case EventAcross

    /** A place (a named period) of the bundle that is one here: the same id and where/when, or the same label and
      * geometry (span) under another id.
      */
    case PlaceFold, PeriodFold

  /** A label for an event id. */
  private val SlugShape = "^[a-z0-9][a-z0-9_.-]*$".r

  def plan(in: Input, sketches: Map[String, MinHash], resolutions: MergeResolutions, cfg: MergeSettings): Result =
    val idOf = mapUid(in.uidAliases, in.ledger)
    val fes = in.foreign
    val rawRevs = fes.revs
    // the bundle's own uids, and those it only refers to (an incremental bundle names records it does
    // not carry, some of them merged away here since)
    val mapped = (rawRevs.keySet ++ fes.tombstones.map(_.uid) ++ fes.references.map(_.uid)).toList
      .map(f => f -> idOf(f))
      .filter((f, l) => f != l)
      .toMap
    val localRevs = in.local.revs
    // a place the bundle renamed (its uid is here under another id, #888): the import follows the
    // rename — unless the new id is a different place's here; then ours keeps its id, and what the
    // bundle points at the new id points at ours
    val renamed = renamedPlaces(fes.places, in.local.places, mapped.keySet)
    val renameKept: Map[UUID, String] = renamed.collect { case (p, ours, true) => p.uid -> ours }.toMap
    // a bundle place (named period) matched to one here under another id: what points at it by id follows
    val placeIdMap: Map[String, String] =
      fes.places.flatMap(p => in.placeMatches.get(p.uid).filter(_ != p.id).map(p.id -> _)).toMap ++
        renamed.collect { case (p, ours, true) => p.id -> ours }
    val periodIdMap: Map[String, String] =
      fes.periods.flatMap(p => in.periodMatches.get(p.uid).filter(_ != p.id).map(p.id -> _)).toMap
    val localized0 = repoint(localize(fes, mapped, localRevs.view.mapValues(_.label).toMap), placeIdMap, periodIdMap)
    val localized =
      if renameKept.isEmpty then localized0
      else
        localized0.copy(places = localized0.places.map(p => renameKept.get(p.uid).fold(p)(ours => p.copy(id = ours))))
    val locRevs = localized.revs
    val toLocal: UUID => UUID = u => mapped.getOrElse(u, u)
    val own = in.ownBundle
    val policy = resolutions.policy
    val from = in.fromLabel
    def liveHere(u: UUID): Boolean = in.live(u) || (localRevs.contains(u) && !in.tombstoned(u))

    // ── 2. sources ──
    val (rights, lefts) = matchSides(in)
    val matching = SourceMatcher.assign(rights, lefts, sketches, cfg, policy.editions)
    val sourcesByUid = in.sources.map(s => s.uid -> s).toMap
    def leftRev(u: UUID): String = sourcesByUid.get(u).map(s => BundleFormat.stamp(s).rev).getOrElse("")
    def rightRev(u: UUID): String = rawRevs.get(u).map(_.rev).getOrElse("")
    def localRev(u: UUID): String = localRevs.get(u).map(_.rev).getOrElse("")

    val gateIds: Map[UUID, String] = matching.pairs
      .filter(_.outcome == Outcome.NeedsDecision)
      .map(p =>
        p.right.uid -> issueId(
          IssueKind.SameWork,
          BundleKind.Source,
          List(p.right.uid),
          List(p.left.uid),
          rightRev(p.right.uid),
          leftRev(p.left.uid)
        )
      )
      .toMap
    val fates: Map[UUID, Fate] = fes.sources.map { s =>
      val l = idOf(s.uid)
      s.uid -> (if in.tombstoned(l) && !localRevs.contains(l) then Fate.Deleted
                else if localRevs.contains(l) then Fate.Same(l, l != s.uid)
                else
                  matching.pairs.find(_.right.uid == s.uid) match
                    case Some(p) if p.outcome == Outcome.AutoMerge => Fate.Auto(p)
                    case Some(p)                                   => Fate.Gate(p, gateIds(s.uid))
                    case None                                      => Fate.New)
    }.toMap
    val pairs: List[(Pair, Option[IssueCondition])] = fes.sources.flatMap(s =>
      fates(s.uid) match
        case Fate.Auto(p)    => Some(p -> None)
        case Fate.Gate(p, g) => Some(p -> Some(IssueCondition(g, DecisionKind.Map)))
        case _               => None
    )

    // ── the drafts: every issue, conditional ones included ──
    val drafts = List.newBuilder[Draft]
    def add(d: Draft): Unit = drafts += d
    val km = DecisionKind
    val lr = List(km.KeepLeft, km.KeepRight)
    val info = List.newBuilder[String]
    renamed.foreach { (p, ours, kept) =>
      info += (if kept then
                 s"place $ours: renamed ${p.id} in the bundle, but here ${p.id} is another place — it keeps its id"
               else s"place $ours: renamed ${p.id} in the bundle — renamed here too (the old id stays an alias)")
    }

    // the gates
    matching.pairs.filter(_.outcome == Outcome.NeedsDecision).foreach { p =>
      val ev = p.evidence
      val title =
        if p.flags.nonEmpty then s"the same work? ${p.flags.mkString("; ")} (score ${SourceMatcher.signed(ev.score)})"
        else s"possibly the same work (score ${SourceMatcher.signed(ev.score)})"
      add(
        Draft(
          MergeIssue(
            gateIds(p.right.uid),
            IssueKind.SameWork,
            IssueSeverity.Blocking,
            BundleKind.Source,
            title,
            Some(MergeIssueDetail.side(p.left.source)),
            Some(MergeIssueDetail.side(p.right.source)),
            ev.why ++ p.alsoLike.take(3).map((ck, sc) => s"also resembles $ck (score ${SourceMatcher.signed(sc)})"),
            List(km.Map, km.Both),
            Option.when(ev.score >= cfg.autoMerge)(MergeDecision.withArg(km.Map, p.left.source.citekey)),
            detail = Some(IssueDetail(MergeIssueDetail.sourceFields(p.left.source, p.right.source)))
          ),
          Set(km.Both)
        )
      )
    }

    // the fields of a pair that is (or may be) merged; their ids by pair, for the fold
    val pairFieldIds = scala.collection.mutable.Map.empty[UUID, Map[IssueKind, String]]
    def fieldIssues(p: Pair, cond: Option[IssueCondition]): List[Draft] =
      val (r, l) = (p.right.source, p.left.source)
      val (fu, lu) = (List(r.uid), List(l.uid))
      val (rr, ll) = (rightRev(r.uid), leftRev(l.uid))
      def mk(
          kind: IssueKind,
          title: String,
          opts: List[DecisionKind],
          suggested: Option[MergeDecision] = None,
          sev: IssueSeverity = IssueSeverity.Blocking,
          why: List[String] = Nil
      ): Draft =
        Draft(
          MergeIssue(
            issueId(kind, BundleKind.Source, fu, lu, rr, ll),
            kind,
            sev,
            BundleKind.Source,
            title,
            Some(MergeIssueDetail.side(l)),
            Some(MergeIssueDetail.side(r)),
            why,
            opts,
            suggested,
            cond,
            detail = Some(IssueDetail(MergeIssueDetail.sourceFields(l, r)))
          ),
          Set.empty
        )
      val doiR = r.doi.flatMap(MergeText.doiKey)
      val doiL = l.doi.flatMap(MergeText.doiKey)
      val sameDoi = doiR.isDefined && doiR == doiL
      val ds = List(
        Option.when(doiR.isDefined && doiL.isDefined && doiR != doiL)(
          mk(
            IssueKind.DoiDiffers,
            s"DOI differs: ${l.doi.get} here, ${r.doi.get} in the bundle",
            lr,
            why = p.evidence.strong.map(k => s"matched by $k: one record has the wrong DOI")
          )
        ),
        r.year.zip(l.year).filter((a, b) => a != b && !(math.abs(a - b) == 1 && sameDoi)).map { (a, b) =>
          val earliest = if b <= a then km.KeepLeft else km.KeepRight
          mk(
            IssueKind.YearDiffers,
            s"year differs: $b here, $a in the bundle",
            lr,
            Some(MergeDecision.of(earliest)),
            why = List("the suggestion keeps the earliest year (the first edition)")
          )
        },
        Option.when(r.status != l.status && r.status != SourceStatus.Unassessed && l.status != SourceStatus.Unassessed)(
          mk(IssueKind.StatusDiffers, s"status differs: ${l.status.wire} here, ${r.status.wire} in the bundle", lr)
        ),
        Option.when(p.evidence.titleDiffers)(
          mk(IssueKind.TitleDiffers, "title differs", lr, why = List(s"here: ${l.title}", s"bundle: ${r.title}"))
        ),
        Option.when(p.evidence.authors == MergeText.AuthorAgreement.Disjoint)(
          mk(
            IssueKind.AuthorsDiffers,
            "no author in common",
            lr,
            why = List(s"here: ${l.authors.mkString("; ")}", s"bundle: ${r.authors.mkString("; ")}")
          )
        ),
        text(r.summary, l.summary).map(_ =>
          mk(IssueKind.SummaryDiffers, "summary differs", List(km.KeepLeft, km.KeepRight, km.Concat))
        ),
        text(r.notes, l.notes).map(_ =>
          mk(IssueKind.NotesDiffers, "notes differ", List(km.KeepLeft, km.KeepRight, km.Concat))
        ),
        r.period
          .zip(l.period)
          .filter((a, b) => a != b)
          .map((a, b) =>
            mk(
              IssueKind.PeriodDiffers,
              s"period differs: ${MergeIssueDetail.rangeText(Some(b))} here, ${MergeIssueDetail.rangeText(Some(a))} in the bundle",
              List(km.KeepLeft, km.KeepRight, km.Hull),
              Some(MergeDecision.of(km.Hull))
            )
          ),
        r.bodySha256.zip(l.bodySha256).filter((a, b) => a != b).map { (a, b) =>
          val sim = sketches
            .get(a)
            .zip(sketches.get(b))
            .map((x, y) => s" (MinHash ${SourceMatcher.fixed(x.similarity(y), 2)})")
            .getOrElse("")
          mk(
            IssueKind.BodyDiffers,
            s"OCR bodies differ$sim",
            lr,
            Some(MergeDecision.of(km.KeepLeft)),
            IssueSeverity.Default,
            List("by default this collection's body stays; keep-right takes the bundle's (and its figures)")
          )
        }
      ).flatten
      pairFieldIds(r.uid) = ds.map(d => d.issue.kind -> d.issue.id).toMap
      ds

    // a bundle source that would be NEW: is its citekey taken here?
    def collision(s: BundleSource, cond: Option[IssueCondition]): Option[Draft] =
      val owner = in.labels.citekeys
        .get(s.citekey)
        .orElse(in.labels.aliases.get(s.citekey))
        .filterNot(in.tombstoned)
      owner.flatMap(sourcesByUid.get).map { o =>
        val asAlias = !in.labels.citekeys.contains(s.citekey)
        val score = lefts
          .find(_.uid == o.uid)
          .map(l => SourceMatcher.evidence(Side(s, Set.empty), l, sketches, cfg.weights).score)
        val title =
          if asAlias then s"citekey ${s.citekey} is an old citekey (alias) of another source here"
          else s"same citekey, different works" + score.fold("")(sc => s" (score ${SourceMatcher.signed(sc)})")
        val suggestion = renameSuggestion(s, in, fes)
        Draft(
          MergeIssue(
            issueId(
              IssueKind.CitekeyCollision,
              BundleKind.Source,
              List(s.uid),
              List(o.uid),
              rightRev(s.uid),
              leftRev(o.uid)
            ),
            IssueKind.CitekeyCollision,
            IssueSeverity.Blocking,
            BundleKind.Source,
            title,
            Some(MergeIssueDetail.side(o)),
            Some(MergeIssueDetail.side(s)),
            if asAlias then List("rename-left cannot free an old citekey: the bundle's source takes a new one")
            else Nil,
            if asAlias then List(km.RenameRight) else List(km.RenameRight, km.RenameLeft),
            Some(MergeDecision.withArg(km.RenameRight, suggestion)),
            cond,
            detail = Some(IssueDetail(MergeIssueDetail.sourceFields(o, s)))
          ),
          Set.empty
        )
      }

    fes.sources.foreach { s =>
      fates(s.uid) match
        case Fate.Auto(p)    => fieldIssues(p, None).foreach(add)
        case Fate.Gate(p, g) =>
          fieldIssues(p, Some(IssueCondition(g, km.Map))).foreach(add)
          collision(s, Some(IssueCondition(g, km.Both))).foreach(add)
        case Fate.New => collision(s, None).foreach(add)
        case _        => ()
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
            val id = issueId(IssueKind.AliasConflict, BundleKind.Source, List(a.sourceUid), List(o), "", "", a.alias)
            aliasIssues += id -> a.alias
            Draft(
              MergeIssue(
                id,
                IssueKind.AliasConflict,
                IssueSeverity.Blocking,
                BundleKind.Source,
                s"old citekey ${a.alias} names ${src.fold(a.sourceUid.toString)(_.citekey)} in the bundle, " +
                  s"but ${sourcesByUid.get(o).fold(o.toString)(_.citekey)} here" + (if asCitekey then " (its citekey)"
                                                                                    else ""),
                sourcesByUid.get(o).map(MergeIssueDetail.side),
                src.map(MergeIssueDetail.side),
                List("keep-left drops the bundle's old citekey; this collection's meaning stays"),
                if asCitekey then List(km.KeepLeft) else lr,
                Some(MergeDecision.of(km.KeepLeft)),
                cond,
                detail = sourcesByUid.get(o).zip(src).map((x, y) => IssueDetail(MergeIssueDetail.sourceFields(x, y)))
              ),
              Set(km.KeepLeft)
            )
          }
        fates.get(a.sourceUid) match
          case Some(Fate.Same(l, _)) => conflict(l, None).foreach(add)
          case Some(Fate.Auto(p))    => conflict(p.left.uid, None).foreach(add)
          case Some(Fate.Gate(p, g)) =>
            (
              conflict(p.left.uid, Some(IssueCondition(g, km.Map))),
              conflict(a.sourceUid, Some(IssueCondition(g, km.Both)))
            ) match
              case (Some(x), Some(_)) => add(x.modify(_.issue.condition).setTo(None))
              case (x, y)             => (x ++ y).foreach(add)
          case Some(Fate.New) | None => conflict(a.sourceUid, None).foreach(add)
          case Some(Fate.Deleted)    => ()
      }
    }

    // ── 4. what a pair owns (M4) ──
    // rows of the bundle that already have their own uid here are not matched again inside a pair
    val foreignFindingsOf = fes.findings.filterNot(f => localRevs.contains(toLocal(f.uid))).groupBy(_.sourceUid)
    val localFindingsOf = in.local.findings.groupBy(_.sourceUid)
    val foreignAttsOf = fes.attestations.filterNot(t => localRevs.contains(toLocal(t.uid))).groupBy(_.sourceUid)
    val localAttsOf = in.local.attestations.groupBy(_.sourceUid)
    val nearIssue = scala.collection.mutable.Map.empty[UUID, String] // bundle finding → issue
    val findingMatches = scala.collection.mutable.Map.empty[UUID, List[FindingMatch]] // bundle source → matches
    // per pair and per alternative of the body question (the body issue's decision, or None when
    // there is no question): the body sha the merge keeps and the attestations tried against it
    val anchors = scala.collection.mutable.Map.empty[UUID, Map[Option[DecisionKind], List[AnchorTry]]]

    pairs.foreach { (p, cond) =>
      val (r, l) = (p.right.source, p.left.source)
      // findings
      val ms = MergeFold.matchFindings(
        localFindingsOf.getOrElse(l.uid, Nil),
        foreignFindingsOf.getOrElse(r.uid, Nil),
        cfg.findingWithinKm,
        cfg.findingNearAtLeast
      )
      findingMatches(r.uid) = ms
      ms.foreach {
        case FindingMatch.Near(rf, lf, j) =>
          val id = issueId(
            IssueKind.FindingNearDuplicate,
            BundleKind.Finding,
            List(rf.uid),
            List(lf.uid),
            rightRev(rf.uid),
            localRev(lf.uid)
          )
          nearIssue(rf.uid) = id
          add(
            Draft(
              MergeIssue(
                id,
                IssueKind.FindingNearDuplicate,
                IssueSeverity.Default,
                BundleKind.Finding,
                s"near-duplicate findings (claim words alike, Jaccard ${SourceMatcher.fixed(j, 2)})",
                Some(IssueSide(lf.id, lf.uid, List(MergeIssueDetail.preview(lf.claim)))),
                Some(IssueSide(rf.id, rf.uid, List(MergeIssueDetail.preview(rf.claim)))),
                List("by default both are kept; merge makes them one finding"),
                List(km.Both, km.Merge),
                Some(MergeDecision.of(km.Both)),
                cond,
                detail =
                  Some(
                    IssueDetail(
                      List(
                        IssueField("claim", Some(lf.claim), Some(rf.claim)),
                        IssueField(
                          "period",
                          lf.period.map(x => MergeIssueDetail.rangeText(Some(x))),
                          rf.period.map(x => MergeIssueDetail.rangeText(Some(x)))
                        ),
                        IssueField("region", lf.region, rf.region),
                        IssueField(
                          "confidence",
                          lf.confidence.map(_.toString.toLowerCase),
                          rf.confidence.map(_.toString.toLowerCase)
                        )
                      )
                    )
                  )
              ),
              Set.empty
            )
          )
        case FindingMatch.Same(rf, lf)
            if rf.confidence.isDefined && lf.confidence.isDefined && rf.confidence != lf.confidence =>
          info += s"finding ${lf.id}: the same claim as the bundle's ${rf.id} at another confidence — the more cautious is kept"
        case _ => ()
      }
      // attestations, under each alternative of the body question
      val bodyIssue = pairFieldIds.get(r.uid).flatMap(_.get(IssueKind.BodyDiffers))
      val alternatives: List[(Option[DecisionKind], Option[String], Option[IssueCondition])] = bodyIssue match
        case Some(b) =>
          List(
            (Some(km.KeepLeft), l.bodySha256, Some(IssueCondition(b, km.KeepLeft))),
            (Some(km.KeepRight), r.bodySha256, Some(IssueCondition(b, km.KeepRight)))
          )
        case None => List((None, l.bodySha256.orElse(r.bodySha256), cond))
      val atts = foreignAttsOf.getOrElse(r.uid, Nil).map(_ -> true) ++ localAttsOf.getOrElse(l.uid, Nil).map(_ -> false)
      anchors(r.uid) = alternatives.map { (alt, kept, c) =>
        val tries = atts.sortBy((t, _) => t.uid.toString).flatMap { (t, foreign) =>
          val ownBody = if foreign then r.bodySha256 else l.bodySha256
          if kept.isEmpty || ownBody == kept || !anchored(t) then None
          else
            val body = kept.flatMap(in.bodies.get)
            val found = for
              txt <- body
              q <- t.quote.filter(_.nonEmpty)
              a <- TextAnchor.reanchor(txt, q, t.prefix, t.suffix, t.charStart)
            yield a
            found match
              case Some(a) =>
                Some(AnchorTry(t, foreign, Some(a), body.flatMap(TextAnchor.chunkOf(_, a, in.chunkChars)), None))
              case None =>
                val tag = alt.fold("")(DecisionKind.wireOf)
                val id = issueId(
                  IssueKind.AttestationUnanchored,
                  BundleKind.Attestation,
                  List(t.uid),
                  List(l.uid),
                  if foreign then rightRev(t.uid) else localRev(t.uid),
                  kept.getOrElse(""),
                  tag
                )
                val att = IssueSide(
                  s"${t.targetType} ${t.targetId}",
                  t.uid,
                  t.quote.map(q => s"«${MergeIssueDetail.preview(q)}»").toList
                )
                val whose = if foreign then "the bundle's" else "this collection's"
                add(
                  Draft(
                    MergeIssue(
                      id,
                      IssueKind.AttestationUnanchored,
                      IssueSeverity.Blocking,
                      BundleKind.Attestation,
                      s"$whose attestation on ${l.citekey}: its quote is not in the body the merge keeps" +
                        alt.fold("")(k => s" (if the body is ${DecisionKind.wireOf(k)})"),
                      if foreign then Some(MergeIssueDetail.side(l)) else Some(att),
                      if foreign then Some(att) else Some(MergeIssueDetail.side(r)),
                      List(if t.quote.exists(_.nonEmpty) then
                        "the quote (with its prefix and suffix) was looked for, exactly and with spacing ignored"
                      else "it has a position but no quote, so it cannot be found again"),
                      List(km.Drop, km.KeepUnanchored),
                      None,
                      c,
                      detail = Some(
                        IssueDetail(
                          List(
                            IssueField("quote", if foreign then None else t.quote, if foreign then t.quote else None),
                            IssueField(
                              "before it",
                              if foreign then None else t.prefix,
                              if foreign then t.prefix else None
                            ),
                            IssueField(
                              "after it",
                              if foreign then None else t.suffix,
                              if foreign then t.suffix else None
                            )
                          )
                        )
                      )
                    ),
                    Set.empty
                  )
                )
                Some(AnchorTry(t, foreign, None, None, Some(id)))
        }
        alt -> tries
      }.toMap
    }

    // ── 5. rows ──
    val ledgerOf: UUID => Option[Ledger] = f =>
      if own then None else in.ledger.get(f).map(e => Ledger(e.foreignRev, e.localRevAfter))
    def localOf(f: UUID): Local =
      val l = toLocal(f)
      Local(localRevs.get(l).map(_.rev), in.tombstoned(l), ledgerOf(f))
    // the source a line belongs to (its fate decides whether the line is held, or folded with it)
    val assetSource = fes.assets.map(a => a.uid -> a.sourceUid).toMap
    val findingSource = fes.findings.map(f => f.uid -> f.sourceUid).toMap
    val linkAsset = fes.assetLinks.map(l => l.uid -> l.assetUid).toMap
    val attSource = fes.attestations.map(t => t.uid -> t.sourceUid).toMap
    def ownerSource(f: UUID, kind: BundleKind): Option[UUID] = kind match
      case BundleKind.Source      => Some(f)
      case BundleKind.Finding     => findingSource.get(f)
      case BundleKind.Asset       => assetSource.get(f)
      case BundleKind.AssetLink   => linkAsset.get(f).flatMap(assetSource.get)
      case BundleKind.Attestation => attSource.get(f)
      case _                      => None
    def heldBy(f: UUID, kind: BundleKind): Boolean =
      ownerSource(f, kind).flatMap(fates.get).exists {
        case Fate.Auto(_) | Fate.Gate(_, _) => true
        case _                              => false
      }

    val initialRows: List[Row] =
      rawRevs.toList.sortBy((u, r) => (r.kind.ordinal, u.toString)).map { (f, r) =>
        val l = toLocal(f)
        val loc = locRevs.get(l).map(_.rev).getOrElse(r.rev)
        val lab = locRevs.get(l).map(_.label).getOrElse(r.label)
        val d =
          if heldBy(f, r.kind) && !localRevs.contains(l) then Decision.Held
          else BundlePlan.entity(r.rev, loc, localOf(f), own)
        Row(r.kind, f, l, lab, r.rev, loc, localRevs.get(l).map(_.rev), d, tombstone = false)
      } ++ fes.tombstones.map { t =>
        val l = toLocal(t.uid)
        Row(
          t.kind,
          t.uid,
          l,
          localRevs.get(l).map(_.label).getOrElse(t.uid.toString),
          BundlePlan.DeletedRev,
          BundlePlan.DeletedRev,
          localRevs.get(l).map(_.rev),
          BundlePlan.tombstone(localOf(t.uid), own),
          tombstone = true
        )
      }
    val initialOf: Map[UUID, Row] = initialRows.filterNot(_.tombstone).map(r => r.foreignUid -> r).toMap

    // records here, by uid; the bundle's (localized) events by uid; the bundle's raw articles
    val localArticle = in.local.articles.map(a => a.uid -> a).toMap
    val localGoal = in.local.goals.map(g => g.uid -> g).toMap
    val localEvent = in.local.events.map(e => e.uid -> e).toMap
    val localQueue = in.local.queue.map(q => q.uid -> q).toMap
    val localPlace = in.local.places.map(p => p.uid -> p).toMap
    val localPeriod = in.local.periods.map(p => p.uid -> p).toMap
    val bundleEvent = localized.events.map(e => e.uid -> e).toMap
    val rawArticle = fes.articles.map(a => a.uid -> a).toMap
    def foreignHistory(f: UUID): Set[String] = fes.versions.filter(_.articleUid == f).map(_.bodySha256).toSet
    def outcome(local: BundleArticle, foreignUid: UUID): ArticleOutcome =
      MergeFold.articleOutcome(
        local.body,
        in.articleHistory.getOrElse(local.uid, Nil),
        rawArticle(foreignUid).body,
        foreignHistory(foreignUid)
      )
    def baseOf(local: UUID, sha: String): Option[String] =
      in.articleHistory.getOrElse(local, Nil).find(_._1 == sha).map(_._2)

    /** The two records of a row question side by side, where the screen can show more than labels. */
    def rowDetail(kind: BundleKind, u: UUID): Option[IssueDetail] = kind match
      case BundleKind.Source =>
        sourcesByUid
          .get(u)
          .zip(localized.sources.find(_.uid == u))
          .map((l, r) => IssueDetail(MergeIssueDetail.sourceFields(l, r)))
      case BundleKind.Event => localEvent.get(u).zip(bundleEvent.get(u)).map(MergeIssueDetail.eventDetail)
      case BundleKind.Place =>
        in.local.places.find(_.uid == u).zip(localized.places.find(_.uid == u)).map(MergeIssueDetail.placeDetail)
      case _ => None
    // a bundle uid → (the uid here, what M4 does)
    val merges = scala.collection.mutable.LinkedHashMap.empty[UUID, (UUID, Merges)]
    def articleMerge(f: UUID, o: UUID, oc: ArticleOutcome): Boolean = oc match
      case ArticleOutcome.Same | ArticleOutcome.Ours => merges(f) = o -> Merges.ArticleKeep; true
      case ArticleOutcome.Theirs                     => merges(f) = o -> Merges.ArticleTheirs; true
      case ArticleOutcome.Merged(b, _)               => merges(f) = o -> Merges.ArticleMerged(b); true
      case _                                         => false

    // changed on both sides; deleted there and changed here — unless M4 merges it
    initialRows.filter(_.decision == Decision.Conflict).foreach { r =>
      val left = Some(IssueSide(r.label, r.uid))
      val right =
        Option.when(!r.tombstone)(IssueSide(rawRevs.get(r.foreignUid).map(_.label).getOrElse(r.label), r.foreignUid))
      def conflictIssue(
          title: String,
          opts: List[DecisionKind],
          importable: Set[DecisionKind],
          detail: Option[IssueDetail] = None
      ): Unit =
        val kind = if r.tombstone then IssueKind.DeletedThereChangedHere else IssueKind.ChangedBothSides
        add(
          Draft(
            MergeIssue(
              issueId(kind, r.kind, List(r.foreignUid), List(r.uid), r.foreignRev, r.localRev.getOrElse("")),
              kind,
              IssueSeverity.Blocking,
              r.kind,
              title,
              left,
              right,
              Nil,
              opts,
              detail = detail.orElse(rowDetail(r.kind, r.uid))
            ),
            importable
          )
        )
      val article = List(km.KeepLeft, km.KeepRight, km.Both, km.File)
      if r.tombstone then
        conflictIssue("deleted in the bundle, changed here since the last import", lr, Set(km.KeepLeft, km.KeepRight))
      else
        r.kind match
          case BundleKind.Article =>
            val oc = outcome(localArticle(r.uid), r.foreignUid)
            if !articleMerge(r.foreignUid, r.uid, oc) then
              oc match
                case ArticleOutcome.Conflict(n, sha) =>
                  conflictIssue(
                    s"changed on both sides since a common version: ${Plural.count(n, "conflicting region")}",
                    article,
                    Set(km.KeepLeft, km.KeepRight),
                    Some(
                      IssueDetail(text =
                        Some(articleText(baseOf(r.uid, sha), localArticle(r.uid).body, rawArticle(r.foreignUid).body))
                      )
                    )
                  )
                case _ =>
                  conflictIssue(
                    "changed on both sides since the last import (no common base kept)",
                    article,
                    Set(km.KeepLeft, km.KeepRight),
                    Some(
                      IssueDetail(text =
                        Some(articleText(None, localArticle(r.uid).body, rawArticle(r.foreignUid).body))
                      )
                    )
                  )
          case BundleKind.Goal      => merges(r.foreignUid) = r.uid -> Merges.GoalFold
          case BundleKind.QueueItem => merges(r.foreignUid) = r.uid -> Merges.QueueFold
          case BundleKind.Event
              if localEvent.get(r.uid).zip(bundleEvent.get(r.uid)).exists((a, b) => MergeFold.sameCore(a, b)) =>
            merges(r.foreignUid) = r.uid -> Merges.EventFold
          case _ => conflictIssue("changed on both sides since the last import", lr, Set(km.KeepLeft, km.KeepRight))
    }

    // labels a different record has here — for the lines that would be written
    val deleting: Set[UUID] = initialRows.filter(r => r.tombstone && r.decision == Decision.Delete).map(_.uid).toSet
    val writesInitially: Set[UUID] =
      initialRows.filter(r => r.decision == Decision.Insert || r.decision == Decision.FastForward).map(_.uid).toSet
    def taken(owner: Option[UUID], self: UUID): Option[UUID] = owner.filter(o => o != self && !deleting(o))
    def bundleUidOf(u: UUID): UUID = mapped.collectFirst { case (k, v) if v == u => k }.getOrElse(u)
    val notes = List.newBuilder[String]
    val fatal = List.newBuilder[String]
    def labelTaken[A](
        kind: BundleKind,
        rows: List[A],
        uid: A => UUID,
        label: A => String,
        owner: A => Option[UUID],
        what: String,
        options: List[DecisionKind] = lr,
        merge: (A, UUID) => Option[Merges] = (_: A, _: UUID) => None,
        detailOf: (A, UUID) => Option[IssueDetail] = (_: A, _: UUID) => None
    ): Unit =
      rows.filter(a => writesInitially(uid(a))).foreach { a =>
        val l = label(a)
        taken(owner(a), uid(a)).foreach { o =>
          val f = bundleUidOf(uid(a))
          merge(a, o) match
            case Some(how) => merges(f) = o -> how
            case None      =>
              add(
                Draft(
                  MergeIssue(
                    issueId(
                      IssueKind.LabelTaken,
                      kind,
                      List(f),
                      List(o),
                      rawRevs.get(f).map(_.rev).getOrElse(""),
                      localRev(o)
                    ),
                    IssueKind.LabelTaken,
                    IssueSeverity.Blocking,
                    kind,
                    s"here that $what belongs to a different record",
                    Some(IssueSide(l, o)),
                    Some(IssueSide(l, f)),
                    List("keep-left leaves the bundle's record out (and whatever needs it)") ++
                      Option
                        .when(options.contains(km.RenameRight))(
                          "keep-right makes them one, the bundle's fields winning; " +
                            "rename-right keeps both, the bundle's under a new id"
                        )
                        .toList,
                    options,
                    detail = detailOf(a, o)
                  ),
                  Set(km.KeepLeft)
                )
              )
        }
      }
    labelTaken(
      BundleKind.Source,
      localized.sources.filter(s => localRevs.contains(s.uid)),
      _.uid,
      _.citekey,
      s => in.labels.citekeys.get(s.citekey).orElse(in.labels.aliases.get(s.citekey)),
      "citekey",
      detailOf = (s, o) => sourcesByUid.get(o).map(l => IssueDetail(MergeIssueDetail.sourceFields(l, s)))
    )
    labelTaken(
      BundleKind.Event,
      localized.events,
      _.uid,
      _.id,
      e => in.labels.events.get(e.id),
      "event id",
      options = List(km.KeepLeft, km.KeepRight, km.RenameRight),
      merge = (e, o) => localEvent.get(o).filter(MergeFold.sameCore(_, e)).map(_ => Merges.EventFold),
      detailOf = (e, o) => localEvent.get(o).map(MergeIssueDetail.eventDetail(_, e))
    )
    labelTaken(
      BundleKind.Goal,
      localized.goals,
      _.uid,
      _.slug,
      g => in.labels.goals.get(g.slug),
      "goal slug",
      merge = (_, o) => localGoal.get(o).map(_ => Merges.GoalFold)
    )
    // an acquire/tasks item with the same text here is the same item
    val queueClaimed = scala.collection.mutable.Set.empty[UUID]
    localized.queue
      .filter(q => writesInitially(q.uid) && q.list != Queue.Kind.Log.label)
      .sortBy(_.uid.toString)
      .foreach { q =>
        in.local.queue
          .filter(l =>
            l.list == q.list && !queueClaimed(l.uid) && !locRevs.contains(l.uid) &&
              MergeFold.queueKey(l.text) == MergeFold.queueKey(q.text)
          )
          .sortBy(_.uid.toString)
          .headOption
          .foreach { l =>
            queueClaimed += l.uid
            merges(bundleUidOf(q.uid)) = l.uid -> Merges.QueueFold
          }
      }
    // the slug a `both` gives the bundle's article: `<slug>-<the other collection's label>`
    val slugsUsed = scala.collection.mutable.Set
      .from(in.labels.slugs.keySet ++ in.local.articles.map(_.slug) ++ fes.articles.map(_.slug))
    val bothSlugs = scala.collection.mutable.Map.empty[UUID, String]
    def bothSlug(f: UUID, slug: String): String =
      bothSlugs.getOrElseUpdate(
        f, {
          val tag = AssetLinks.safeName(from).toLowerCase(java.util.Locale.ROOT)
          val s = Iterator.from(1).map(i => s"$slug-$tag${if i == 1 then "" else s"-$i"}").find(s => !slugsUsed(s)).get
          slugsUsed += s
          s
        }
      )
    // an article: the same slug, a different article
    localized.articles.filter(a => writesInitially(a.uid)).sortBy(_.slug).foreach { a =>
      taken(in.labels.slugs.get(a.slug), a.uid).foreach { o =>
        val mine = localArticle.get(o)
        val same = mine.exists(m => m.body == a.body && m.title == a.title)
        val f = bundleUidOf(a.uid)
        def blocking(title: String, base: Option[String]) =
          MergeIssue(
            issueId(IssueKind.ArticleDiverged, BundleKind.Article, List(f), List(o), rightRev(f), localRev(o)),
            IssueKind.ArticleDiverged,
            IssueSeverity.Blocking,
            BundleKind.Article,
            title,
            Some(IssueSide(a.slug, o, mine.map(m => s"\"${m.title}\" · ${m.body.length} characters").toList)),
            Some(IssueSide(a.slug, f, List(s"\"${a.title}\" · ${a.body.length} characters"))),
            List(s"both: the bundle's becomes ${bothSlug(f, a.slug)}"),
            List(km.KeepLeft, km.KeepRight, km.Both, km.File),
            detail = mine.map(m => IssueDetail(text = Some(articleText(base, m.body, rawArticle(f).body))))
          )
        if same then
          add(
            Draft(
              MergeIssue(
                issueId(IssueKind.ArticleDiverged, BundleKind.Article, List(f), List(o), rightRev(f), localRev(o)),
                IssueKind.ArticleDiverged,
                IssueSeverity.Default,
                BundleKind.Article,
                "the same text was written on both sides",
                Some(IssueSide(a.slug, o)),
                Some(IssueSide(a.slug, f)),
                Nil,
                List(km.KeepLeft, km.Both),
                Some(MergeDecision.of(km.KeepLeft))
              ),
              Set(km.KeepLeft)
            )
          )
        else
          val oc = mine.map(outcome(_, f)).getOrElse(ArticleOutcome.NoBase)
          if !articleMerge(f, o, oc) then
            oc match
              case ArticleOutcome.Conflict(n, sha) =>
                add(
                  Draft(
                    blocking(
                      s"one slug, two versions of the article: ${Plural.count(n, "conflicting region")} " +
                        "against their common version",
                      baseOf(o, sha)
                    ),
                    Set(km.KeepLeft)
                  )
                )
              case _ =>
                add(Draft(blocking("two different articles under one slug (no common base)", None), Set(km.KeepLeft)))
      }
    }

    // an event under another id here that is the same event: the same label, dates and place
    val eventsClaimed = scala.collection.mutable.Set.empty[UUID]
    localized.events
      .filter(e =>
        writesInitially(e.uid) && !merges.contains(bundleUidOf(e.uid)) &&
          in.labels.events.get(e.id).forall(_ == e.uid)
      )
      .sortBy(_.id)
      .foreach { e =>
        in.local.events
          .filter(l => !eventsClaimed(l.uid) && !locRevs.contains(l.uid) && MergeFold.sameEventAcrossIds(l, e))
          .sortBy(_.id)
          .headOption
          .foreach { l =>
            eventsClaimed += l.uid
            merges(bundleUidOf(e.uid)) = l.uid -> Merges.EventAcross
          }
      }

    // places and named periods added on both sides: one id is one place when where and when agree,
    // a question when they do not; a place under another id is one when its label and geometry are
    // (the database compared them: `placeMatches`)
    val localPlaceById = in.local.places.map(p => p.id -> p).toMap
    val localPeriodById = in.local.periods.map(p => p.id -> p).toMap
    val placeIssues = scala.collection.mutable.Map.empty[UUID, String] // bundle uid → issue
    localized.places.filter(p => writesInitially(p.uid)).sortBy(_.id).foreach { p =>
      val f = bundleUidOf(p.uid)
      localPlaceById.get(p.id).filter(_.uid != p.uid) match
        case Some(o) if MergeFold.samePlace(o, p) => merges(f) = o.uid -> Merges.PlaceFold
        case Some(o)                              =>
          val id = issueId(IssueKind.PlaceDiffers, BundleKind.Place, List(f), List(o.uid), rightRev(f), localRev(o.uid))
          placeIssues(f) = id
          add(
            Draft(
              MergeIssue(
                id,
                IssueKind.PlaceDiffers,
                IssueSeverity.Blocking,
                BundleKind.Place,
                s"place ${p.id} was added on both sides, " +
                  (if o.geom != p.geom then "with different geometries" else "with different time spans"),
                Some(IssueSide(o.id, o.uid, List(o.label))),
                Some(IssueSide(p.id, f, List(p.label))),
                List(
                  "keep-left keeps this collection's place (the bundle's events point at it); keep-right takes the bundle's " +
                    "geometry and span"
                ),
                lr,
                detail = Some(MergeIssueDetail.placeDetail(o, p))
              ),
              Set.empty
            )
          )
        case None =>
          localPlaceById.values
            .find(x => in.placeMatches.get(f).contains(x.id))
            .foreach(o => merges(f) = o.uid -> Merges.PlaceFold)
    }
    val periodIssues = scala.collection.mutable.Map.empty[UUID, String]
    localized.periods.filter(p => writesInitially(p.uid)).sortBy(_.id).foreach { p =>
      val f = bundleUidOf(p.uid)
      localPeriodById.get(p.id).filter(_.uid != p.uid) match
        case Some(o) if MergeFold.samePeriod(o, p) => merges(f) = o.uid -> Merges.PeriodFold
        case Some(o)                               =>
          val id =
            issueId(IssueKind.NamedPeriodDiffers, BundleKind.Period, List(f), List(o.uid), rightRev(f), localRev(o.uid))
          periodIssues(f) = id
          add(
            Draft(
              MergeIssue(
                id,
                IssueKind.NamedPeriodDiffers,
                IssueSeverity.Blocking,
                BundleKind.Period,
                s"named period ${p.id} was added on both sides and says something else: " +
                  s"${o.label} ${MergeIssueDetail.rangeText(o.period)} here, ${p.label} ${MergeIssueDetail.rangeText(p.period)} in the bundle",
                Some(IssueSide(o.id, o.uid, List(o.label))),
                Some(IssueSide(p.id, f, List(p.label))),
                List("keep-left keeps this collection's period; keep-right takes the bundle's label and span"),
                lr,
                detail = Some(
                  IssueDetail(
                    fields = List(
                      IssueField("label", Some(o.label), Some(p.label)),
                      IssueField(
                        "span",
                        Some(MergeIssueDetail.rangeText(o.period)),
                        Some(MergeIssueDetail.rangeText(p.period))
                      ),
                      IssueField("authority", o.authority, p.authority),
                      IssueField("region", o.region, p.region)
                    ),
                    place = Some(
                      IssuePlace(
                        leftSpan = o.period.map(MergeIssueDetail.span),
                        rightSpan = p.period.map(MergeIssueDetail.span)
                      )
                    )
                  )
                )
              ),
              Set.empty
            )
          )
        case None =>
          localPeriodById.values
            .find(x => in.periodMatches.get(f).contains(x.id))
            .foreach(o => merges(f) = o.uid -> Merges.PeriodFold)
    }

    // a goal that is one on both sides with two approved plans and neither newer: a question (#881)
    val goalPlanIssue = scala.collection.mutable.Map.empty[UUID, String] // bundle uid → issue
    merges.toList.collect { case (f, (o, Merges.GoalFold)) => (f, o) }.foreach { (f, o) =>
      for
        l <- localGoal.get(o)
        r <- localized.goals.find(_.uid == toLocal(f))
        if MergeFold.planOutcome(l, r) == MergeFold.PlanOutcome.Differ
      do
        val id = issueId(IssueKind.GoalPlanDiffers, BundleKind.Goal, List(f), List(o), rightRev(f), localRev(o))
        goalPlanIssue(f) = id
        add(
          Draft(
            MergeIssue(
              id,
              IssueKind.GoalPlanDiffers,
              IssueSeverity.Blocking,
              BundleKind.Goal,
              s"goal ${l.slug}: two different approved plans, and neither is known to be the newer",
              Some(IssueSide(l.slug, o, l.planApprovedAt.map(a => s"approved $a").toList)),
              Some(IssueSide(r.slug, f, r.planApprovedAt.map(a => s"approved $a").toList)),
              List("the plan approved later wins when both say when; here they do not"),
              lr,
              detail = Some(
                IssueDetail(fields =
                  List(
                    IssueField("plan", l.plan.map(MergeIssueDetail.planText), r.plan.map(MergeIssueDetail.planText)),
                    IssueField("approved", l.planApprovedAt, r.planApprovedAt)
                  )
                )
              )
            ),
            Set.empty
          )
        )
    }
    // …and the schedules both sides have for a goal that is one: this collection's are kept
    localized.goals.foreach { r =>
      val here = merges
        .get(bundleUidOf(r.uid))
        .collect { case (o, Merges.GoalFold) => o }
        .orElse(Option.when(localGoal.contains(r.uid))(r.uid))
      here.flatMap(localGoal.get).foreach { l =>
        MergeFold.scheduleDifferences(l, r).foreach { (k, a, b) =>
          info += s"goal ${l.slug}: its $k schedule differs (here ${a.cron}${if a.enabled then "" else " off"}, " +
            s"the bundle's ${b.cron}${if b.enabled then "" else " off"}) — this collection's is kept"
        }
      }
    }

    // ── resolutions ──
    val all = drafts.result().distinctBy(_.issue.id)
    val byId: Map[String, Draft] = all.map(d => d.issue.id -> d).toMap
    val errors = List.newBuilder[String]
    val labelsHere = in.labels.citekeys.keySet ++ in.labels.aliases.keySet ++ fes.sources.map(_.citekey)
    val eventIdsHere = in.local.events.map(_.id).toSet ++ fes.events.map(_.id)
    val texts: Map[String, String] = resolutions.resolve.flatMap(r => r.text.map(r.issue.trim -> _)).toMap
    // a resolution an earlier apply carried out answers an issue that is gone because of it: done, not stale
    val carriedOut = resolutions.resolve.filter(r =>
      !byId.contains(r.issue.trim) &&
        in.remembered.get(r.issue.trim).exists(d => MergeDecision.parse(r.decision).toOption.contains(d))
    )
    if carriedOut.nonEmpty then
      info += s"${Plural.count(carriedOut.size, "resolution")} carried out by an earlier `merge apply` (the issues are gone)"
    val chosen: Map[String, MergeDecision] = resolutions.resolve
      .filterNot(carriedOut.contains)
      .flatMap { r =>
        val id = r.issue.trim
        (byId.get(id), MergeDecision.parse(r.decision)) match
          case (None, _) =>
            errors += s"$id: not an issue of this plan — stale (the records changed since it was decided) or never one"
            None
          case (_, Left(e))          => errors += s"$id: $e"; None
          case (Some(d), Right(dec)) =>
            val i = d.issue
            val argOk: Either[String, Unit] = dec.kind match
              case km.Map =>
                val allowed = List(i.left, i.right).flatten.map(_.label)
                val ck = dec.argument.getOrElse("")
                val leftUid = i.left.map(_.uid)
                if !allowed.contains(ck) then
                  Left(s"map takes one of the two citekeys: ${allowed.map(c => s"map:$c").mkString(" or ")}")
                else if in.labels.citekeys.get(ck).orElse(in.labels.aliases.get(ck)).exists(o => !leftUid.contains(o))
                then Left(s"'$ck' is a citekey (or an old one) of another source here — merge under the other citekey")
                else Right(())
              case km.RenameRight | km.RenameLeft if i.entity == BundleKind.Event =>
                val eid = dec.argument.getOrElse("")
                if !SlugShape.matches(eid) then Left(s"'$eid' is not an event id (lower-case letters, digits, _ . -)")
                else if eventIdsHere(eid) then Left(s"'$eid' is already an event id here or in the bundle")
                else Right(())
              case km.RenameRight | km.RenameLeft =>
                val ck = dec.argument.getOrElse("")
                if !Citekeys.valid(ck) then Left(s"'$ck' is not a citekey (lower-case letters, digits and _)")
                else if labelsHere(ck) then Left(s"'$ck' is already a citekey (or an old one) here or in the bundle")
                else Right(())
              case _ => Right(())
            if !i.options.contains(dec.kind) then
              errors += s"$id: ${dec.wire} is not a decision for this issue (${i.options.map(DecisionKind.wireOf).mkString(" | ")})"
              None
            else
              argOk match
                case Left(e)  => errors += s"$id: $e"; None
                case Right(_) => Some(id -> dec)
      }
      .toMap
    // the decision in force for an issue, and who made it: a resolution, a policy, an earlier apply,
    // the default
    def decisionOf(i: MergeIssue): Option[(MergeDecision, DecidedBy)] =
      chosen
        .get(i.id)
        .map(_ -> DecidedBy.Resolution)
        .orElse(
          Option
            .when(i.kind == IssueKind.YearDiffers && policy.editions.contains(EditionsPolicy.Merge))(i.suggested)
            .flatten
            .map(_ -> DecidedBy.Policy)
        )
        .orElse(in.remembered.get(i.id).filter(d => i.options.contains(d.kind)).map(_ -> DecidedBy.Remembered))
        .orElse(Option.when(i.severity == IssueSeverity.Default)(i.suggested).flatten.map(_ -> DecidedBy.Default))
    // an issue conditional on another applies when that one is decided its way; conditions chain (an
    // attestation's question under the body question under the same-work question)
    val standings = scala.collection.mutable.Map.empty[String, Standing]
    def standingOf(id: String, seen: Set[String]): Standing =
      standings.get(id) match
        case Some(s) => s
        case None    =>
          val s = byId(id).issue.condition match
            case None                                                => Standing.Live
            case Some(c) if seen(c.issue) || !byId.contains(c.issue) => Standing.Live
            case Some(c)                                             =>
              standingOf(c.issue, seen + id) match
                case Standing.Dropped     => Standing.Dropped
                case Standing.Conditional => Standing.Conditional
                case Standing.Live        =>
                  // the other alternative of a DEFAULT stays listed (conditional): overriding a default
                  // is a decision too, and it must not reveal an issue that was not listed
                  decisionOf(byId(c.issue).issue) match
                    case None                             => Standing.Conditional
                    case Some((d, _)) if d.kind == c.when => Standing.Live
                    case Some((_, DecidedBy.Default))     => Standing.Conditional
                    case Some(_)                          => Standing.Dropped
          standings(id) = s
          s
    val live: List[Draft] = all.flatMap { d =>
      standingOf(d.issue.id, Set.empty) match
        case Standing.Live        => Some(d.modify(_.issue.condition).setTo(None))
        case Standing.Conditional => Some(d)
        case Standing.Dropped     => None
    }
    val liveIds = live.map(_.issue.id).toSet
    chosen.keys.toList.sorted.filterNot(liveIds).foreach { id =>
      val c = byId(id).issue.condition.get
      val now = if standingOf(c.issue, Set.empty) == Standing.Dropped then "does not apply"
      else s"is ${decisionOf(byId(c.issue).issue).fold("open")(_._1.wire)}"
      errors += s"$id: applies only if ${c.issue} = ${DecisionKind.wireOf(c.when)}, and ${c.issue} $now"
    }
    val decided: List[Draft] = live.map { d =>
      val i = d.issue
      val dec = decisionOf(i)
      d.copy(issue = i.copy(decision = dec.map(_._1), decidedBy = dec.map(_._2)))
    }
    val issues = decided.map(_.issue)
    val unconditional: Map[String, MergeIssue] = issues.filter(_.condition.isEmpty).map(i => i.id -> i).toMap

    /** The decision in force for an issue that applies. */
    def decisionIn(id: String): Option[MergeDecision] = unconditional.get(id).flatMap(_.decision)
    val gateDecision: Map[String, MergeDecision] =
      issues.filter(_.kind == IssueKind.SameWork).flatMap(i => i.decision.map(i.id -> _)).toMap

    // ── the folds: what merge apply writes, as decided now ──
    // a bundle uid → (the uid here, kind); replacement lines by their final uid, built once the
    // folds are known (from the bundle's lines in this collection's uids)
    val foldMap = scala.collection.mutable.LinkedHashMap.empty[UUID, (UUID, BundleKind)]
    val overrides = scala.collection.mutable.Map.empty[UUID, Decision]
    type Line[A] = BundleEntities => A
    val sourceLine = scala.collection.mutable.Map.empty[UUID, Line[BundleSource]]
    val findingLine = scala.collection.mutable.Map.empty[UUID, Line[BundleFinding]]
    val assetLine = scala.collection.mutable.Map.empty[UUID, Line[BundleAsset]]
    val linkLine = scala.collection.mutable.Map.empty[UUID, Line[BundleAssetLink]]
    val attLine = scala.collection.mutable.Map.empty[UUID, Line[BundleAttestation]]
    val articleLine = scala.collection.mutable.Map.empty[UUID, Line[BundleArticle]]
    val eventLine = scala.collection.mutable.Map.empty[UUID, Line[BundleEvent]]
    val goalLine = scala.collection.mutable.Map.empty[UUID, Line[BundleGoal]]
    val queueLine = scala.collection.mutable.Map.empty[UUID, Line[BundleQueueItem]]
    val placeLine = scala.collection.mutable.Map.empty[UUID, Line[BundlePlace]]
    val periodLine = scala.collection.mutable.Map.empty[UUID, Line[BundlePeriod]]
    def sourceIn(es: BundleEntities, u: UUID) = es.sources.find(_.uid == u).get
    def findingIn(es: BundleEntities, u: UUID) = es.findings.find(_.uid == u).get
    def assetIn(es: BundleEntities, u: UUID) = es.assets.find(_.uid == u).get
    def attIn(es: BundleEntities, u: UUID) = es.attestations.find(_.uid == u).get
    def articleIn(es: BundleEntities, u: UUID) = es.articles.find(_.uid == u).get
    def eventIn(es: BundleEntities, u: UUID) = es.events.find(_.uid == u).get
    def goalIn(es: BundleEntities, u: UUID) = es.goals.find(_.uid == u).get
    def queueIn(es: BundleEntities, u: UUID) = es.queue.find(_.uid == u).get
    def placeIn(es: BundleEntities, u: UUID) = es.places.find(_.uid == u).get
    def periodIn(es: BundleEntities, u: UUID) = es.periods.find(_.uid == u).get
    val dropped = scala.collection.mutable.LinkedHashSet.empty[UUID] // bundle uids left out for good
    val foldedSources = scala.collection.mutable.LinkedHashMap.empty[UUID, UUID]
    val renames = List.newBuilder[(UUID, String, String)]
    val snapshots = scala.collection.mutable.LinkedHashSet.empty[UUID]
    val versions = List.newBuilder[ExtraVersion]
    val copies = List.newBuilder[Line[BundleArticle]]
    val attFixes = List.newBuilder[AttestationFix]
    val bodyRenames = scala.collection.mutable.Map.empty[UUID, List[(String, String)]]
    val reviews = scala.collection.mutable.LinkedHashSet.empty[String]
    var tally = MergeTally() // the apply's report, added to as each fold is decided
    def fold(f: UUID, l: UUID, kind: BundleKind): Unit = foldMap(f) = l -> kind

    // the citekey each source of the bundle will have here (`[[citekey]]` links follow it)
    val finalCitekey = scala.collection.mutable.Map.empty[UUID, String]
    val collisionDecided = issues.filter(i => i.kind == IssueKind.CitekeyCollision && i.condition.isEmpty)
    val renameRight: Map[UUID, String] = collisionDecided
      .flatMap(i => i.decision.filter(_.kind == km.RenameRight).flatMap(_.argument).zip(i.right.map(_.uid)).map(_.swap))
      .toMap
    collisionDecided.foreach { i =>
      i.decision
        .filter(_.kind == km.RenameLeft)
        .flatMap(_.argument)
        .zip(i.left)
        .foreach((ck, l) => renames += ((l.uid, l.label, ck)))
    }
    val localFindingIds = in.local.findings.map(_.id).toSet
    val assignedFindingIds = scala.collection.mutable.Set.from(localFindingIds)

    // the pairs that fold: every auto-merge, and a gate decided map
    pairs.foreach { (p, _) =>
      val (r, l) = (p.right.source, p.left.source)
      val mapTo: Option[String] = fates(r.uid) match
        case Fate.Auto(_)    => Some(l.citekey)
        case Fate.Gate(_, g) => decisionIn(g).filter(_.kind == km.Map).flatMap(_.argument)
        case _               => None
      mapTo.foreach { ck =>
        fold(r.uid, l.uid, BundleKind.Source)
        foldedSources(r.uid) = l.uid
        finalCitekey(r.uid) = ck
        tally = tally.copy(
          sourcesMerged = tally.sourcesMerged + 1,
          sourcesByScore = tally.sourcesByScore + (if p.evidence.strongKey then 0 else 1)
        )
        val fieldDec = pairFieldIds.getOrElse(r.uid, Map.empty).flatMap((k, id) => decisionIn(id).map(k -> _))
        val merged = MergeFold.foldSource(l, r, fieldDec, ck)
        sourceLine(l.uid) = _ => merged
        val keepRightBody = merged.bodySha256 != l.bodySha256

        // findings: the same claim folds, a near-duplicate folds when decided `merge`, the rest arrive
        // under the survivor's citekey, numbered after its last
        val ms = findingMatches.getOrElse(r.uid, Nil)
        def merging(rf: BundleFinding) = nearIssue.get(rf.uid).flatMap(decisionIn).exists(_.kind == km.Merge)
        val renumbered =
          localFindingsOf.getOrElse(l.uid, Nil).map(f => s"$ck#${f.id.dropWhile(_ != '#').drop(1)}").toSet
        val incoming = ms.collect {
          case FindingMatch.Unmatched(rf)                  => rf
          case FindingMatch.Near(rf, _, _) if !merging(rf) => rf
        }
        val ids = MergeFold.nextFindingIds(ck, assignedFindingIds.toSet ++ renumbered, incoming.size)
        assignedFindingIds ++= ids
        incoming.zip(ids).foreach((rf, id) => findingLine(rf.uid) = es => findingIn(es, rf.uid).copy(id = id))
        ms.foreach {
          case FindingMatch.Same(rf, lf) =>
            fold(rf.uid, lf.uid, BundleKind.Finding); findingLine(lf.uid) = _ => MergeFold.foldFinding(lf, rf)
            tally = tally.modify(_.findingsMerged).using(_ + 1)
          case FindingMatch.Near(rf, lf, _) if merging(rf) =>
            fold(rf.uid, lf.uid, BundleKind.Finding); findingLine(lf.uid) = _ => MergeFold.foldFinding(lf, rf)
            tally = tally.modify(_.findingsMerged).using(_ + 1)
          case _ => ()
        }

        // assets: the same bytes are one figure; a name taken here gets the other collection's label
        val localAssets = in.local.assets.filter(_.sourceUid == l.uid)
        val names = scala.collection.mutable.Set.from(localAssets.map(_.name))
        val usedLocal = scala.collection.mutable.Set.empty[UUID]
        fes.assets.filter(a => a.sourceUid == r.uid && !localRevs.contains(toLocal(a.uid))).sortBy(_.name).foreach { a =>
          localAssets.find(x => x.sha256 == a.sha256 && !usedLocal(x.uid)) match
            case Some(x) =>
              usedLocal += x.uid; fold(a.uid, x.uid, BundleKind.Asset); assetLine(x.uid) = _ => x
              tally = tally.modify(_.assetsMerged).using(_ + 1)
            case None if names(a.name) =>
              val n = MergeFold.freeAssetName(a.name, from, names.toSet)
              names += n
              assetLine(a.uid) = es => assetIn(es, a.uid).copy(name = n)
              if keepRightBody then bodyRenames(l.uid) = bodyRenames.getOrElse(l.uid, Nil) :+ (a.name -> n)
            case None => names += a.name
        }
        // asset links of a folded figure that this collection's figure already has are one
        fes.assetLinks.filter(k => foldMap.get(k.assetUid).exists(_._2 == BundleKind.Asset)).foreach { k =>
          val target =
            k.targetUid.map(u => foldMap.get(u).map(_._1).getOrElse(toLocal(u)).toString).getOrElse(k.targetId)
          in.local.assetLinks
            .find(x =>
              x.assetUid == foldMap(k.assetUid)._1 && x.relation == k.relation &&
                x.targetType == k.targetType && x.targetUid.map(_.toString).getOrElse(x.targetId) == target
            )
            .foreach { x =>
              fold(k.uid, x.uid, BundleKind.AssetLink); linkLine(x.uid) = _ => x
            }
        }

        // attestations: re-anchored into the body kept, or as decided; one already here is one
        val alt = pairFieldIds.get(r.uid).flatMap(_.get(IssueKind.BodyDiffers)) match
          case Some(id) => Some(decisionIn(id).map(_.kind).getOrElse(km.KeepLeft))
          case None     => None
        val tries = anchors.get(r.uid).flatMap(_.get(alt)).getOrElse(Nil)
        val localAtts = localAttsOf.getOrElse(l.uid, Nil)
        def targetKey(t: BundleAttestation): String =
          t.targetUid.map(u => foldMap.get(u).map(_._1).getOrElse(toLocal(u)).toString).getOrElse(t.targetId)
        foreignAttsOf.getOrElse(r.uid, Nil).foreach { t =>
          localAtts.find(x => x.targetType == t.targetType && targetKey(x) == targetKey(t) && x.quote == t.quote) match
            case Some(x) => fold(t.uid, x.uid, BundleKind.Attestation); attLine(x.uid) = _ => x
            case None    =>
              tries.find(a => a.foreign && a.att.uid == t.uid) match
                case None                                  => ()
                case Some(AnchorTry(_, _, Some(a), co, _)) =>
                  attLine(t.uid) =
                    es => attIn(es, t.uid).copy(charStart = Some(a.start), charEnd = Some(a.end), chunkOrd = co)
                  tally = tally.modify(_.attestationsReanchored).using(_ + 1)
                case Some(AnchorTry(_, _, None, _, issue)) =>
                  issue.flatMap(decisionIn).map(_.kind) match
                    case Some(km.KeepUnanchored) =>
                      attLine(t.uid) = es => attIn(es, t.uid).copy(charStart = None, charEnd = None, chunkOrd = None)
                    case _ =>
                      dropped += t.uid
                      tally = tally.modify(_.attestationsDropped).using(_ + 1)
        }
        tries.filterNot(_.foreign).foreach {
          case AnchorTry(t, _, Some(a), co, _) =>
            attFixes += AttestationFix(t.uid, Some(a), co, drop = false)
            tally = tally.modify(_.attestationsReanchored).using(_ + 1)
          case AnchorTry(t, _, None, _, issue) =>
            issue.flatMap(decisionIn).map(_.kind) match
              case Some(km.KeepUnanchored) => attFixes += AttestationFix(t.uid, None, None, drop = false)
              case _                       =>
                attFixes += AttestationFix(t.uid, None, None, drop = true)
                tally = tally.modify(_.attestationsDropped).using(_ + 1)
        }
      }
    }
    // the citekey every other source of the bundle will have here
    fes.sources.foreach { s =>
      if !finalCitekey.contains(s.uid) then
        fates(s.uid) match
          case Fate.Same(l, _) =>
            val writes =
              initialOf.get(s.uid).exists(r => r.decision == Decision.FastForward || r.decision == Decision.Insert)
            finalCitekey(s.uid) = if writes then localized.sources.find(_.uid == l).fold(s.citekey)(_.citekey)
            else sourcesByUid.get(l).fold(s.citekey)(_.citekey)
          case _ => finalCitekey(s.uid) = renameRight.getOrElse(s.uid, s.citekey)
    }
    renameRight.foreach { (u, ck) =>
      sourceLine(u) = es => sourceIn(es, u).copy(citekey = ck)
      fes.findings
        .filter(_.sourceUid == u)
        .foreach(f =>
          findingLine(f.uid) = es => findingIn(es, f.uid).copy(id = s"$ck#${f.id.dropWhile(_ != '#').drop(1)}")
        )
    }
    tally = tally.copy(sourcesRenamed = renameRight.size + renames.result().size)
    // a finding id or an asset name a different record has here: the bundle's is renumbered / renamed
    // (a source whose citekey collides is renamed as a whole, its findings with it)
    val collidingSources: Set[UUID] = fes.sources
      .filter(s =>
        fates.get(s.uid).exists(f => f == Fate.New || f.isInstanceOf[Fate.Gate]) &&
          (in.labels.citekeys.contains(s.citekey) || in.labels.aliases.contains(s.citekey))
      )
      .map(_.uid)
      .toSet
    val relabelled = scala.collection.mutable.ListBuffer.empty[String]
    initialRows
      .filter(r =>
        r.decision == Decision.Insert && r.kind == BundleKind.Finding && !findingLine.contains(r.foreignUid) &&
          !findingSource.get(r.foreignUid).exists(collidingSources)
      )
      .foreach { r =>
        val f = localized.findings.find(_.uid == r.uid).get
        in.labels.findings.get(f.id).filter(_ != f.uid).foreach { _ =>
          val ck = f.id.takeWhile(_ != '#')
          val id = MergeFold.nextFindingIds(ck, assignedFindingIds.toSet, 1).head
          assignedFindingIds += id
          findingLine(r.foreignUid) = es => findingIn(es, r.foreignUid).copy(id = id)
          info += s"finding ${f.id}: that id is another finding's here — the bundle's arrives as $id"
          relabelled += s"finding ${f.id} → $id"
        }
      }
    val assetNamesTaken = scala.collection.mutable.Set.from(in.local.assets.map(a => (a.sourceUid, a.name)))
    initialRows
      .filter(r => r.decision == Decision.Insert && r.kind == BundleKind.Asset && !assetLine.contains(r.foreignUid))
      .foreach { r =>
        val a = localized.assets.find(_.uid == r.uid).get
        if in.labels.assets.get((a.sourceUid, a.name)).exists(_ != a.uid) then
          val n = MergeFold.freeAssetName(
            a.name,
            from,
            assetNamesTaken.collect { case (s, x) if s == a.sourceUid => x }.toSet
          )
          assetNamesTaken += ((a.sourceUid, n))
          assetLine(r.foreignUid) = es => assetIn(es, r.foreignUid).copy(name = n)
          if initialOf.get(fes.assets.find(_.uid == r.foreignUid).get.sourceUid).exists(_.decision.writes) then
            bodyRenames(a.sourceUid) = bodyRenames.getOrElse(a.sourceUid, Nil) :+ (a.name -> n)
          info += s"asset ${a.name}: that name is another figure's in its source here — the bundle's arrives as $n"
          relabelled += s"asset ${a.name} → $n"
      }
    val ckMap: Map[String, String] =
      fes.sources.flatMap(s => finalCitekey.get(s.uid).filter(_ != s.citekey).map(s.citekey -> _)).toMap
    def rewrite(body: String): String = if ckMap.isEmpty then body else ArticleLinks.retarget(body, ckMap.get)._1

    // articles, goals, queue items and events M4 merges by itself, and the decisions about the rest
    def articleFold(f: UUID, o: UUID, body: Option[String], theirsWins: Boolean, review: Boolean): Unit =
      val raw = rawArticle(f)
      if f != o then fold(f, o, BundleKind.Article)
      val loc = localArticle(o)
      articleLine(o) = es =>
        val theirs = articleIn(es, o)
        val b = body.getOrElse(rewrite(theirs.body))
        if theirsWins then theirs.copy(uid = loc.uid, origin = loc.origin, slug = loc.slug, body = b, rev = "")
        else MergeFold.foldArticle(loc, theirs, b)
      snapshots += o
      versions += ExtraVersion(o, raw.body, raw.summary, raw.updatedAt)
      if review then reviews += loc.slug
    merges.foreach { case (f, (o, how)) =>
      how match
        case Merges.ArticleKeep =>
          if f != o then fold(f, o, BundleKind.Article)
          articleLine(o) = _ => localArticle(o)
          overrides(f) = Decision.Kept
        case Merges.ArticleTheirs =>
          articleFold(f, o, None, theirsWins = true, review = false)
          tally = tally.modify(_.articlesFastForward).using(_ + 1)
        case Merges.ArticleMerged(b) =>
          articleFold(f, o, Some(rewrite(b)), theirsWins = false, review = true)
          tally = tally.modify(_.articlesThreeWay).using(_ + 1)
        case Merges.GoalFold =>
          if f != o then fold(f, o, BundleKind.Goal)
          val planDecision = goalPlanIssue.get(f).flatMap(decisionIn).map(_.kind)
          goalLine(o) = es => MergeFold.foldGoal(localGoal(o), goalIn(es, o), from, planDecision)
          tally = tally.modify(_.goalsMerged).using(_ + 1)
        case Merges.QueueFold =>
          if f != o then fold(f, o, BundleKind.QueueItem)
          queueLine(o) = es => MergeFold.foldQueue(localQueue(o), queueIn(es, o))
          tally = tally.modify(_.queueMerged).using(_ + 1)
        case Merges.EventFold | Merges.EventAcross =>
          if f != o then fold(f, o, BundleKind.Event)
          eventLine(o) = es => MergeFold.foldEvent(localEvent(o), eventIn(es, o))
          tally = tally.modify(_.eventsMerged).using(_ + 1)
        case Merges.PlaceFold =>
          fold(f, o, BundleKind.Place)
          placeLine(o) = es => MergeFold.foldPlace(localPlace(o), placeIn(es, o))
          tally = tally.modify(_.placesMerged).using(_ + 1)
        case Merges.PeriodFold =>
          fold(f, o, BundleKind.Period)
          periodLine(o) = _ => localPeriod(o)
          tally = tally.modify(_.periodsMerged).using(_ + 1)
    }
    // the decisions about articles and events that M4 carries out (keep-left and keep-right of a
    // plain conflict are M2's row decisions, below)
    val keptRight = scala.collection.mutable.Set.empty[UUID]
    unconditional.values.toList.sortBy(_.id).foreach { i =>
      (i.kind, i.entity, i.decision, i.right.map(_.uid), i.left.map(_.uid)) match
        case (IssueKind.ArticleDiverged | IssueKind.ChangedBothSides, BundleKind.Article, Some(d), Some(f), Some(o)) =>
          val sameUid = toLocal(f) == o
          d.kind match
            case km.KeepRight if !sameUid =>
              articleFold(f, o, None, theirsWins = true, review = true)
            case km.KeepRight =>
              keptRight += f
              reviews += localArticle(o).slug
            case km.File =>
              articleFold(f, o, Some(texts.getOrElse(i.id, "")), theirsWins = false, review = true)
              if sameUid then overrides(f) = Decision.FastForward
              tally = tally.modify(_.articlesFromFile).using(_ + 1)
            case km.Both =>
              val slug = bothSlug(f, rawArticle(f).slug)
              if sameUid then
                overrides(f) = Decision.Kept
                val copyUid = UUID.nameUUIDFromBytes(s"merge-both:$f".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                copies += (es =>
                  articleIn(es, o).copy(uid = copyUid, slug = slug, body = rewrite(rawArticle(f).body), rev = "")
                )
              else
                articleLine(f) = es => articleIn(es, f).copy(slug = slug, body = rewrite(rawArticle(f).body), rev = "")
              reviews += slug
              tally = tally.modify(_.articlesBoth).using(_ + 1)
            case _ => ()
        case (IssueKind.LabelTaken, BundleKind.Event, Some(d), Some(f), Some(o))
            if d.kind == km.KeepRight && localEvent.contains(o) =>
          fold(f, o, BundleKind.Event)
          eventLine(o) = es =>
            MergeFold.foldEvent(
              eventIn(es, o).copy(id = localEvent(o).id, origin = localEvent(o).origin),
              localEvent(o)
            )
          tally = tally.modify(_.eventsMerged).using(_ + 1)
        case (IssueKind.LabelTaken, BundleKind.Event, Some(d), Some(f), _) if d.kind == km.RenameRight =>
          d.argument.foreach(eid => eventLine(f) = es => eventIn(es, f).copy(id = eid))
        // a place (named period) added on both sides under one id: ours stays, or theirs wins
        case (IssueKind.PlaceDiffers, BundleKind.Place, Some(d), Some(f), Some(o)) =>
          fold(f, o, BundleKind.Place)
          tally = tally.modify(_.placesMerged).using(_ + 1)
          if d.kind == km.KeepRight then
            placeLine(o) = es => MergeFold.foldPlace(placeIn(es, o).copy(origin = localPlace(o).origin), localPlace(o))
          else overrides(f) = Decision.Kept
        case (IssueKind.NamedPeriodDiffers, BundleKind.Period, Some(d), Some(f), Some(o)) =>
          fold(f, o, BundleKind.Period)
          tally = tally.modify(_.periodsMerged).using(_ + 1)
          if d.kind == km.KeepRight then
            periodLine(o) = es => periodIn(es, o).copy(origin = localPeriod(o).origin, rev = "")
          else overrides(f) = Decision.Kept
        case _ => ()
    }
    // every other article the bundle writes carries its links in this collection's citekeys
    if ckMap.nonEmpty then
      initialRows
        .filter(r =>
          r.kind == BundleKind.Article && !foldMap.contains(r.foreignUid) && !articleLine.contains(r.uid) &&
            !overrides.contains(r.foreignUid) &&
            (r.decision == Decision.Insert || r.decision == Decision.FastForward || keptRight(r.foreignUid))
        )
        .foreach { r =>
          val raw = rawArticle(r.foreignUid)
          val b = rewrite(raw.body)
          if b != raw.body then
            articleLine(r.uid) = es => articleIn(es, r.uid).copy(body = b, rev = "")
            versions += ExtraVersion(r.uid, raw.body, raw.summary, raw.updatedAt)
        }

    // ── the final lines ──
    val foldUids: Map[UUID, UUID] = foldMap.view.mapValues(_._1).toMap
    val es0 = localize(localized, foldUids, Map.empty)
    def replaced[A](
        as: List[A],
        uid: A => UUID,
        repl: scala.collection.Map[UUID, Line[A]],
        keep: A => Boolean = (_: A) => true
    ): List[A] =
      val base = as.filter(keep).map(a => repl.get(uid(a)).fold(a)(_(es0)))
      val extra = repl.keySet.diff(as.map(uid).toSet).toList.sortBy(_.toString).map(u => repl(u)(es0))
      (base ++ extra).distinctBy(uid)
    val foldedHere = foldedSources.values.toSet
    // a merged source's whole edge set: ours and the bundle's, re-pointed, without self-loops
    val edges0 = es0.edges ++ in.local.edges.filter(e => foldedHere(e.srcUid))
    val selfLoops = edges0.filter(e => e.srcUid == e.dstUid).distinct
    if selfLoops.nonEmpty then
      tally = tally.copy(selfLoopsDropped = selfLoops.size)
      selfLoops.foreach(e => info += s"edge ${e.kind} from a source to itself (both ends merged into one) — left out")
    // the bundle's citekey of a merged source becomes an old citekey of the survivor
    val citekeysHere = in.labels.citekeys.keySet ++ fes.sources.flatMap(s => finalCitekey.get(s.uid))
    val newAliases = foldedSources.toList.flatMap { (f, l) =>
      val raw = fes.sources.find(_.uid == f).get
      Option.when(
        raw.citekey != finalCitekey(f) && !citekeysHere(raw.citekey) &&
          in.labels.aliases.get(raw.citekey).forall(_ == l) && !fes.aliases.exists(_.alias == raw.citekey)
      )(BundleAlias(raw.citekey, l, raw.updatedAt))
    }
    // …and where it cannot, it is said
    foldedSources.foreach { (f, l) =>
      val raw = fes.sources.find(_.uid == f).get
      in.labels.citekeys
        .get(raw.citekey)
        .orElse(in.labels.aliases.get(raw.citekey))
        .filter(_ != l)
        .foreach(o =>
          info += s"source ${raw.citekey}: merged into ${finalCitekey(f)}, its citekey stays with " +
            s"${sourcesByUid.get(o).fold(o.toString)(_.citekey)} here (not kept as an old citekey)"
        )
    }
    tally = tally.copy(aliases =
      newAliases.size +
        foldedSources.count((f, l) => sourcesByUid.get(l).exists(_.citekey != finalCitekey(f)))
    )

    /** An old label travels unless it names ANOTHER record here — as a live label or as an old one of its own (an alias
      * is never also another record's label: it would resolve two ways).
      */
    def keptAliases[A](
        as: List[A],
        alias: A => String,
        owner: A => UUID,
        live: Map[String, UUID],
        old: Map[String, UUID],
        what: String,
        record: String
    ): List[A] =
      as.filter { a =>
        val here = live.get(alias(a)).orElse(old.get(alias(a))).filterNot(in.tombstoned)
        val other = here.exists(_ != owner(a))
        if other then notes += s"$what ${alias(a)}: names another $record here — the bundle's is left out"
        !other
      }
    val finalEs0 = es0.copy(
      sources = replaced(es0.sources, _.uid, sourceLine),
      findings = replaced(es0.findings, _.uid, findingLine),
      edges = edges0.filterNot(e => e.srcUid == e.dstUid).distinct,
      unresolvedEdges = (es0.unresolvedEdges ++ in.local.unresolvedEdges.filter(e => foldedHere(e.srcUid))).distinct,
      aliases = es0.aliases ++ newAliases,
      articles = replaced(es0.articles, _.uid, articleLine),
      assets = replaced(es0.assets, _.uid, assetLine),
      assetLinks = replaced(es0.assetLinks, _.uid, linkLine),
      attestations = replaced(es0.attestations, _.uid, attLine, t => !dropped(t.uid)),
      events = replaced(es0.events, _.uid, eventLine),
      goals = replaced(es0.goals, _.uid, goalLine).map { g =>
        // a goal here and there: this collection's schedules stay, the links of both
        localGoal.get(g.uid).filter(_ => !goalLine.contains(g.uid)).fold(g) { l =>
          val k = MergeFold.keepLocalSchedules(l, g)
          if k.links == g.links && k.schedules == g.schedules then g
          else g.copy(links = k.links, schedules = k.schedules, rev = "")
        }
      },
      queue = replaced(es0.queue, _.uid, queueLine),
      places = replaced(es0.places, _.uid, placeLine),
      periods = replaced(es0.periods, _.uid, periodLine),
      articleAliases = es0.articleAliases.filter { a =>
        val owner = in.labels.slugs.get(a.alias).orElse(in.labels.articleAliases.get(a.alias)).filterNot(in.tombstoned)
        val other = owner.exists(_ != a.articleUid)
        if other then notes += s"old slug ${a.alias}: names another article here — the bundle's is left out"
        !other
      },
      // old event ids, goal slugs and place ids: the same rule — never one that names another record here
      eventAliases = keptAliases(
        es0.eventAliases,
        _.alias,
        _.eventUid,
        in.labels.events,
        in.labels.eventAliases,
        "old event id",
        "event"
      ),
      goalAliases = keptAliases(
        es0.goalAliases,
        _.alias,
        _.goalUid,
        in.labels.goals,
        in.labels.goalAliases,
        "old goal slug",
        "goal"
      ),
      placeAliases = keptAliases(
        es0.placeAliases,
        _.alias,
        _.placeUid,
        in.labels.places,
        in.labels.placeAliases,
        "old place id",
        "place"
      )
    )
    val finalEs = relinkGoals(finalEs0, in)
    val finalRevs = finalEs.revs
    // a merged source whose own line is unchanged is still written when its edge set grows
    def edgeSet(es: List[BundleEdge], u: UUID) = es.filter(_.srcUid == u).toSet
    val edgesGrow: Set[UUID] = foldedHere.filter(u =>
      edgeSet(finalEs.edges, u) != edgeSet(in.local.edges, u) ||
        finalEs.unresolvedEdges.filter(_.srcUid == u).toSet != in.local.unresolvedEdges.filter(_.srcUid == u).toSet
    )

    // ── final row decisions ──
    val issueOfRow: Map[UUID, MergeIssue] = unconditional.values
      .filter(i =>
        i.kind == IssueKind.ChangedBothSides || i.kind == IssueKind.DeletedThereChangedHere ||
          i.kind == IssueKind.LabelTaken || i.kind == IssueKind.ArticleDiverged
      )
      .flatMap(i => i.right.map(_.uid).orElse(i.left.map(_.uid)).map(toLocal).map(_ -> i))
      .toMap
    val gateBoth: Set[UUID] = fes.sources
      .filter(s =>
        fates(s.uid) match
          case Fate.Gate(_, g) => gateDecision.get(g).exists(_.kind == km.Both)
          case _               => false
      )
      .map(_.uid)
      .toSet
    val folding: Set[UUID] = foldedSources.keySet.toSet
    val resolvedRows = initialRows.map { r0 =>
      val owner = ownerSource(r0.foreignUid, r0.kind)
      val uid = if r0.tombstone then r0.uid else foldUids.getOrElse(r0.foreignUid, r0.uid)
      val loc = if r0.tombstone then r0.localizedRev else finalRevs.get(uid).map(_.rev).getOrElse(r0.localizedRev)
      val r = r0.copy(
        uid = uid,
        localizedRev = loc,
        localRev = localRevs.get(uid).map(_.rev),
        label = if r0.tombstone then r0.label else finalRevs.get(uid).map(_.label).getOrElse(r0.label)
      )
      def foldDecision =
        if r.localRev.contains(loc) && !edgesGrow(uid) then Decision.Unchanged else Decision.FastForward
      if r0.tombstone then
        issueOfRow.get(r0.uid).flatMap(_.decision).map(_.kind) match
          case Some(km.KeepLeft)  => r.copy(decision = Decision.Kept)
          case Some(km.KeepRight) => r.copy(decision = Decision.Delete)
          case _                  => r
      else if dropped(r0.foreignUid) then r.copy(decision = Decision.Skip)
      else if overrides.contains(r0.foreignUid) then r.copy(decision = overrides(r0.foreignUid))
      else if foldUids.contains(r0.foreignUid) then r.copy(decision = foldDecision)
      else if r0.decision == Decision.Held && owner.exists(o => gateBoth(o) || folding(o)) then
        r.copy(decision = Decision.Insert)
      else if r0.decision == Decision.Conflict && merges.get(r0.foreignUid).exists(_._1 == uid) then
        r.copy(decision = foldDecision)
      else
        issueOfRow.get(r0.uid).flatMap(i => i.decision.map(i -> _)) match
          case Some((i, d)) =>
            (i.kind, d.kind) match
              case (IssueKind.ChangedBothSides, km.KeepLeft)  => r.copy(decision = Decision.Kept)
              case (IssueKind.ChangedBothSides, km.KeepRight) => r.copy(decision = Decision.FastForward)
              case (IssueKind.LabelTaken | IssueKind.ArticleDiverged, km.KeepLeft) => r.copy(decision = Decision.Skip)
              case _                                                               => r
          case None => r
    }

    // what needs a deleted (or skipped, or held) row is not written either
    val refs = finalEs.references
    val decisionOf0 = resolvedRows.map(r => r.uid -> r.decision).toMap
    val cascaded = BundlePlan.cascade(decisionOf0, refs, u => in.tombstoned(u) && !liveHere(u))
    val rows = resolvedRows.map(r => r.copy(decision = cascaded.getOrElse(r.uid, r.decision)))
    val decisionOfRow = rows.map(r => r.uid -> r.decision).toMap
    def writes(u: UUID): Boolean = decisionOfRow.get(u).exists(d => d == Decision.Insert || d == Decision.FastForward)
    val deletingNow = rows.filter(r => r.tombstone && r.decision == Decision.Delete).map(_.uid).toSet

    // references a written line makes
    val inBundleLive: UUID => Boolean = u =>
      decisionOfRow.get(u).exists {
        case Decision.Insert | Decision.FastForward | Decision.Unchanged | Decision.KeepLocal | Decision.Kept |
            Decision.Conflict =>
          true
        case _ => false
      }
    val written = refs.filter(r => writes(r.owner))
    val (_, missing) = written.partition(r => inBundleLive(r.uid) || (liveHere(r.uid) && !deletingNow(r.uid)))
    val (required, optional) = missing.partition(_.required)
    required
      .map(r => s"${r.from}: needs ${r.kind.wire} ${r.uid}, which is neither in the bundle nor here")
      .distinct
      .foreach(fatal += _)
    optional
      .map(r => s"${r.from}: ${r.kind.wire} ${r.uid} is not here — that link is left out")
      .distinct
      .foreach(notes += _)
    // gazetteer places
    val placesComing = finalEs.places.filter(p => inBundleLive(p.uid)).map(_.id).toSet ++ in.local.places.map(_.id)
    finalEs.events
      .filter(e => writes(e.uid))
      .flatMap(e => e.placeId.map(e.id -> _))
      .filterNot((_, p) => in.places(p) || placesComing(p))
      .foreach((e, p) =>
        fatal += s"event $e: place '$p' is not in this gazetteer — run `histmersion init` (or import that place) first"
      )
    // two bundle lines onto one row here
    val ownUids = rawRevs.keySet ++ fes.tombstones.map(_.uid)
    mapped.toList
      .filter((f, _) => ownUids(f))
      .groupBy(_._2)
      .filter(_._2.size > 1)
      .keys
      .toList
      .map(_.toString)
      .sorted
      .foreach(l => fatal += s"several records of the bundle map onto $l here")

    rows
      .filter(_.decision == Decision.SkipDeleted)
      .foreach(r => notes += s"${r.kind.wire} ${r.label} (${r.uid}): deleted here — not brought back")
    if in.reference != in.manifest.reference then
      notes += s"the gazetteer/periods differ from the bundle's (bundle: ${in.manifest.reference.places} places, " +
        s"${in.manifest.reference.periods} periods) — events point into them by id"
    if own then notes += "this instance's own bundle: rows changed here since are kept"
    // old citekeys of the bundle that were dropped here AFTER the bundle's alias was made (#827)
    val droppedAliases = fes.aliases.filter(a => in.aliasTombstones.get(a.alias).exists(a.createdAt <= _))
    droppedAliases.foreach(a =>
      notes += s"old citekey ${a.alias}: dropped here (renamed back) on ${in.aliasTombstones(a.alias)} — not brought back"
    )

    // ── what an import cannot do (it needs merge apply) ──
    val needsApply = List.newBuilder[String]
    fes.sources.foreach { s =>
      fates(s.uid) match
        case Fate.Auto(p) =>
          needsApply += s"source ${s.citekey}: the same work as ${p.left.source.citekey} here " +
            s"(${
                if p.evidence.strongKey then p.evidence.strong.mkString(", ")
                else s"score ${SourceMatcher.signed(p.evidence.score)}"
              })" +
            " — folding the two into one is `merge apply`'s"
        case Fate.Gate(p, g) if gateDecision.get(g).exists(_.kind == km.Map) =>
          needsApply += s"source ${s.citekey}: $g decided ${gateDecision(g).wire} — folding it into ${p.left.source.citekey} is `merge apply`'s"
        case _ => ()
    }
    merges.foreach { case (f, (o, how)) =>
      val kind = rawRevs.get(f).fold("record")(_.kind.wire)
      val label = finalRevs.get(o).orElse(localRevs.get(o)).fold(o.toString)(_.label)
      how match
        case Merges.ArticleKeep | Merges.ArticleTheirs if f == o => ()
        case _                                                   =>
          needsApply += s"$kind $label: the bundle's and this collection's are merged into one — that is `merge apply`'s"
    }
    if renameRight.nonEmpty || renames.result().nonEmpty then
      needsApply += "citekeys given to resolve a collision (rename-left, rename-right) — that is `merge apply`'s"
    relabelled.foreach(x => needsApply += s"$x: a label taken here, renumbered — that is `merge apply`'s")
    issues.filter(_.condition.isEmpty).foreach { i =>
      i.decision.foreach { dec =>
        val pairIssue = i.kind match
          case IssueKind.DoiDiffers | IssueKind.YearDiffers | IssueKind.StatusDiffers | IssueKind.TitleDiffers |
              IssueKind.AuthorsDiffers | IssueKind.SummaryDiffers | IssueKind.NotesDiffers | IssueKind.PeriodDiffers |
              IssueKind.BodyDiffers | IssueKind.SameWork | IssueKind.FindingNearDuplicate |
              IssueKind.AttestationUnanchored =>
            true
          case _ => false
        if !pairIssue && !byId(i.id).importable(dec.kind) then
          needsApply += s"${i.id} = ${dec.wire}: carrying that out is `merge apply`'s"
      }
    }

    // ── counts and info ──
    val counts = fes.sources.foldLeft(MatchCounts(sources = fes.sources.size)) { (c, s) =>
      fates(s.uid) match
        case Fate.Same(_, false)                  => c.copy(byUid = c.byUid + 1)
        case Fate.Same(_, true)                   => c.copy(byLedger = c.byLedger + 1)
        case Fate.Deleted                         => c.copy(deletedHere = c.deletedHere + 1)
        case Fate.Auto(p) if p.evidence.strongKey => c.copy(byStrongKey = c.byStrongKey + 1)
        case Fate.Auto(_)                         => c.copy(byScore = c.byScore + 1)
        case Fate.Gate(_, _)                      => c.copy(undecided = c.undecided + 1)
        case Fate.New                             => c.copy(unmatched = c.unmatched + 1)
    }
    val matches: List[SourceMatch] = fes.sources.sortBy(_.citekey).flatMap { s =>
      fates(s.uid) match
        case Fate.Same(_, _) => None
        case Fate.Deleted    =>
          Some(SourceMatch(s.uid, s.citekey, None, None, MatchMethod.Unmatched, MatchOutcome.DeletedHere))
        case Fate.New     => Some(SourceMatch(s.uid, s.citekey, None, None, MatchMethod.Unmatched, MatchOutcome.New))
        case Fate.Auto(p) =>
          Some(
            SourceMatch(
              s.uid,
              s.citekey,
              Some(p.left.uid),
              Some(p.left.source.citekey),
              if p.evidence.strongKey then MatchMethod.StrongKey else MatchMethod.Score,
              MatchOutcome.AutoMerge,
              Some(p.evidence.score),
              p.evidence.why
            )
          )
        case Fate.Gate(p, g) =>
          Some(
            SourceMatch(
              s.uid,
              s.citekey,
              Some(p.left.uid),
              Some(p.left.source.citekey),
              MatchMethod.Score,
              MatchOutcome.NeedsDecision,
              Some(p.evidence.score),
              p.evidence.why,
              Some(g)
            )
          )
    }
    val held =
      rows.filter(r => r.decision == Decision.Held && r.kind != BundleKind.Source).groupMapReduce(_.kind)(_ => 1)(_ + _)
    if held.nonEmpty then
      info += s"${held.toList.sortBy(_._1.ordinal).map((k, n) => Plural.count(n, k.wire)).mkString(", ")} belong to " +
        "sources still to be decided: they are matched inside the pair once it is merged"
    merges.foreach { case (f, (o, how)) =>
      val label = finalRevs.get(o).orElse(localRevs.get(o)).fold(o.toString)(_.label)
      how match
        case Merges.ArticleKeep =>
          info += s"article $label: this collection's text already contains the bundle's — kept"
        case Merges.ArticleTheirs =>
          info += s"article $label: the bundle's text continues this collection's — taken (fast-forward)"
        case Merges.ArticleMerged(_) =>
          info += s"article $label: changed on both sides, merged three-way without a conflict — queued for review"
        case Merges.GoalFold =>
          info += s"goal $label: the same goal on both sides — the status further along, notes joined"
        case Merges.QueueFold =>
          info += s"queue item «${MergeIssueDetail.preview(label, 50)}»: the same item on both sides — one item, done if done anywhere"
        case Merges.EventFold =>
          info += s"event $label: the same dating and place on both sides — one event, datings and participants unioned"
        case Merges.EventAcross =>
          info += s"event $label: the bundle's ${rawRevs.get(f).fold("")(_.label)} has the same label, dates and place — one event"
        case Merges.PlaceFold =>
          info += s"place $label: the bundle's ${rawRevs.get(f).fold("")(_.label)} is the same place — one place, names unioned"
        case Merges.PeriodFold =>
          info += s"named period $label: the bundle's ${rawRevs.get(f).fold("")(_.label)} is the same period — this collection's kept"
    }
    val problems = (in.compat ++ in.readProblems ++ fatal.result()).distinct

    val apply = Apply(
      folds = foldMap.toList.map((f, v) => Fold(v._2, f, v._1)).sortBy(f => (f.kind.ordinal, f.foreignUid.toString)),
      renames = renames.result(),
      snapshots = snapshots.toSet,
      versions = versions.result(),
      copies = copies.result().map(_(es0)),
      attestations = attFixes.result(),
      tombstones =
        dropped.toList.flatMap(u => fes.attestations.find(_.uid == u).map(t => (u, BundleKind.Attestation, t.origin))),
      bodyRenames = bodyRenames.toMap,
      reviews = reviews.toList,
      remember = issues
        .filter(i => i.condition.isEmpty && i.decidedBy.exists(b => b == DecidedBy.Resolution || b == DecidedBy.Policy))
        .flatMap(i => i.decision.map(i.id -> _)),
      touch = (foldMap.values.map(_._1) ++ merges.values.map(_._1) ++ articleLine.keys).toSet,
      tally = tally.copy(redirects = foldMap.size)
    )
    val changes = rows.count(_.decision.writes) + apply.folds.size + apply.renames.size + apply.attestations.size +
      apply.tombstones.size + apply.copies.size

    val sorted = issues.sortBy(i => (i.severity.ordinal, i.condition.isDefined, i.entity.ordinal, i.id))
    val hash = BundleFormat
      .sha256Hex(
        (
          List(in.manifest.bundleId.toString) ++
            matches.map(m => s"${m.rightUid}>${m.leftUid.fold("")(_.toString)}:${MatchOutcome.wireOf(m.outcome)}") ++
            sorted.map(i => s"${i.id}=${i.decision.fold("")(_.wire)}") ++ problems ++
            rows.map(r => s"${r.foreignUid}>${r.uid}:${r.decision}:${r.localizedRev}:${r.localRev.getOrElse("")}") ++
            (apply.renames.map(_.toString) ++ apply.attestations.map(_.toString) ++ apply.folds.map(_.toString)).sorted
        ).mkString("\n")
      )
      .take(8)
    val usedResolutions = chosen.keySet.intersect(liveIds).size

    val plan = MergePlan(
      in.manifest.bundleId,
      in.manifest.instance,
      in.here,
      in.manifest.formatVersion,
      in.manifest.schemaVersion,
      in.manifest.asOf,
      hash,
      counts,
      matches,
      sorted,
      info.result() ++ notes.result(),
      problems,
      errors.result().distinct,
      usedResolutions,
      changes
    )
    val aliasOf = aliasIssues.result()
    Result(
      plan,
      finalEs,
      rows,
      needsApply.result().distinct,
      issues
        .filter(i =>
          i.kind == IssueKind.AliasConflict && i.condition.isEmpty && i.decision.exists(_.kind == km.KeepLeft)
        )
        .flatMap(i => aliasOf.get(i.id))
        .toSet ++ droppedAliases.map(_.alias),
      notes.result(),
      apply
    )

  /** Both texts present and different (after trimming). */
  private def text(a: Option[String], b: Option[String]): Option[Unit] =
    a.map(_.trim).filter(_.nonEmpty).zip(b.map(_.trim).filter(_.nonEmpty)).filter((x, y) => x != y).map(_ => ())

  /** A new citekey for a bundle source whose own is taken: its key plus the next significant title word
    * (`smith2019_ritual` → `smith2019_ritualkingship`), else a letter (`smith2019_ritualb`). Never one in use here or
    * in the bundle. Pure.
    */
  def renameSuggestion(s: BundleSource, in: Input, fes: BundleEntities): String =
    val usedHere = in.labels.citekeys.keySet ++ in.labels.aliases.keySet ++ fes.sources.map(_.citekey) ++
      fes.aliases.map(_.alias)
    val suffix = s.citekey.dropWhile(_ != '_').drop(1)
    val words = MergeText
      .significant(MergeText.tokens(s.title))
      .map(_.filter(c => c.isLetterOrDigit && c < 128))
      .filter(w => w.nonEmpty && !suffix.contains(w))
    val byWord = words.headOption.map(w => s.citekey + w).filterNot(usedHere)
    byWord.getOrElse(Iterator.from(0).map(i => s.citekey + ('b' + i).toChar).find(c => !usedHere(c)).get)

/** Citekey shape for a rename. */
object Citekeys:
  private val Shape = "^[a-z][a-z0-9_]*$".r
  def valid(ck: String): Boolean = Shape.matches(ck)
