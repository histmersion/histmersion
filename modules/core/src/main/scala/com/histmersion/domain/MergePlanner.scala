package com.histmersion
package domain
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.JsonObject
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.problem.Text.sentence
import com.histmersion.api.exchange.MergeIssue
import com.histmersion.api.exchange.MergeDecision

import com.histmersion.util.data.exchange.BundleFormat
import com.histmersion.util.data.exchange.BundlePlan
import com.histmersion.util.data.exchange.Diff3
import com.histmersion.util.data.exchange.PairQuestions
import com.histmersion.util.data.exchange.PlanFolds
import com.histmersion.util.data.exchange.PlanMerge
import com.histmersion.util.data.exchange.PlanQuestions
import com.histmersion.util.data.exchange.PlanRelabels
import com.histmersion.util.data.exchange.PlanRules
import com.histmersion.util.data.exchange.PlanRuns
import com.histmersion.util.data.exchange.SourceFate
import com.histmersion.util.data.exchange.SourcePair
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.Plural
import com.histmersion.domain.catalogue.evidence.TextAnchor
import com.histmersion.api.exchange.{
  IssueCondition,
  IssueDetail,
  IssueField,
  IssueHunk,
  IssueSide,
  IssueText,
  MatchCounts,
  MergeDecision,
  MergeIssue,
  MergePlan,
  MergeResolution,
  MergeResolutions,
  MergeTally
}
import com.histmersion.api.automation.Queue
import com.histmersion.api.library.Citekey
import com.histmersion.Sha256
import com.histmersion.api.given

import BundlePlan.{Decision, Ledger, Local}
import MergeFold.ArticleOutcome
import com.histmersion.util.ocr.Chunk
import com.histmersion.util.text.Anchor
import com.histmersion.util.text.MinHash
import com.histmersion.api.text.SafeName

import java.util.UUID
import com.histmersion.api.exchange.EditionsPolicy
import com.histmersion.api.library.MatchOutcome
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.api.exchange.Bundle
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.exchange.PlanMatches

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
  *   2. **sources**: the rest are matched by the sources' rules (the library's, [[PlanRun.matchSources]]): auto-merge,
  *      needs-decision (a [[IssueKind.SameWork]] issue), or new — a new one whose citekey is taken here is a
  *      [[IssueKind.CitekeyCollision]];
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
  private given CanEqual[UUID, UUID] = CanEqual.derived

  type IssueExtra = IssueExtra.T
  object IssueExtra extends Newtype[String, Pure]

  /** One row of `merge_ledger`: the foreign rev merged, the local uid it maps to, the local rev left. */

  final case class LedgerEntry(
      foreignRev: Ledger.ForeignRev,
      localUid: UUID,
      localRevAfter: Option[Ledger.LocalRevAfter]
  )

  /** Who has which label here: citekeys and old citekeys (aliases) → source uid; finding ids, article slugs, event ids,
    * goal slugs → uid; (source uid, asset name) → asset uid.
    */
  final case class Labels(
      citekeys: Map[Citekey, UUID] = Map.empty,
      aliases: Map[Citekey, UUID] = Map.empty,
      findings: Map[Bundle.Finding.Id, UUID] = Map.empty,
      slugs: Map[Bundle.Article.Slug, UUID] = Map.empty,
      events: Map[Bundle.Event.Id, UUID] = Map.empty,
      goals: Map[Bundle.Goal.Slug, UUID] = Map.empty,
      assets: Map[(UUID, Bundle.Asset.Name), UUID] = Map.empty,
      /** Old article slugs here (`article_aliases`) → the article's uid. */
      articleAliases: Map[Bundle.ArticleAlias.Alias, UUID] = Map.empty,
      /** Old event ids and goal slugs here (V053) → the record's uid. */
      eventAliases: Map[Bundle.EventAlias.Alias, UUID] = Map.empty,
      goalAliases: Map[Bundle.GoalAlias.Alias, UUID] = Map.empty,
      /** Place ids here among the names the bundle uses (its places' ids and old ids) → uid, and the old place ids here
        * (V055) → the place's uid.
        */
      places: Map[Bundle.Place.Id, UUID] = Map.empty,
      placeAliases: Map[Bundle.PlaceAlias.Alias, UUID] = Map.empty
  )

  final case class Input(
      manifest: Bundle.Manifest,
      here: Bundle.Instance,
      /** Format and schema against this server (`BundleFormat.compatible`). */
      compat: List[Input.Compat],
      /** What reading the bundle found wrong (bad lines, checksums, missing blobs). */
      readProblems: List[Input.ReadProblems],
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
      sources: List[Bundle.Source],
      labels: Labels,
      /** Uids alive here, among those the bundle's lines refer to. */
      live: Set[UUID],
      /** Gazetteer ids present here, among those the bundle's events name. */
      places: Set[Input.Places],
      reference: Bundle.Reference,
      /** Old citekeys dropped here by renaming back (V050), with when — ISO-8601 UTC, as bundle timestamps are written,
        * so the two compare as strings.
        */
      aliasTombstones: Map[Citekey, Input.AliasTombstonesValue] = Map.empty,
      /** OCR bodies (sha256 → text) of the pairs whose attestations must be re-anchored ([[bodiesWanted]]).
        */
      bodies: Map[Bundle.Source.BodySha256, TextAnchor.Text] = Map.empty,
      /** This collection's recorded versions of its articles (article uid → (sha256, body)), newest first: where a
        * common base for a three-way merge is looked for.
        */
      articleHistory: Map[UUID, List[(Sha256, Bundle.Article.Body)]] = Map.empty,
      /** Decisions an earlier `merge apply` carried out, by issue id (`merge_decisions`). */
      remembered: Map[MergeIssue.Id, MergeDecision] = Map.empty,
      /** `limits.chunk-chars`: to place a re-anchored attestation in its FTS chunk (0 leaves it unplaced). */
      chunkChars: TextAnchor.ChunkChars = TextAnchor.ChunkChars(0),
      /** A bundle place (by uid) whose id is not here, but whose label and geometry name a place here (the database
        * compares the geometries): that place's id.
        */
      placeMatches: Map[UUID, Bundle.Place.Id] = Map.empty,
      /** Likewise for a named period: the same label and an overlapping span. */
      periodMatches: Map[UUID, Bundle.Period.Id] = Map.empty
  ):
    def ownBundle: Boolean = manifest.instance.id == here.id

    /** How the other collection is named in what the merge writes ("work"). */
    def fromLabel: Row.Label =
      Row.Label(
        manifest.instance.label.map(_.value.trim).filter(_.nonEmpty).getOrElse(manifest.instance.id.toString.take(8))
      )

  object Input:

    type Compat = Compat.T
    object Compat extends Newtype[String, Pure]
    type ReadProblems = ReadProblems.T
    object ReadProblems extends Newtype[String, Pure]
    type Places = Places.T
    object Places extends Newtype[String, Pure]
    type AliasTombstonesValue = AliasTombstonesValue.T
    object AliasTombstonesValue extends Newtype[String, Pure]

  /** A line's fate, for the importer: its uid in the bundle and here, its revs, the decision. */
  final case class Row(
      kind: Bundle.Kind,
      foreignUid: UUID,
      uid: UUID,
      label: Row.Label,
      foreignRev: Ledger.ForeignRev,
      localizedRev: Local.Rev,
      localRev: Option[Local.Rev],
      decision: Decision,
      tombstone: Row.Tombstone
  )

  object Row:
    type Label = Label.T
    object Label extends Newtype[String, Pure]
    type Tombstone = Tombstone.T
    object Tombstone extends Newtype[Boolean, Pure]

  /** A record of the bundle folded into one here: its uid becomes a redirect (`uid_aliases`). */
  final case class Fold(kind: Bundle.Kind, foreignUid: UUID, localUid: UUID)

  /** One of THIS collection's attestations, moved because the merge keeps the bundle's body: its new place, or `None`
    * (kept without a place — `keep-unanchored`); `drop` deletes it.
    */
  final case class AttestationFix(
      uid: UUID,
      anchor: Option[Anchor],
      chunkOrd: Option[Chunk.Ord],
      drop: AttestationFix.Drop
  )

  object AttestationFix:
    type Drop = Drop.T
    object Drop extends Newtype[Boolean, Pure]

  /** A version to record for an article here: the bundle's text, so the next merge finds it in this collection's
    * history (a common base).
    */
  final case class ExtraVersion(
      articleUid: UUID,
      body: Bundle.Article.Body,
      summary: Option[Bundle.Article.Summary],
      versionOf: Bundle.Article.UpdatedAt
  )

  /** What `merge apply` does besides writing the decided lines. */
  final case class Apply(
      folds: List[Fold] = Nil,
      /** Sources here given another citekey first (`rename-left`): uid, old, new. */
      renames: List[(UUID, Citekey, Citekey)] = Nil,
      /** Articles here whose current text is recorded as a version before the merge replaces it. */
      snapshots: Set[UUID] = Set.empty,
      versions: List[ExtraVersion] = Nil,
      /** Articles added beside one here (`both` on an article changed on both sides): a new record. */
      copies: List[Bundle.Article] = Nil,
      attestations: List[AttestationFix] = Nil,
      /** Bundle records left out for good (an attestation dropped): recorded as deleted here, so the next plan does not
        * bring them back. (uid, kind, origin.)
        */
      tombstones: List[(UUID, Bundle.Kind, UUID)] = Nil,
      /** Asset references to rewrite in a body the merge takes from the bundle: source uid → (old name, new name), for
        * its figures renamed because the name was taken.
        */
      bodyRenames: Map[UUID, List[(Bundle.Asset.Name, Bundle.Asset.Name)]] = Map.empty,
      /** Articles (their slugs as they will be) to queue for `/review-article`. */
      reviews: List[Bundle.Article.Slug] = Nil,
      /** The decisions to remember for the next plan (an issue id → its decision). */
      remember: List[(MergeIssue.Id, MergeDecision)] = Nil,
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
      needsApply: List[Result.NeedsApply],
      /** Old citekeys of the bundle not to import (a resolved alias conflict, or one dropped here). */
      skipAliases: Set[Citekey],
      /** Optional links the import leaves out, and other notes for the import report. */
      notes: List[Result.Notes],
      apply: Apply = Apply()
  )

  object Result:
    type NeedsApply = NeedsApply.T
    object NeedsApply extends Newtype[String, Pure]
    type Notes = Notes.T
    object Notes extends Newtype[String, Pure]

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
  def localize(es: BundleEntities, mapped: Map[UUID, UUID], localLabel: Map[UUID, Row.Label]): BundleEntities =
    if mapped.isEmpty then es
    else
      val m = mapped.map((f, l) => f.toString -> l.toString)
      def go[A](as: List[A], labelField: Option[String])(using JsonValueCodec[A]): List[A] =
        as.map { a =>
          val obj = a.asJson.asObject.getOrElse(JsonObject.empty)
          val own = obj("uid").flatMap(_.asString).flatMap(u => scala.util.Try(UUID.fromString(u)).toOption)
          val relabelled = (for
            f <- own
            l <- mapped.get(f)
            field <- labelField
            lab <- localLabel.get(l)
          yield obj.updated(field, Json.fromString(lab.value))).getOrElse(obj)
          mapJson(m)(Json.fromJsonObject(relabelled)).as[A].getOrElse(a)
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
  def letter(kind: MergeIssue.Kind, entity: Bundle.Kind): com.histmersion.util.data.exchange.Bundle.Kind.IssueLetter =
    kind match
      case MergeIssue.Kind.BodyDiffers   => com.histmersion.util.data.exchange.Bundle.Kind.IssueLetter("O")
      case MergeIssue.Kind.AliasConflict => com.histmersion.util.data.exchange.Bundle.Kind.IssueLetter("L")
      case _ => com.histmersion.util.data.exchange.Bundle.Kind.IssueLetter(entity.issueLetter)

  /** `<letter>-<8 hex>`: sha256 of the kind, the sorted bundle uids, the sorted local uids, the two revs and anything
    * else that tells the issue apart (an alias). The same records unchanged → the same id; either side changed → a new
    * id, and the old resolution goes stale. Pure.
    */
  def issueId(
      kind: MergeIssue.Kind,
      entity: Bundle.Kind,
      right: List[UUID],
      left: List[UUID],
      rightRev: Ledger.ForeignRev,
      leftRev: Local.Rev,
      extra: IssueExtra = IssueExtra("")
  ): MergeIssue.Id = issueIdOf(kind, entity, right, left, rightRev.value, leftRev.value, extra)

  /** As above for an issue about a body: the body kept (its sha256, or none) stands where the local rev does. */
  def issueId(
      kind: MergeIssue.Kind,
      entity: Bundle.Kind,
      right: List[UUID],
      left: List[UUID],
      rightRev: Ledger.ForeignRev,
      keptBody: Option[Bundle.Source.BodySha256],
      extra: IssueExtra
  ): MergeIssue.Id = issueIdOf(kind, entity, right, left, rightRev.value, keptBody.fold("")(_.value), extra)

  private def issueIdOf(
      kind: MergeIssue.Kind,
      entity: Bundle.Kind,
      right: List[UUID],
      left: List[UUID],
      rightRev: String,
      leftRev: String,
      extra: IssueExtra
  ): MergeIssue.Id =
    val text = List(
      MergeIssue.Kind.wireOf(kind).value,
      right.map(_.toString).sorted.mkString(","),
      left.map(_.toString).sorted.mkString(","),
      rightRev,
      leftRev,
      extra.value
    ).mkString("\u0000")
    MergeIssue.Id(s"${letter(kind, entity).value}-${BundleFormat.sha256Hex(text).value.take(8)}")

  /** Two versions of an article, with the three-way merge's regions over their common version (or the whole text as one
    * region when there is none).
    */
  private[domain] def articleText(
      base: Option[Bundle.Article.Body],
      left: Bundle.Article.Body,
      right: Bundle.Article.Body
  ): IssueText =
    val hunks = base match
      case Some(b) =>
        Diff3.merge(Diff3.Text(b.value), Diff3.Text(left.value), Diff3.Text(right.value)).hunks.map {
          case Diff3.Hunk.Agreed(ls) =>
            IssueHunk(conflict = IssueHunk.Conflict(false), left = ls.toList.map(l => IssueHunk.Left(l.value)))
          case Diff3.Hunk.Conflict(o, a, c) =>
            IssueHunk(
              conflict = IssueHunk.Conflict(true),
              o.toList.map(x => IssueHunk.Base(x.value)),
              a.toList.map(x => IssueHunk.Left(x.value)),
              c.toList.map(x => IssueHunk.Right(x.value))
            )
        }
      case None =>
        List(
          IssueHunk(
            conflict = IssueHunk.Conflict(true),
            Nil,
            left.value.split("\n", -1).toList.map(IssueHunk.Left(_)),
            right.value.split("\n", -1).toList.map(IssueHunk.Right(_))
          )
        )
    IssueText(base.map(b => IssueText.Base(b.value)), IssueText.Left(left.value), IssueText.Right(right.value), hunks)

  /** A goal's links name what they link by label; the label of a linked source or article is the one it has here once
    * the merge is done (a merged source's survivor citekey, a renamed article).
    */
  private def relinkGoals(es: BundleEntities, in: Input): BundleEntities =
    val citekeys = in.sources.map(s => s.uid -> s.citekey).toMap ++ es.sources.map(s => s.uid -> s.citekey)
    val slugs = in.local.articles.map(a => a.uid -> a.slug).toMap ++ es.articles.map(a => a.uid -> a.slug)
    def label(k: Bundle.GoalLink): Option[Bundle.GoalLink.Ref] = k.kind.value match
      case "source"  => k.refUid.flatMap(citekeys.get).map(c => Bundle.GoalLink.Ref(c.value))
      case "article" => k.refUid.flatMap(slugs.get).map(s => Bundle.GoalLink.Ref(s.value))
      case _         => None
    es.modify(_.goals)
      .setTo(es.goals.map { g =>
        val links = g.links.map(_.map(k => label(k).filter(_ != k.ref).fold(k)(r => k.copy(ref = r))))
        if links == g.links then g else g.modify(_.links).setTo(links).modify(_.rev).setTo(Bundle.Goal.Rev(""))
      })

  // ── sketches and bodies ─────────────────────────────────────────────────────────────────────

  /** The OCR bodies (by sha256) whose MinHash could decide whether two sources are one, as the kinds' rules say: the
    * database computes them before [[plan]].
    */
  def sketchesWanted(in: Input, cfg: MergeSettings, rules: List[PlanRules]): Set[Bundle.Source.BodySha256] =
    PlanRuns(rules.map(_.start(in))).sketchesWanted(cfg)

  /** The OCR bodies (by sha256) the plan must read for the kinds' rules (re-anchoring attestations): of the matched
    * pairs, as each kind's rules say.
    */
  def bodiesWanted(
      in: Input,
      sketches: Map[Bundle.Source.BodySha256, MinHash],
      resolutions: MergeResolutions,
      cfg: MergeSettings,
      rules: List[PlanRules]
  ): Set[Bundle.Source.BodySha256] =
    val run = PlanRuns(rules.map(_.start(in)))
    val pairs = run.matchSources(sketches, cfg, resolutions.policy.editions)
    run.bodiesWanted(pairs.map(p => (p.right, p.left)))

  // ── the plan ────────────────────────────────────────────────────────────────────────────────

  /** An issue before resolutions, with the decision kinds an import can carry out itself. */
  final private case class Draft(issue: MergeIssue, importable: Set[MergeDecision.Kind])

  /** Whether an issue applies, given the decisions of the issues it is conditional on. */
  private enum Standing derives CanEqual:
    case Live, Conditional, Dropped

  /** What M4 merges by itself, decided before the resolutions. */
  private enum Merges derives CanEqual:
    /** This collection's article already contains the bundle's text: ours stays. */
    case ArticleKeep

    /** The bundle's article continues ours: fast-forward. */
    case ArticleTheirs

    /** Both changed since a common version, merged three-way without a conflict. */
    case ArticleMerged(body: Bundle.Article.Body)
    case GoalFold, QueueFold

    /** A record a kind's rules found to be one with a record here. */
    case Contributed(how: PlanMerge)

  def plan(
      in: Input,
      sketches: Map[Bundle.Source.BodySha256, MinHash],
      resolutions: MergeResolutions,
      cfg: MergeSettings,
      rules: List[PlanRules]
  ): Result =
    val run = PlanRuns(rules.map(_.start(in)))
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
    // what the kinds' rules point at the records here (a place the bundle renamed, one matched under another id)
    val localized =
      run.localized(localize(fes, mapped, localRevs.view.mapValues(x => Row.Label(x.label)).toMap), mapped)
    val locRevs = localized.revs
    val toLocal: UUID => UUID = u => mapped.getOrElse(u, u)
    val own = in.ownBundle
    val policy = resolutions.policy
    val from = in.fromLabel
    def liveHere(u: UUID): Boolean = in.live(u) || (localRevs.contains(u) && !in.tombstoned(u))

    // ── 2. sources ──
    val matched: List[SourcePair] = run.matchSources(sketches, cfg, policy.editions)
    val sourcesByUid = in.sources.map(s => s.uid -> s).toMap
    def rightRev(u: UUID): Ledger.ForeignRev = Ledger.ForeignRev(rawRevs.get(u).map(_.rev).getOrElse(""))
    def localRev(u: UUID): Local.Rev = Local.Rev(localRevs.get(u).map(_.rev).getOrElse(""))
    def gateId(g: SourceFate.Gate.Issue): MergeIssue.Id = MergeIssue.Id(g.value)

    val fates: Map[UUID, SourceFate] = fes.sources.map { s =>
      val l = idOf(s.uid)
      s.uid -> (if in.tombstoned(l) && !localRevs.contains(l) then SourceFate.Deleted
                else if localRevs.contains(l) then SourceFate.Same(l, SourceFate.Same.ByLedger(l != s.uid))
                else
                  matched.find(_.right.uid == s.uid) match
                    case Some(p) =>
                      p.gate.fold(SourceFate.Auto(p))(g => SourceFate.Gate(p, SourceFate.Gate.Issue(g.value)))
                    case None => SourceFate.New)
    }.toMap
    val pairs: List[(SourcePair, Option[IssueCondition])] = fes.sources.flatMap(s =>
      fates(s.uid) match
        case SourceFate.Auto(p)    => Some(p -> None)
        case SourceFate.Gate(p, g) => Some(p -> Some(IssueCondition(gateId(g), MergeDecision.Kind.Map)))
        case _                     => None
    )

    // ── the drafts: every issue, conditional ones included ──
    val drafts = List.newBuilder[Draft]
    def add(d: Draft): Unit = drafts += d
    val km = MergeDecision.Kind
    val lr = List(km.KeepLeft, km.KeepRight)
    val info = List.newBuilder[String]
    run.notices.foreach(info += _.value)
    val noted = info
    val pairQuestions = new PairQuestions:
      def input: Input = in
      def rightRev(u: UUID): Ledger.ForeignRev = Ledger.ForeignRev(rawRevs.get(u).map(_.rev).getOrElse(""))
      def localRev(u: UUID): Local.Rev = Local.Rev(localRevs.get(u).map(_.rev).getOrElse(""))
      def here(u: UUID): Boolean = localRevs.contains(u)
      def toLocal(u: UUID): UUID = mapped.getOrElse(u, u)
      def ask(issue: MergeIssue, importable: Set[MergeDecision.Kind]): Unit = add(Draft(issue, importable))
      def info(line: PairQuestions.InfoLine): Unit = noted += line.value

    // whether a pair is one work, the fields of a pair, a citekey taken: as the sources' rules ask
    run.sourceQuestions(fates, sketches, cfg, pairQuestions)

    // old citekeys of the bundle that name a different source here (issue id → the alias)
    val aliasIssues = Map.newBuilder[MergeIssue.Id, Citekey]
    fes.aliases.foreach { a =>
      val owner = in.labels.aliases.get(a.alias).orElse(in.labels.citekeys.get(a.alias)).filterNot(in.tombstoned)
      owner.foreach { o =>
        val asCitekey = in.labels.citekeys.get(a.alias).contains(o)
        def conflict(target: UUID, cond: Option[IssueCondition]): Option[Draft] =
          Option.when(o != target) {
            val src = fes.sources.find(_.uid == a.sourceUid)
            val id =
              issueId(
                MergeIssue.Kind.AliasConflict,
                Bundle.Kind.Source,
                List(a.sourceUid),
                List(o),
                Ledger.ForeignRev(""),
                Local.Rev(""),
                IssueExtra(a.alias.value)
              )
            aliasIssues += id -> a.alias
            Draft(
              MergeIssue(
                id,
                MergeIssue.Kind.AliasConflict,
                MergeIssue.Severity.Blocking,
                Bundle.Kind.Source,
                MergeIssue.Title(
                  s"old citekey ${a.alias} names ${src.fold(a.sourceUid.toString)(_.citekey.value)} in the bundle, " +
                    s"but ${sourcesByUid.get(o).fold(o.toString)(_.citekey.value)} here" + (if asCitekey then
                                                                                              " (its citekey)"
                                                                                            else "")
                ),
                sourcesByUid.get(o).map(MergeIssue.Detail.side),
                src.map(MergeIssue.Detail.side),
                List(
                  Problem.Unclassified(
                    Legacy.Text("keep-left drops the bundle's old citekey; this collection's meaning stays")
                  )
                ),
                if asCitekey then List(km.KeepLeft) else lr,
                Some(MergeDecision.of(km.KeepLeft)),
                cond,
                detail = sourcesByUid.get(o).zip(src).map((x, y) => IssueDetail(MergeIssue.Detail.sourceFields(x, y)))
              ),
              Set(km.KeepLeft)
            )
          }
        fates.get(a.sourceUid) match
          case Some(SourceFate.Same(l, _)) => conflict(l, None).foreach(add)
          case Some(SourceFate.Auto(p))    => conflict(p.left.uid, None).foreach(add)
          case Some(SourceFate.Gate(p, g)) =>
            (
              conflict(p.left.uid, Some(IssueCondition(gateId(g), km.Map))),
              conflict(a.sourceUid, Some(IssueCondition(gateId(g), km.Both)))
            ) match
              case (Some(x), Some(_)) => add(x.modify(_.issue.condition).setTo(None))
              case (x, y)             => (x ++ y).foreach(add)
          case Some(SourceFate.New) | None => conflict(a.sourceUid, None).foreach(add)
          case Some(SourceFate.Deleted)    => ()
      }
    }

    // ── 4. what a pair owns (M4) ──
    pairs.foreach { (p, cond) =>
      val (r, l) = (p.right, p.left)
      // what the kinds own of the pair (the findings first), under each alternative of the body question
      run.pairFirst(r, l, cond, cfg, pairQuestions)
      run.pair(r, l, cond, run.bodyIssue(r.uid), pairQuestions)
    }

    // ── 5. rows ──
    val ledgerOf: UUID => Option[Ledger] = f =>
      if own then None else in.ledger.get(f).map(e => Ledger(e.foreignRev, e.localRevAfter))
    def localOf(f: UUID): Local =
      val l = toLocal(f)
      Local(localRevs.get(l).map(x => Local.Rev(x.rev)), Local.Tombstoned(in.tombstoned(l)), ledgerOf(f))
    // the source a line belongs to (its fate decides whether the line is held, or folded with it)
    def ownerSource(f: UUID, kind: Bundle.Kind): Option[UUID] = kind match
      case Bundle.Kind.Source => Some(f)
      case _                  => run.ownerSource(f, kind)
    def heldBy(f: UUID, kind: Bundle.Kind): Boolean =
      ownerSource(f, kind).flatMap(fates.get).exists {
        case SourceFate.Auto(_) | SourceFate.Gate(_, _) => true
        case _                                          => false
      }

    val initialRows: List[Row] =
      rawRevs.toList.sortBy((u, r) => (r.kind.ordinal, u.toString)).map { (f, r) =>
        val l = toLocal(f)
        val loc = locRevs.get(l).map(_.rev).getOrElse(r.rev)
        val lab = locRevs.get(l).map(_.label).getOrElse(r.label)
        val d =
          if heldBy(f, r.kind) && !localRevs.contains(l) then Decision.Held
          else BundlePlan.entity(Ledger.ForeignRev(r.rev), Local.Rev(loc), localOf(f), BundlePlan.EntityOwnBundle(own))
        Row(
          r.kind,
          f,
          l,
          Row.Label(lab),
          Ledger.ForeignRev(r.rev),
          Local.Rev(loc),
          localRevs.get(l).map(x => Local.Rev(x.rev)),
          d,
          tombstone = Row.Tombstone(false)
        )
      } ++ fes.tombstones.map { t =>
        val l = toLocal(t.uid)
        Row(
          t.kind,
          t.uid,
          l,
          Row.Label(localRevs.get(l).map(_.label).getOrElse(t.uid.toString)),
          Ledger.ForeignRev(BundlePlan.DeletedRev),
          Local.Rev(BundlePlan.DeletedRev),
          localRevs.get(l).map(x => Local.Rev(x.rev)),
          BundlePlan.tombstone(localOf(t.uid), BundlePlan.TombstoneOwnBundle(own)),
          tombstone = Row.Tombstone(true)
        )
      }
    val initialOf: Map[UUID, Row] = initialRows.filterNot(_.tombstone.value).map(r => r.foreignUid -> r).toMap

    // records here, by uid; the bundle's (localized) events by uid; the bundle's raw articles
    val localArticle = in.local.articles.map(a => a.uid -> a).toMap
    val localGoal = in.local.goals.map(g => g.uid -> g).toMap
    val localQueue = in.local.queue.map(q => q.uid -> q).toMap
    val rawArticle = fes.articles.map(a => a.uid -> a).toMap
    def foreignHistory(f: UUID): Set[Sha256] =
      fes.versions.filter(_.articleUid == f).map(v => Sha256.assume(v.bodySha256.value)).toSet
    def outcome(local: Bundle.Article, foreignUid: UUID): ArticleOutcome =
      MergeFold.articleOutcome(
        MergeFold.ArticleBody(local.body.value),
        in.articleHistory
          .getOrElse(local.uid, Nil)
          .map((sha, body) => (sha, MergeFold.ArticleBody(body.value))),
        MergeFold.ArticleBody(rawArticle(foreignUid).body.value),
        foreignHistory(foreignUid)
      )
    def baseOf(local: UUID, sha: Sha256): Option[Bundle.Article.Body] =
      in.articleHistory.getOrElse(local, Nil).find(_._1 == sha).map(_._2)

    /** The two records of a row question side by side, where the screen can show more than labels. */
    def rowDetail(kind: Bundle.Kind, u: UUID): Option[IssueDetail] = run.rowDetail(kind, u, localized)
    // a bundle uid → (the uid here, what M4 does)
    val merges = scala.collection.mutable.LinkedHashMap.empty[UUID, (UUID, Merges)]
    def articleMerge(f: UUID, o: UUID, oc: ArticleOutcome): Boolean = oc match
      case ArticleOutcome.Same | ArticleOutcome.Ours => merges(f) = o -> Merges.ArticleKeep; true
      case ArticleOutcome.Theirs                     => merges(f) = o -> Merges.ArticleTheirs; true
      case ArticleOutcome.Merged(b, _) => merges(f) = o -> Merges.ArticleMerged(Bundle.Article.Body(b.value)); true
      case _                           => false

    // changed on both sides; deleted there and changed here — unless M4 merges it
    initialRows.filter(_.decision == Decision.Conflict).foreach { r =>
      val left = Some(IssueSide(IssueSide.Label(r.label.value), r.uid))
      val right =
        Option.when(!r.tombstone.value)(
          IssueSide(IssueSide.Label(rawRevs.get(r.foreignUid).map(_.label).getOrElse(r.label.value)), r.foreignUid)
        )
      def conflictIssue(
          title: MergeIssue.Title,
          opts: List[MergeDecision.Kind],
          importable: Set[MergeDecision.Kind],
          detail: Option[IssueDetail] = None
      ): Unit =
        val kind =
          if r.tombstone.value then MergeIssue.Kind.DeletedThereChangedHere else MergeIssue.Kind.ChangedBothSides
        add(
          Draft(
            MergeIssue(
              issueId(kind, r.kind, List(r.foreignUid), List(r.uid), r.foreignRev, r.localRev.getOrElse(Local.Rev(""))),
              kind,
              MergeIssue.Severity.Blocking,
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
      if r.tombstone.value then
        conflictIssue(
          MergeIssue.Title("deleted in the bundle, changed here since the last import"),
          lr,
          Set(km.KeepLeft, km.KeepRight)
        )
      else
        r.kind match
          case Bundle.Kind.Article =>
            val oc = outcome(localArticle(r.uid), r.foreignUid)
            if !articleMerge(r.foreignUid, r.uid, oc) then
              oc match
                case ArticleOutcome.Conflict(n, sha) =>
                  conflictIssue(
                    MergeIssue.Title(
                      s"changed on both sides since a common version: ${Plural.count(Plural.Count(n.value.toLong), Plural.Noun.Singular("conflicting region"))}"
                    ),
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
                    MergeIssue.Title("changed on both sides since the last import (no common base kept)"),
                    article,
                    Set(km.KeepLeft, km.KeepRight),
                    Some(
                      IssueDetail(text =
                        Some(articleText(None, localArticle(r.uid).body, rawArticle(r.foreignUid).body))
                      )
                    )
                  )
          case Bundle.Kind.Goal      => merges(r.foreignUid) = r.uid -> Merges.GoalFold
          case Bundle.Kind.QueueItem => merges(r.foreignUid) = r.uid -> Merges.QueueFold
          case _                     =>
            run.conflict(r, localized) match
              case Some(how) => merges(r.foreignUid) = r.uid -> Merges.Contributed(how)
              case None      =>
                conflictIssue(
                  MergeIssue.Title("changed on both sides since the last import"),
                  lr,
                  Set(km.KeepLeft, km.KeepRight)
                )
    }

    // labels a different record has here — for the lines that would be written
    val deleting: Set[UUID] =
      initialRows.filter(r => r.tombstone.value && r.decision == Decision.Delete).map(_.uid).toSet
    val writesInitially: Set[UUID] =
      initialRows.filter(r => r.decision == Decision.Insert || r.decision == Decision.FastForward).map(_.uid).toSet
    def taken(owner: Option[UUID], self: UUID): Option[UUID] = owner.filter(o => o != self && !deleting(o))
    def bundleUidOf(u: UUID): UUID = mapped.collectFirst { case (k, v) if v == u => k }.getOrElse(u)
    val notes = List.newBuilder[String]
    val fatal = List.newBuilder[String]
    def labelTaken[A](
        kind: Bundle.Kind,
        rows: List[A],
        uid: A => UUID,
        label: A => String,
        owner: A => Option[UUID],
        what: String,
        options: List[MergeDecision.Kind] = lr,
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
                      MergeIssue.Kind.LabelTaken,
                      kind,
                      List(f),
                      List(o),
                      rightRev(f),
                      localRev(o)
                    ),
                    MergeIssue.Kind.LabelTaken,
                    MergeIssue.Severity.Blocking,
                    kind,
                    MergeIssue.Title(s"here that $what belongs to a different record"),
                    Some(IssueSide(IssueSide.Label(l), o)),
                    Some(IssueSide(IssueSide.Label(l), f)),
                    List(
                      Problem
                        .Unclassified(Legacy.Text("keep-left leaves the bundle's record out (and whatever needs it)"))
                    ) ++
                      Option
                        .when(options.contains(km.RenameRight))(
                          Problem.Unclassified(
                            Legacy.Text(
                              "keep-right makes them one, the bundle's fields winning; " +
                                "rename-right keeps both, the bundle's under a new id"
                            )
                          )
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
    // the names the kinds' rules see, under the planner's own
    val linesHere = localized
    val writtenBefore: UUID => Boolean = writesInitially
    def labelTakenOf[A](
        kind: Bundle.Kind,
        rows: List[A],
        uid: A => UUID,
        label: A => String,
        owner: A => Option[UUID],
        what: String,
        options: List[MergeDecision.Kind],
        merge: (A, UUID) => Option[Merges],
        detailOf: (A, UUID) => Option[IssueDetail]
    ): Unit = labelTaken(kind, rows, uid, label, owner, what, options, merge, detailOf)
    val questions = new PlanQuestions:
      def input: Input = in
      def bundle: BundleEntities = fes
      def localized: BundleEntities = linesHere
      def rightRev(u: UUID): Ledger.ForeignRev = pairQuestions.rightRev(u)
      def localRev(u: UUID): Local.Rev = pairQuestions.localRev(u)
      def here(u: UUID): Boolean = localRevs.contains(u)
      def inLocalized(u: UUID): Boolean = locRevs.contains(u)
      def toLocal(u: UUID): UUID = mapped.getOrElse(u, u)
      def bundleUidOf(u: UUID): UUID = mapped.collectFirst { case (k, v) if v == u => k }.getOrElse(u)
      def writesInitially(u: UUID): Boolean = writtenBefore(u)
      def merged(foreign: UUID): Boolean = merges.contains(foreign)
      def merge(foreign: UUID, here: UUID, how: PlanMerge): Unit = merges(foreign) = here -> Merges.Contributed(how)
      def ask(issue: MergeIssue, importable: Set[MergeDecision.Kind]): Unit = add(Draft(issue, importable))
      def info(line: PairQuestions.InfoLine): Unit = noted += line.value
      def labelTaken[A](
          kind: Bundle.Kind,
          rows: List[A],
          uid: A => UUID,
          label: A => PlanQuestions.LabelTakenLabel,
          owner: A => Option[UUID],
          what: PlanQuestions.LabelTakenWhat,
          options: List[MergeDecision.Kind],
          merge: (A, UUID) => Option[PlanMerge],
          detailOf: (A, UUID) => Option[IssueDetail]
      ): Unit =
        labelTakenOf(
          kind,
          rows,
          uid,
          a => label(a).value,
          owner,
          what.value,
          options,
          (a, o) => merge(a, o).map(Merges.Contributed(_)),
          detailOf
        )
    run.labelsTakenFirst(questions)
    run.labelsTaken(questions)
    labelTaken(
      Bundle.Kind.Goal,
      localized.goals,
      _.uid,
      _.slug.value,
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
    val bothSlugs = scala.collection.mutable.Map.empty[UUID, Bundle.Article.Slug]
    def bothSlug(f: UUID, slug: Bundle.Article.Slug): Bundle.Article.Slug =
      bothSlugs.getOrElseUpdate(
        f, {
          val tag = SafeName.of(from.value).value.toLowerCase(java.util.Locale.ROOT)
          val s = Iterator
            .from(1)
            .map(i => Bundle.Article.Slug(s"${slug.value}-$tag${if i == 1 then "" else s"-$i"}"))
            .find(s => !slugsUsed(s))
            .get
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
        def blocking(title: MergeIssue.Title, base: Option[Bundle.Article.Body]) =
          MergeIssue(
            issueId(MergeIssue.Kind.ArticleDiverged, Bundle.Kind.Article, List(f), List(o), rightRev(f), localRev(o)),
            MergeIssue.Kind.ArticleDiverged,
            MergeIssue.Severity.Blocking,
            Bundle.Kind.Article,
            title,
            Some(
              IssueSide(
                IssueSide.Label(a.slug.value),
                o,
                mine.map(m => IssueSide.Lines(s"\"${m.title}\" · ${m.body.value.length} characters")).toList
              )
            ),
            Some(
              IssueSide(
                IssueSide.Label(a.slug.value),
                f,
                List(IssueSide.Lines(s"\"${a.title}\" · ${a.body.value.length} characters"))
              )
            ),
            List(Problem.Unclassified(Legacy.Text(s"both: the bundle's becomes ${bothSlug(f, a.slug)}"))),
            List(km.KeepLeft, km.KeepRight, km.Both, km.File),
            detail = mine.map(m => IssueDetail(text = Some(articleText(base, m.body, rawArticle(f).body))))
          )
        if same then
          add(
            Draft(
              MergeIssue(
                issueId(
                  MergeIssue.Kind.ArticleDiverged,
                  Bundle.Kind.Article,
                  List(f),
                  List(o),
                  rightRev(f),
                  localRev(o)
                ),
                MergeIssue.Kind.ArticleDiverged,
                MergeIssue.Severity.Default,
                Bundle.Kind.Article,
                MergeIssue.Title("the same text was written on both sides"),
                Some(IssueSide(IssueSide.Label(a.slug.value), o)),
                Some(IssueSide(IssueSide.Label(a.slug.value), f)),
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
                      MergeIssue.Title(
                        s"one slug, two versions of the article: ${Plural.count(Plural.Count(n.value.toLong), Plural.Noun.Singular("conflicting region"))} " +
                          "against their common version"
                      ),
                      baseOf(o, sha)
                    ),
                    Set(km.KeepLeft)
                  )
                )
              case _ =>
                add(
                  Draft(
                    blocking(MergeIssue.Title("two different articles under one slug (no common base)"), None),
                    Set(km.KeepLeft)
                  )
                )
      }
    }

    // records added on both sides that are one (or a question), as the kinds' rules find them
    run.addedOnBoth(questions)

    // a goal that is one on both sides with two approved plans and neither newer: a question (#881)
    val goalPlanIssue = scala.collection.mutable.Map.empty[UUID, MergeIssue.Id] // bundle uid → issue
    merges.toList.collect { case (f, (o, Merges.GoalFold)) => (f, o) }.foreach { (f, o) =>
      for
        l <- localGoal.get(o)
        r <- localized.goals.find(_.uid == toLocal(f))
        if MergeFold.planOutcome(l, r) == MergeFold.PlanOutcome.Differ
      do
        val id = issueId(MergeIssue.Kind.GoalPlanDiffers, Bundle.Kind.Goal, List(f), List(o), rightRev(f), localRev(o))
        goalPlanIssue(f) = id
        add(
          Draft(
            MergeIssue(
              id,
              MergeIssue.Kind.GoalPlanDiffers,
              MergeIssue.Severity.Blocking,
              Bundle.Kind.Goal,
              MergeIssue.Title(s"goal ${l.slug}: two different approved plans, and neither is known to be the newer"),
              Some(
                IssueSide(
                  IssueSide.Label(l.slug.value),
                  o,
                  l.planApprovedAt.map(a => IssueSide.Lines(s"approved $a")).toList
                )
              ),
              Some(
                IssueSide(
                  IssueSide.Label(r.slug.value),
                  f,
                  r.planApprovedAt.map(a => IssueSide.Lines(s"approved $a")).toList
                )
              ),
              List(
                Problem.Unclassified(Legacy.Text("the plan approved later wins when both say when; here they do not"))
              ),
              lr,
              detail = Some(
                IssueDetail(fields =
                  List(
                    IssueField(
                      IssueField.Field("plan"),
                      l.plan.map(j => IssueField.Value(MergeIssue.Detail.planText(j))),
                      r.plan.map(j => IssueField.Value(MergeIssue.Detail.planText(j)))
                    ),
                    IssueField(
                      IssueField.Field("approved"),
                      l.planApprovedAt.map(t => IssueField.Value(t.value)),
                      r.planApprovedAt.map(t => IssueField.Value(t.value))
                    )
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
          info += s"goal ${l.slug}: its $k schedule differs (here ${a.cron}${
              if a.enabled.value then "" else " off"
            }, " +
            s"the bundle's ${b.cron}${if b.enabled.value then "" else " off"}) — this collection's is kept"
        }
      }
    }

    // ── resolutions ──
    val all = drafts.result().distinctBy(_.issue.id)
    val byId: Map[MergeIssue.Id, Draft] = all.map(d => d.issue.id -> d).toMap
    val errors = List.newBuilder[String]
    val texts: Map[MergeIssue.Id, MergeResolution.Text] =
      resolutions.resolve.flatMap(r => r.text.map(MergeIssue.Id(r.issue.value.trim) -> _)).toMap
    // a resolution an earlier apply carried out answers an issue that is gone because of it: done, not stale
    val carriedOut = resolutions.resolve.filter(r =>
      !byId.contains(MergeIssue.Id(r.issue.value.trim)) &&
        in.remembered
          .get(MergeIssue.Id(r.issue.value.trim))
          .exists(d => MergeDecision.parse(MergeDecision.Wire(r.decision.value)).toOption.contains(d))
    )
    if carriedOut.nonEmpty then
      info += s"${Plural.count(Plural.Count(carriedOut.size.toLong), Plural.Noun.Singular("resolution"))} carried out by an earlier `merge apply` (the issues are gone)"
    val chosen: Map[MergeIssue.Id, MergeDecision] = resolutions.resolve
      .filterNot(carriedOut.contains)
      .flatMap { r =>
        val id = MergeIssue.Id(r.issue.value.trim)
        (byId.get(id), MergeDecision.parse(MergeDecision.Wire(r.decision.value))) match
          case (None, _) =>
            errors += s"$id: not an issue of this plan — stale (the records changed since it was decided) or never one"
            None
          case (_, Left(e))          => errors += s"$id: ${e.sentence}"; None
          case (Some(d), Right(dec)) =>
            val i = d.issue
            val argOk: Either[MergePlan.ResolutionErrors, Unit] = dec.kind match
              case km.Map => run.citekeyArgument(i, dec).getOrElse(Right(()))
              case km.RenameRight | km.RenameLeft if run.argument(i, dec).isDefined => run.argument(i, dec).get
              case km.RenameRight | km.RenameLeft => run.citekeyArgument(i, dec).getOrElse(Right(()))
              case _                              => Right(())
            if !i.options.contains(dec.kind) then
              errors += s"$id: ${dec.wire} is not a decision for this issue (${i.options.map(MergeDecision.Kind.wireOf).mkString(" | ")})"
              None
            else
              argOk match
                case Left(e)  => errors += s"$id: ${e.value}"; None
                case Right(_) => Some(id -> dec)
      }
      .toMap
    // the decision in force for an issue, and who made it: a resolution, a policy, an earlier apply,
    // the default
    def decisionOf(i: MergeIssue): Option[(MergeDecision, MergeIssue.DecidedBy)] =
      chosen
        .get(i.id)
        .map(_ -> MergeIssue.DecidedBy.Resolution)
        .orElse(
          Option
            .when(i.kind == MergeIssue.Kind.YearDiffers && policy.editions.contains(EditionsPolicy.Merge))(i.suggested)
            .flatten
            .map(_ -> MergeIssue.DecidedBy.Policy)
        )
        .orElse(
          in.remembered.get(i.id).filter(d => i.options.contains(d.kind)).map(_ -> MergeIssue.DecidedBy.Remembered)
        )
        .orElse(
          Option
            .when(i.severity == MergeIssue.Severity.Default)(i.suggested)
            .flatten
            .map(_ -> MergeIssue.DecidedBy.Default)
        )
    // an issue conditional on another applies when that one is decided its way; conditions chain (an
    // attestation's question under the body question under the same-work question)
    val standings = scala.collection.mutable.Map.empty[MergeIssue.Id, Standing]
    def standingOf(id: MergeIssue.Id, seen: Set[MergeIssue.Id]): Standing =
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
                    case None                                    => Standing.Conditional
                    case Some((d, _)) if d.kind == c.when        => Standing.Live
                    case Some((_, MergeIssue.DecidedBy.Default)) => Standing.Conditional
                    case Some(_)                                 => Standing.Dropped
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
      else s"is ${decisionOf(byId(c.issue).issue).fold("open")(_._1.wire.value)}"
      errors += s"$id: applies only if ${c.issue} = ${MergeDecision.Kind.wireOf(c.when)}, and ${c.issue} $now"
    }
    val decided: List[Draft] = live.map { d =>
      val i = d.issue
      val dec = decisionOf(i)
      d.modify(_.issue).setTo(i.modify(_.decision).setTo(dec.map(_._1)).modify(_.decidedBy).setTo(dec.map(_._2)))
    }
    val issues = decided.map(_.issue)
    val unconditional: Map[MergeIssue.Id, MergeIssue] = issues.filter(_.condition.isEmpty).map(i => i.id -> i).toMap

    /** The decision in force for an issue that applies. */
    def decisionIn(id: MergeIssue.Id): Option[MergeDecision] = unconditional.get(id).flatMap(_.decision)
    val gateDecision: Map[MergeIssue.Id, MergeDecision] =
      issues.filter(_.kind == MergeIssue.Kind.SameWork).flatMap(i => i.decision.map(i.id -> _)).toMap

    // ── the folds: what merge apply writes, as decided now ──
    // a bundle uid → (the uid here, kind); replacement lines by their final uid, built once the
    // folds are known (from the bundle's lines in this collection's uids)
    val foldMap = scala.collection.mutable.LinkedHashMap.empty[UUID, (UUID, Bundle.Kind)]
    val overrides = scala.collection.mutable.Map.empty[UUID, Decision]
    type Line[A] = BundleEntities => A
    val articleLine = scala.collection.mutable.Map.empty[UUID, Line[Bundle.Article]]
    val goalLine = scala.collection.mutable.Map.empty[UUID, Line[Bundle.Goal]]
    val queueLine = scala.collection.mutable.Map.empty[UUID, Line[Bundle.QueueItem]]
    def articleIn(es: BundleEntities, u: UUID) = es.articles.find(_.uid == u).get
    def goalIn(es: BundleEntities, u: UUID) = es.goals.find(_.uid == u).get
    def queueIn(es: BundleEntities, u: UUID) = es.queue.find(_.uid == u).get
    val foldedSources = scala.collection.mutable.LinkedHashMap.empty[UUID, UUID]
    val renames = List.newBuilder[(UUID, Citekey, Citekey)]
    val snapshots = scala.collection.mutable.LinkedHashSet.empty[UUID]
    val versions = List.newBuilder[ExtraVersion]
    val copies = List.newBuilder[Line[Bundle.Article]]
    val reviews = scala.collection.mutable.LinkedHashSet.empty[Bundle.Article.Slug]
    var tally = MergeTally() // the apply's report, added to as each fold is decided
    def fold(f: UUID, l: UUID, kind: Bundle.Kind): Unit = foldMap(f) = l -> kind
    val folds = new PlanFolds:
      def input: Input = in
      def localized: BundleEntities = linesHere
      def fold(foreign: UUID, here: UUID, kind: Bundle.Kind): Unit = foldMap(foreign) = here -> kind
      def foldedTo(foreign: UUID): Option[UUID] = foldMap.get(foreign).map(_._1)
      def here(u: UUID): Boolean = localRevs.contains(u)
      def toLocal(u: UUID): UUID = mapped.getOrElse(u, u)
      def decisionIn(issue: MergeIssue.Id): Option[MergeDecision] =
        unconditional.get(issue).flatMap(_.decision)
      def count(f: MergeTally => MergeTally): Unit = tally = f(tally)
      def keepLocal(foreign: UUID): Unit = overrides(foreign) = Decision.Kept

    // the citekey each source of the bundle will have here (`[[citekey]]` links follow it)
    val finalCitekey = scala.collection.mutable.Map.empty[UUID, Citekey]
    val collisionDecided = issues.filter(i => i.kind == MergeIssue.Kind.CitekeyCollision && i.condition.isEmpty)
    val renameRight: Map[UUID, Citekey] = collisionDecided
      .flatMap(i =>
        i.decision
          .filter(_.kind == km.RenameRight)
          .flatMap(_.argument)
          .zip(i.right.map(_.uid))
          .map((a, u) => u -> Citekey(a.value))
      )
      .toMap
    collisionDecided.foreach { i =>
      i.decision
        .filter(_.kind == km.RenameLeft)
        .flatMap(_.argument)
        .zip(i.left)
        .foreach((ck, l) => renames += ((l.uid, Citekey(l.label.value), Citekey(ck.value))))
    }

    // the pairs that fold: every auto-merge, and a gate decided map
    pairs.foreach { (p, _) =>
      val (r, l) = (p.right, p.left)
      val mapTo: Option[Citekey] = fates(r.uid) match
        case SourceFate.Auto(_)    => Some(l.citekey)
        case SourceFate.Gate(_, g) =>
          decisionIn(gateId(g)).filter(_.kind == km.Map).flatMap(_.argument).map(a => Citekey(a.value))
        case _ => None
      mapTo.flatMap(ck => com.histmersion.domain.library.Citekey.option(ck.value).map(ck -> _)).foreach {
        (ck, domainCk) =>
          fold(r.uid, l.uid, Bundle.Kind.Source)
          foldedSources(r.uid) = l.uid
          finalCitekey(r.uid) = ck
          tally = tally.modify(_.sourcesMerged).using(n => MergeTally.SourcesMerged(n.value + 1))
          // the source field by field and its findings, as the sources' rules fold them
          val keepRightBody = run.foldSources(r, l, domainCk, folds)

          // what the kinds own of the pair before the rest: the figures
          run.foldPairFirst(r, l, keepRightBody, folds)

          // what the kinds own of the pair: as the body question is decided
          val alt = run.bodyIssue(r.uid) match
            case Some(id) => Some(decisionIn(id).map(_.kind).getOrElse(km.KeepLeft))
            case None     => None
          run.foldPair(r, l, alt, folds)
      }
    }
    // the citekey every other source of the bundle will have here
    fes.sources.foreach { s =>
      if !finalCitekey.contains(s.uid) then
        fates(s.uid) match
          case SourceFate.Same(l, _) =>
            val writes =
              initialOf.get(s.uid).exists(r => r.decision == Decision.FastForward || r.decision == Decision.Insert)
            finalCitekey(s.uid) = if writes then localized.sources.find(_.uid == l).fold(s.citekey)(_.citekey)
            else sourcesByUid.get(l).fold(s.citekey)(_.citekey)
          case _ => finalCitekey(s.uid) = renameRight.getOrElse(s.uid, s.citekey)
    }
    renameRight.foreach((u, ck) => com.histmersion.domain.library.Citekey.option(ck.value).foreach(run.renamed(u, _)))
    tally = tally.copy(sourcesRenamed = MergeTally.SourcesRenamed(renameRight.size + renames.result().size))
    // a finding id or an asset name a different record has here: the bundle's is renumbered / renamed
    val relabelled = scala.collection.mutable.ListBuffer.empty[String]
    val (notes0, relabelled0) = (info, relabelled)
    val relabels = new PlanRelabels:
      def rows: List[Row] = initialRows
      def localized: BundleEntities = linesHere
      def writesInitially(foreign: UUID): Boolean = initialOf.get(foreign).exists(_.decision.writes)
      def info(line: PlanRelabels.InfoLine): Unit = notes0 += line.value
      def relabelled(line: PlanRelabels.RelabelledLine): Unit = relabelled0 += line.value
    run.relabelFirst(fates, relabels)
    run.relabel(relabels)
    val ckMap: Map[Citekey, Citekey] =
      fes.sources.flatMap(s => finalCitekey.get(s.uid).filter(_ != s.citekey).map(s.citekey -> _)).toMap
    def rewrite(body: Bundle.Article.Body): Bundle.Article.Body =
      if ckMap.isEmpty then body
      else
        run.relinked(
          body,
          ckMap.flatMap((k, v) =>
            for
              from <- com.histmersion.domain.library.Citekey.option(k.value)
              to <- com.histmersion.domain.library.Citekey.option(v.value)
            yield from -> to
          )
        )

    // articles, goals, queue items and events M4 merges by itself, and the decisions about the rest
    def articleFold(f: UUID, o: UUID, body: Option[Bundle.Article.Body], theirsWins: Boolean, review: Boolean): Unit =
      val raw = rawArticle(f)
      if f != o then fold(f, o, Bundle.Kind.Article)
      val loc = localArticle(o)
      articleLine(o) = es =>
        val theirs = articleIn(es, o)
        val b = body.getOrElse(rewrite(theirs.body))
        if theirsWins then
          theirs
            .modify(_.uid)
            .setTo(loc.uid)
            .modify(_.origin)
            .setTo(loc.origin)
            .modify(_.slug)
            .setTo(loc.slug)
            .modify(_.body)
            .setTo(b)
            .modify(_.rev)
            .setTo(Bundle.Article.Rev(""))
        else MergeFold.foldArticle(loc, theirs, MergeFold.ArticleBody(b.value))
      snapshots += o
      versions += ExtraVersion(o, raw.body, raw.summary, raw.updatedAt)
      if review then reviews += loc.slug
    merges.foreach { case (f, (o, how)) =>
      how match
        case Merges.ArticleKeep =>
          if f != o then fold(f, o, Bundle.Kind.Article)
          articleLine(o) = _ => localArticle(o)
          overrides(f) = Decision.Kept
        case Merges.ArticleTheirs =>
          articleFold(f, o, None, theirsWins = true, review = false)
          tally = tally.modify(_.articlesFastForward).using(n => MergeTally.ArticlesFastForward(n.value + 1))
        case Merges.ArticleMerged(b) =>
          articleFold(f, o, Some(rewrite(b)), theirsWins = false, review = true)
          tally = tally.modify(_.articlesThreeWay).using(n => MergeTally.ArticlesThreeWay(n.value + 1))
        case Merges.GoalFold =>
          if f != o then fold(f, o, Bundle.Kind.Goal)
          val planDecision = goalPlanIssue.get(f).flatMap(decisionIn).map(_.kind)
          goalLine(o) = es => MergeFold.foldGoal(localGoal(o), goalIn(es, o), from, planDecision)
          tally = tally.modify(_.goalsMerged).using(n => MergeTally.GoalsMerged(n.value + 1))
        case Merges.QueueFold =>
          if f != o then fold(f, o, Bundle.Kind.QueueItem)
          queueLine(o) = es => MergeFold.foldQueue(localQueue(o), queueIn(es, o))
          tally = tally.modify(_.queueMerged).using(n => MergeTally.QueueMerged(n.value + 1))
        case Merges.Contributed(m) => m.fold(f, o, folds)
    }
    // the decisions about articles and events that M4 carries out (keep-left and keep-right of a
    // plain conflict are M2's row decisions, below)
    val keptRight = scala.collection.mutable.Set.empty[UUID]
    unconditional.values.toList.sortBy(_.id.value).foreach { i =>
      (i.kind, i.entity, i.decision, i.right.map(_.uid), i.left.map(_.uid)) match
        case (
              MergeIssue.Kind.ArticleDiverged | MergeIssue.Kind.ChangedBothSides,
              Bundle.Kind.Article,
              Some(d),
              Some(f),
              Some(o)
            ) =>
          val sameUid = toLocal(f) == o
          d.kind match
            case km.KeepRight if !sameUid =>
              articleFold(f, o, None, theirsWins = true, review = true)
            case km.KeepRight =>
              keptRight += f
              reviews += localArticle(o).slug
            case km.File =>
              articleFold(
                f,
                o,
                Some(texts.get(i.id).fold(Bundle.Article.Body(""))(t => Bundle.Article.Body(t.value))),
                theirsWins = false,
                review = true
              )
              if sameUid then overrides(f) = Decision.FastForward
              tally = tally.modify(_.articlesFromFile).using(n => MergeTally.ArticlesFromFile(n.value + 1))
            case km.Both =>
              val slug = bothSlug(f, rawArticle(f).slug)
              if sameUid then
                overrides(f) = Decision.Kept
                val copyUid = UUID.nameUUIDFromBytes(s"merge-both:$f".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                copies += (es =>
                  articleIn(es, o)
                    .modify(_.uid)
                    .setTo(copyUid)
                    .modify(_.slug)
                    .setTo(slug)
                    .modify(_.body)
                    .setTo(rewrite(rawArticle(f).body))
                    .modify(_.rev)
                    .setTo(Bundle.Article.Rev(""))
                )
              else
                articleLine(f) = es =>
                  articleIn(es, f)
                    .modify(_.slug)
                    .setTo(slug)
                    .modify(_.body)
                    .setTo(rewrite(rawArticle(f).body))
                    .modify(_.rev)
                    .setTo(Bundle.Article.Rev(""))
              reviews += slug
              tally = tally.modify(_.articlesBoth).using(n => MergeTally.ArticlesBoth(n.value + 1))
            case _ => ()
        case _ => run.decided(i, folds)
    }
    // every other article the bundle writes carries its links in this collection's citekeys
    if ckMap.nonEmpty then
      initialRows
        .filter(r =>
          r.kind == Bundle.Kind.Article && !foldMap.contains(r.foreignUid) && !articleLine.contains(r.uid) &&
            !overrides.contains(r.foreignUid) &&
            (r.decision == Decision.Insert || r.decision == Decision.FastForward || keptRight(r.foreignUid))
        )
        .foreach { r =>
          val raw = rawArticle(r.foreignUid)
          val b = rewrite(raw.body)
          if b != raw.body then
            articleLine(r.uid) =
              es => articleIn(es, r.uid).modify(_.body).setTo(b).modify(_.rev).setTo(Bundle.Article.Rev(""))
            versions += ExtraVersion(r.uid, raw.body, raw.summary, raw.updatedAt)
        }

    // ── the final lines ──
    val foldUids: Map[UUID, UUID] = foldMap.view.mapValues(_._1).toMap
    val es0 = localize(localized, foldUids, Map.empty)
    def replaced[A](as: List[A], uid: A => UUID, repl: scala.collection.Map[UUID, Line[A]]): List[A] =
      MergePlanner.replaced(as, uid, repl, es0, (_: A) => true)
    val foldedHere = foldedSources.values.toSet
    // a merged source's whole edge set: ours and the bundle's, re-pointed, without self-loops
    val edges0 = es0.edges ++ in.local.edges.filter(e => foldedHere(e.srcUid))
    val selfLoops = edges0.filter(e => e.srcUid == e.dstUid).distinct
    if selfLoops.nonEmpty then
      tally = tally.copy(selfLoopsDropped = MergeTally.SelfLoopsDropped(selfLoops.size))
      selfLoops.foreach(e => info += s"edge ${e.kind} from a source to itself (both ends merged into one) — left out")
    // the bundle's citekey of a merged source becomes an old citekey of the survivor
    val citekeysHere = in.labels.citekeys.keySet ++ fes.sources.flatMap(s => finalCitekey.get(s.uid))
    val newAliases = foldedSources.toList.flatMap { (f, l) =>
      val raw = fes.sources.find(_.uid == f).get
      Option.when(
        raw.citekey != finalCitekey(f) && !citekeysHere(raw.citekey) &&
          in.labels.aliases.get(raw.citekey).forall(_ == l) && !fes.aliases.exists(_.alias == raw.citekey)
      )(Bundle.Alias(raw.citekey, l, Bundle.Alias.CreatedAt(raw.updatedAt.value)))
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
            s"${sourcesByUid.get(o).fold(o.toString)(_.citekey.value)} here (not kept as an old citekey)"
        )
    }
    tally = tally.copy(aliases =
      MergeTally.Aliases(
        newAliases.size +
          foldedSources.count((f, l) => sourcesByUid.get(l).exists(_.citekey != finalCitekey(f)))
      )
    )

    /** An old label travels unless it names ANOTHER record here — as a live label or as an old one of its own (an alias
      * is never also another record's label: it would resolve two ways).
      */
    def keptAliases[A, K](
        as: List[A],
        alias: A => K,
        owner: A => UUID,
        live: Map[K, UUID],
        old: Map[K, UUID],
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
      edges = edges0.filterNot(e => e.srcUid == e.dstUid).distinct,
      unresolvedEdges = (es0.unresolvedEdges ++ in.local.unresolvedEdges.filter(e => foldedHere(e.srcUid))).distinct,
      aliases = es0.aliases ++ newAliases,
      articles = replaced(es0.articles, _.uid, articleLine),
      goals = replaced(es0.goals, _.uid, goalLine).map { g =>
        // a goal here and there: this collection's schedules stay, the links of both
        localGoal.get(g.uid).filter(_ => !goalLine.contains(g.uid)).fold(g) { l =>
          val k = MergeFold.keepLocalSchedules(l, g)
          if k.links == g.links && k.schedules == g.schedules then g
          else g.copy(links = k.links, schedules = k.schedules, rev = Bundle.Goal.Rev(""))
        }
      },
      queue = replaced(es0.queue, _.uid, queueLine),
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
    val finalEs = relinkGoals(run.lines(finalEs0, es0), in)
    val dropped = run.dropped // bundle uids left out for good
    val finalRevs = finalEs.revs
    // a merged source whose own line is unchanged is still written when its edge set grows
    def edgeSet(es: List[Bundle.Edge], u: UUID) = es.filter(_.srcUid == u).toSet
    val edgesGrow: Set[UUID] = foldedHere.filter(u =>
      edgeSet(finalEs.edges, u) != edgeSet(in.local.edges, u) ||
        finalEs.unresolvedEdges.filter(_.srcUid == u).toSet != in.local.unresolvedEdges.filter(_.srcUid == u).toSet
    )

    // ── final row decisions ──
    val issueOfRow: Map[UUID, MergeIssue] = unconditional.values
      .filter(i =>
        i.kind == MergeIssue.Kind.ChangedBothSides || i.kind == MergeIssue.Kind.DeletedThereChangedHere ||
          i.kind == MergeIssue.Kind.LabelTaken || i.kind == MergeIssue.Kind.ArticleDiverged
      )
      .flatMap(i => i.right.map(_.uid).orElse(i.left.map(_.uid)).map(toLocal).map(_ -> i))
      .toMap
    val gateBoth: Set[UUID] = fes.sources
      .filter(s =>
        fates(s.uid) match
          case SourceFate.Gate(_, g) => gateDecision.get(gateId(g)).exists(_.kind == km.Both)
          case _                     => false
      )
      .map(_.uid)
      .toSet
    val folding: Set[UUID] = foldedSources.keySet.toSet
    val resolvedRows = initialRows.map { r0 =>
      val owner = ownerSource(r0.foreignUid, r0.kind)
      val uid = if r0.tombstone.value then r0.uid else foldUids.getOrElse(r0.foreignUid, r0.uid)
      val loc = if r0.tombstone.value then r0.localizedRev
      else finalRevs.get(uid).map(x => Local.Rev(x.rev)).getOrElse(r0.localizedRev)
      val r = r0
        .modify(_.uid)
        .setTo(uid)
        .modify(_.localizedRev)
        .setTo(loc)
        .modify(_.localRev)
        .setTo(localRevs.get(uid).map(x => Local.Rev(x.rev)))
        .modify(_.label)
        .setTo(if r0.tombstone.value then r0.label
        else finalRevs.get(uid).map(x => Row.Label(x.label)).getOrElse(r0.label))
      def foldDecision =
        if r.localRev.contains(loc) && !edgesGrow(uid) then Decision.Unchanged else Decision.FastForward
      if r0.tombstone.value then
        issueOfRow.get(r0.uid).flatMap(_.decision).map(_.kind) match
          case Some(km.KeepLeft)  => r.modify(_.decision).setTo(Decision.Kept)
          case Some(km.KeepRight) => r.modify(_.decision).setTo(Decision.Delete)
          case _                  => r
      else if dropped(r0.foreignUid) then r.modify(_.decision).setTo(Decision.Skip)
      else if overrides.contains(r0.foreignUid) then r.modify(_.decision).setTo(overrides(r0.foreignUid))
      else if foldUids.contains(r0.foreignUid) then r.modify(_.decision).setTo(foldDecision)
      else if r0.decision == Decision.Held && owner.exists(o => gateBoth(o) || folding(o)) then
        r.modify(_.decision).setTo(Decision.Insert)
      else if r0.decision == Decision.Conflict && merges.get(r0.foreignUid).exists(_._1 == uid) then
        r.modify(_.decision).setTo(foldDecision)
      else
        issueOfRow.get(r0.uid).flatMap(i => i.decision.map(i -> _)) match
          case Some((i, d)) =>
            (i.kind, d.kind) match
              case (MergeIssue.Kind.ChangedBothSides, km.KeepLeft)  => r.modify(_.decision).setTo(Decision.Kept)
              case (MergeIssue.Kind.ChangedBothSides, km.KeepRight) => r.modify(_.decision).setTo(Decision.FastForward)
              case (MergeIssue.Kind.LabelTaken | MergeIssue.Kind.ArticleDiverged, km.KeepLeft) =>
                r.modify(_.decision).setTo(Decision.Skip)
              case _ => r
          case None => r
    }

    // what needs a deleted (or skipped, or held) row is not written either
    val refs = finalEs.references
    val decisionOf0 = resolvedRows.map(r => r.uid -> r.decision).toMap
    val cascaded = BundlePlan.cascade(decisionOf0, refs, u => in.tombstoned(u) && !liveHere(u))
    val rows = resolvedRows.map(r => r.modify(_.decision).setTo(cascaded.getOrElse(r.uid, r.decision)))
    val decisionOfRow = rows.map(r => r.uid -> r.decision).toMap
    def writes(u: UUID): Boolean = decisionOfRow.get(u).exists(d => d == Decision.Insert || d == Decision.FastForward)
    val deletingNow = rows.filter(r => r.tombstone.value && r.decision == Decision.Delete).map(_.uid).toSet

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
    // what the kinds' written lines need that is not coming (an event's gazetteer place)
    run.problems(finalEs, inBundleLive, writes).foreach(fatal += _.value)
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
    val droppedAliases =
      fes.aliases.filter(a => in.aliasTombstones.get(a.alias).exists(t => a.createdAt.value <= t.value))
    droppedAliases.foreach(a =>
      notes += s"old citekey ${a.alias}: dropped here (renamed back) on ${in.aliasTombstones(a.alias)} — not brought back"
    )

    // ── what an import cannot do (it needs merge apply) ──
    val needsApply = List.newBuilder[String]
    fes.sources.foreach { s =>
      fates(s.uid) match
        case SourceFate.Auto(p) =>
          needsApply += s"source ${s.citekey}: the same work as ${p.left.citekey} here (${p.basis})" +
            " — folding the two into one is `merge apply`'s"
        case SourceFate.Gate(p, g) if gateDecision.get(gateId(g)).exists(_.kind == km.Map) =>
          needsApply += s"source ${s.citekey}: $g decided ${gateDecision(gateId(g)).wire} — folding it into ${p.left.citekey} is `merge apply`'s"
        case _ => ()
    }
    merges.foreach { case (f, (o, how)) =>
      val kind = rawRevs.get(f).fold("record")(_.kind.wire.value)
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
        if !run.pairIssue(i.kind) && !byId(i.id).importable(dec.kind) then
          needsApply += s"${i.id} = ${dec.wire}: carrying that out is `merge apply`'s"
      }
    }

    // ── counts and info ──
    @annotation.tailrec
    def countMatches(
        rest: List[Bundle.Source],
        byUid: Int,
        byLedger: Int,
        byStrongKey: Int,
        byScore: Int,
        undecided: Int,
        unmatched: Int,
        deletedHere: Int
    ): MatchCounts =
      rest match
        case Nil =>
          MatchCounts(
            MatchCounts.Sources(fes.sources.size),
            MatchCounts.ByUid(byUid),
            MatchCounts.ByLedger(byLedger),
            MatchCounts.ByStrongKey(byStrongKey),
            MatchCounts.ByScore(byScore),
            MatchCounts.Undecided(undecided),
            MatchCounts.Unmatched(unmatched),
            MatchCounts.DeletedHere(deletedHere)
          )
        case s :: tail =>
          fates(s.uid) match
            case SourceFate.Same(_, viaLedger) if !viaLedger.value =>
              countMatches(tail, byUid + 1, byLedger, byStrongKey, byScore, undecided, unmatched, deletedHere)
            case SourceFate.Same(_, _) =>
              countMatches(tail, byUid, byLedger + 1, byStrongKey, byScore, undecided, unmatched, deletedHere)
            case SourceFate.Deleted =>
              countMatches(tail, byUid, byLedger, byStrongKey, byScore, undecided, unmatched, deletedHere + 1)
            case SourceFate.Auto(p) if p.byKey.value =>
              countMatches(tail, byUid, byLedger, byStrongKey + 1, byScore, undecided, unmatched, deletedHere)
            case SourceFate.Auto(_) =>
              countMatches(tail, byUid, byLedger, byStrongKey, byScore + 1, undecided, unmatched, deletedHere)
            case SourceFate.Gate(_, _) =>
              countMatches(tail, byUid, byLedger, byStrongKey, byScore, undecided + 1, unmatched, deletedHere)
            case SourceFate.New =>
              countMatches(tail, byUid, byLedger, byStrongKey, byScore, undecided, unmatched + 1, deletedHere)
    val counts = countMatches(fes.sources, 0, 0, 0, 0, 0, 0, 0)
    // how each of the bundle's sources was matched, by citekey, as the plan's hash reads it
    val matched0: List[String] = fes.sources.sortBy(_.citekey.value).flatMap { s =>
      def line(left: Option[UUID], o: MatchOutcome) = s"${s.uid}>${left.fold("")(_.toString)}:${MatchOutcome.wireOf(o)}"
      fates(s.uid) match
        case SourceFate.Same(_, _) => None
        case SourceFate.Deleted    => Some(line(None, MatchOutcome.DeletedHere))
        case SourceFate.New        => Some(line(None, MatchOutcome.New))
        case SourceFate.Auto(p)    => Some(line(Some(p.left.uid), MatchOutcome.AutoMerge))
        case SourceFate.Gate(p, _) => Some(line(Some(p.left.uid), MatchOutcome.NeedsDecision))
    }
    val held =
      rows
        .filter(r => r.decision == Decision.Held && r.kind != Bundle.Kind.Source)
        .groupMapReduce(_.kind)(_ => 1)(_ + _)
    if held.nonEmpty then
      info += s"${held.toList.sortBy(_._1.ordinal).map((k, n) => Plural.count(Plural.Count(n.toLong), Plural.Noun.Singular(k.wire.value))).mkString(", ")} belong to " +
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
          info += s"queue item «${MergeIssue.Detail.preview(label, MergeIssue.Detail.ClipLength(50))}»: the same item on both sides — one item, done if done anywhere"
        case Merges.Contributed(m) =>
          info += m.info(PlanMerge.InfoLabel(label), PlanMerge.InfoBundleLabel(rawRevs.get(f).fold("")(_.label))).value
    }
    val problems: List[String] = (in.compat.map(_.value) ++ in.readProblems.map(_.value) ++ fatal.result()).distinct

    val apply = Apply(
      folds = foldMap.toList.map((f, v) => Fold(v._2, f, v._1)).sortBy(f => (f.kind.ordinal, f.foreignUid.toString)),
      renames = renames.result(),
      snapshots = snapshots.toSet,
      versions = versions.result(),
      copies = copies.result().map(_(es0)),
      attestations = run.fixes,
      tombstones = run.tombstones,
      bodyRenames = run.bodyRenames,
      reviews = reviews.toList,
      remember = issues
        .filter(i =>
          i.condition.isEmpty && i.decidedBy
            .exists(b => b == MergeIssue.DecidedBy.Resolution || b == MergeIssue.DecidedBy.Policy)
        )
        .flatMap(i => i.decision.map(i.id -> _)),
      touch = (foldMap.values.map(_._1) ++ merges.values.map(_._1) ++ articleLine.keys).toSet,
      tally = tally.copy(redirects = MergeTally.Redirects(foldMap.size))
    )
    val changes = rows.count(_.decision.writes) + apply.folds.size + apply.renames.size + apply.attestations.size +
      apply.tombstones.size + apply.copies.size

    val sorted = issues.sortBy(i => (i.severity.ordinal, i.condition.isDefined, i.entity.ordinal, i.id.value))
    val hash = BundleFormat
      .sha256Hex(
        (
          List(in.manifest.bundleId.toString) ++
            matched0 ++
            sorted.map(i => s"${i.id}=${i.decision.fold("")(_.wire.value)}") ++ problems ++
            rows.map(r =>
              s"${r.foreignUid}>${r.uid}:${r.decision}:${r.localizedRev}:${r.localRev.fold("")(_.value)}"
            ) ++
            (apply.renames.map(_.toString) ++ apply.attestations.map(_.toString) ++ apply.folds.map(_.toString)).sorted
        ).mkString("\n")
      )
      .value
      .take(8)
    val usedResolutions = chosen.keySet.intersect(liveIds).size

    val plan = run.planned(
      MergePlan(
        in.manifest.bundleId,
        in.manifest.instance,
        in.here,
        MergePlan.FormatVersion(in.manifest.formatVersion.value),
        MergePlan.SchemaVersion(in.manifest.schemaVersion.value),
        in.manifest.asOf.map(a => MergePlan.AsOf(a.value)),
        MergePlan.PlanHash(hash),
        counts,
        PlanMatches.empty,
        sorted,
        (info.result() ++ notes.result()).map(line => MergePlan.Info(line)),
        problems.map(p => MergePlan.Problems(p)),
        errors.result().distinct.map(e => MergePlan.ResolutionErrors(e)),
        MergePlan.Resolved(usedResolutions),
        MergePlan.Changes(changes)
      )
    )
    val aliasOf = aliasIssues.result()
    Result(
      plan,
      finalEs,
      rows,
      needsApply.result().distinct.map(line => Result.NeedsApply(line)),
      issues
        .filter(i =>
          i.kind == MergeIssue.Kind.AliasConflict && i.condition.isEmpty && i.decision.exists(_.kind == km.KeepLeft)
        )
        .flatMap(i => aliasOf.get(i.id))
        .toSet ++ droppedAliases.map(_.alias),
      notes.result().map(line => Result.Notes(line)),
      apply
    )

  /** A kind's final lines: each line `repl` replaces read from `es0` (the bundle's lines in this collection's uids,
    * folds made), the lines it adds after them in uid order, the ones `keep` refuses left out. Pure.
    */
  def replaced[A](
      as: List[A],
      uid: A => UUID,
      repl: scala.collection.Map[UUID, BundleEntities => A],
      es0: BundleEntities,
      keep: A => Boolean
  ): List[A] =
    val base = as.filter(keep).map(a => repl.get(uid(a)).fold(a)(_(es0)))
    val extra = repl.keySet.diff(as.map(uid).toSet).toList.sortBy(_.toString).map(u => repl(u)(es0))
    (base ++ extra).distinctBy(uid)
