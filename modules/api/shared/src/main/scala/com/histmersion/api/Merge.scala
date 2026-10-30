package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.values.{BundleKind, DecidedBy, DecisionKind, EditionsPolicy, IssueKind, IssueSeverity, MatchMethod, MatchOutcome, WireEnumValues}

/** `merge plan` (docs/research-merge-import.md §5–§6, milestone M3): what importing a bundle into this
  * collection would do, and every question it raises, in ONE run.
  *
  * Sides: **left** is always HERE (this collection), **right** is always the BUNDLE (the other one) —
  * JabRef's vocabulary (`keep-left`, `keep-right`, `both`), as the resolution file uses it.
  *
  * A plan is pure: computed from the bundle, this collection's state and the resolutions, and
  * deterministic. Issue ids are stable while the records they name do not change (§6.1), so a
  * resolution file can be written once and reused; an id that is no longer in the plan is reported
  * as stale, never silently re-applied. */

/** One decision: its kind and, for the kinds that take one, the argument (a citekey, a path). On the
  * wire and in the resolution file it is one string — `keep-left`, `map:guterbock1997_hittite`. */
final case class MergeDecision(kind: DecisionKind, argument: Option[String] = None):
  def wire: String = DecisionKind.wireOf(kind) + argument.fold("")(a => s":$a")

object MergeDecision:
  def of(kind: DecisionKind): MergeDecision                = MergeDecision(kind, None)
  def withArg(kind: DecisionKind, arg: String): MergeDecision = MergeDecision(kind, Some(arg))

  /** Parse `kind[:argument]`; the argument is required exactly by the kinds that take one. Pure. */
  def parse(raw: String): Either[String, MergeDecision] =
    val s = raw.trim
    val (k, arg) = s.indexOf(':') match
      case -1 => (s, None)
      case i  => (s.take(i), Some(s.drop(i + 1).trim))
    DecisionKind.values.find(d => DecisionKind.wireOf(d) == k) match
      case None => Left(s"'$s' is not a decision (${DecisionKind.values.map(DecisionKind.wireOf).mkString(", ")})")
      case Some(d) if d.takesArgument && arg.forall(_.isEmpty) =>
        Left(s"'$s': ${DecisionKind.wireOf(d)} needs an argument (${DecisionKind.wireOf(d)}:<value>)")
      case Some(d) if !d.takesArgument && arg.isDefined =>
        Left(s"'$s': ${DecisionKind.wireOf(d)} takes no argument")
      case Some(d) => Right(MergeDecision(d, arg.filter(_.nonEmpty)))

  // the string form IS the value (the resolution file's spelling), so it crosses the wire as one
  given Encoder[MergeDecision] = Encoder.encodeString.contramap(_.wire)
  given Decoder[MergeDecision] = Decoder.decodeString.emap(parse)
  given Schema[MergeDecision]  = Schema.string[MergeDecision]

/** One side of an issue: the record's label, its uid, and a few lines describing it. */
final case class IssueSide(label: String, uid: UUID, lines: List[String] = Nil) derives KindlingsCodecAsObject
object IssueSide:
  given Schema[IssueSide] = KindlingsSchema.derived[IssueSide].schema

/** An issue that exists only under one alternative of another, still open, issue: `if S-3c1e7a2b = map`.
  * It is listed now so that no new issue appears after the other one is decided (§6.1). */
final case class IssueCondition(issue: String, when: DecisionKind) derives KindlingsCodecAsObject
object IssueCondition:
  given Schema[IssueCondition] = KindlingsSchema.derived[IssueCondition].schema

/** One field of the two records an issue is about, as the review screen's three-column table shows
  * it (here | result | bundle). `None` = the record says nothing. */
final case class IssueField(field: String, left: Option[String], right: Option[String]) derives KindlingsCodecAsObject
object IssueField:
  given Schema[IssueField] = KindlingsSchema.derived[IssueField].schema

/** One region of a three-way merge of two article texts: agreed lines, or a conflict between this
  * collection's lines and the bundle's over the common version's. */
final case class IssueHunk(conflict: Boolean, base: List[String] = Nil, left: List[String] = Nil, right: List[String] = Nil)
    derives KindlingsCodecAsObject
object IssueHunk:
  given Schema[IssueHunk] = KindlingsSchema.derived[IssueHunk].schema

/** The texts of an article question: the common version (when the histories share one), both
  * sides', and the merge's regions — what the review screen's diff3 view and its editor start from. */
final case class IssueText(base: Option[String], left: String, right: String, hunks: List[IssueHunk] = Nil)
    derives KindlingsCodecAsObject
object IssueText:
  given Schema[IssueText] = KindlingsSchema.derived[IssueText].schema

/** A span of signed years (BCE negative), either end open. */
final case class IssueSpan(start: Option[Long], end: Option[Long]) derives KindlingsCodecAsObject
object IssueSpan:
  given Schema[IssueSpan] = KindlingsSchema.derived[IssueSpan].schema

/** Where and when the two records of a place or event question are: GeoJSON geometries (the review
  * screen draws both on a map) and their spans. */
final case class IssuePlace(leftGeometry: Option[String] = None, rightGeometry: Option[String] = None,
    leftSpan: Option[IssueSpan] = None, rightSpan: Option[IssueSpan] = None) derives KindlingsCodecAsObject
object IssuePlace:
  given Schema[IssuePlace] = KindlingsSchema.derived[IssuePlace].schema

/** What the review screen shows beside an issue; not part of the issue's id or of the plan hash. */
final case class IssueDetail(fields: List[IssueField] = Nil, text: Option[IssueText] = None,
    place: Option[IssuePlace] = None) derives KindlingsCodecAsObject
object IssueDetail:
  given Schema[IssueDetail] = KindlingsSchema.derived[IssueDetail].schema

final case class MergeIssue(
    /** `<letter>-<8 hex>`: the letter names the entity (S source, O OCR body, F finding, A article, …),
      * the hex is a hash of the issue's kind and the uids and revs it is about. */
    id: String,
    kind: IssueKind,
    severity: IssueSeverity,
    entity: BundleKind,
    title: String,
    /** This collection's record, when it has one. */
    left: Option[IssueSide] = None,
    /** The bundle's record, when it has one. */
    right: Option[IssueSide] = None,
    /** Why: the evidence, e.g. the score's parts. */
    why: List[String] = Nil,
    /** The decisions that are allowed. */
    options: List[DecisionKind] = Nil,
    /** A suggested (or, for a [[IssueSeverity.Default]], the default) decision. */
    suggested: Option[MergeDecision] = None,
    condition: Option[IssueCondition] = None,
    /** The decision in force, and who made it. */
    decision: Option[MergeDecision] = None,
    decidedBy: Option[DecidedBy] = None,
    /** The two records side by side, for the review screen. */
    detail: Option[IssueDetail] = None
) derives KindlingsCodecAsObject:
  /** Open and holding the merge up: blocking, unconditional, undecided. */
  def open: Boolean = severity == IssueSeverity.Blocking && condition.isEmpty && decision.isEmpty
object MergeIssue:
  given Schema[MergeIssue] = KindlingsSchema.derived[MergeIssue].schema

/** One source of the bundle and what it was paired with. Pairs `Same` by uid are counted, not listed. */
final case class SourceMatch(
    rightUid: UUID,
    rightCitekey: String,
    leftUid: Option[UUID],
    leftCitekey: Option[String],
    method: MatchMethod,
    outcome: MatchOutcome,
    score: Option[Double] = None,
    why: List[String] = Nil,
    /** The [[IssueKind.SameWork]] issue that decides it, when one does. */
    issue: Option[String] = None
) derives KindlingsCodecAsObject
object SourceMatch:
  given Schema[SourceMatch] = KindlingsSchema.derived[SourceMatch].schema

/** The bundle's sources, by how they were matched. */
final case class MatchCounts(
    sources: Int = 0,
    byUid: Int = 0,
    byLedger: Int = 0,
    byStrongKey: Int = 0,
    byScore: Int = 0,
    undecided: Int = 0,
    unmatched: Int = 0,
    deletedHere: Int = 0
) derives KindlingsCodecAsObject
object MatchCounts:
  given Schema[MatchCounts] = KindlingsSchema.derived[MatchCounts].schema

final case class MergePolicy(editions: Option[EditionsPolicy] = None) derives KindlingsCodecAsObject
object MergePolicy:
  given Schema[MergePolicy] = KindlingsSchema.derived[MergePolicy].schema

/** One line of a resolution file: an issue id and the decision, as the user wrote it (checked by the
  * plan, which reports every bad one at once). */
final case class MergeResolution(issue: String, decision: String,
    /** The merged text of a `file:<path>` decision: the CLI reads the file and sends it, since the
      * server cannot read the user's files. */
    text: Option[String] = None) derives KindlingsCodecAsObject
object MergeResolution:
  given Schema[MergeResolution] = KindlingsSchema.derived[MergeResolution].schema

/** The resolution file (`--resolutions`), merged with `--resolve` flags and `--editions`. */
final case class MergeResolutions(
    resolve: List[MergeResolution] = Nil,
    policy: MergePolicy = MergePolicy(),
    /** The bundle id the file was written for, when it says. */
    bundle: Option[UUID] = None
) derives KindlingsCodecAsObject:
  /** `later` wins per issue (flags over the file); its policy where it sets one. */
  def overriddenBy(later: MergeResolutions): MergeResolutions =
    val ids = later.resolve.map(_.issue).toSet
    MergeResolutions(resolve.filterNot(r => ids(r.issue)) ++ later.resolve,
      MergePolicy(later.policy.editions.orElse(policy.editions)), later.bundle.orElse(bundle))
object MergeResolutions:
  given Schema[MergeResolutions] = KindlingsSchema.derived[MergeResolutions].schema

/** `POST /api/bundle/upload`: a bundle kept on the server under its sha256, so it can be planned
  * and imported (with resolutions) by name, and an MCP client can plan it without sending a file. */
final case class BundleUpload(sha256: String, bytes: Long, manifest: Option[BundleManifest], problems: List[String])
    derives KindlingsCodecAsObject
object BundleUpload:
  given Schema[BundleUpload] = KindlingsSchema.derived[BundleUpload].schema

/** `POST /api/merge/plan`: plan an uploaded bundle (by its sha256) with these resolutions. */
final case class MergePlanRequest(bundle: String, resolutions: MergeResolutions = MergeResolutions())
    derives KindlingsCodecAsObject
object MergePlanRequest:
  given Schema[MergePlanRequest] = KindlingsSchema.derived[MergePlanRequest].schema

/** `POST /api/merge/import`: import an uploaded bundle, taking the resolutions into account. */
final case class MergeImportRequest(bundle: String, resolutions: MergeResolutions = MergeResolutions(),
    dryRun: Boolean = false) derives KindlingsCodecAsObject
object MergeImportRequest:
  given Schema[MergeImportRequest] = KindlingsSchema.derived[MergeImportRequest].schema

/** The plan (§6.2): what an import would do and every issue, in one run. */
final case class MergePlan(
    bundleId: UUID,
    from: BundleInstance,
    into: BundleInstance,
    formatVersion: Int,
    schemaVersion: String,
    asOf: Option[String],
    /** A hash of the outcome — every match, every issue id and every decision in force. The same
      * inputs give the same hash; `merge apply` (M5) re-plans and compares it. */
    planHash: String,
    matches: MatchCounts,
    /** Every source of the bundle that is not simply the same record here. */
    sources: List[SourceMatch],
    issues: List[MergeIssue],
    /** Reported only: what will be carried along, dropped or derived. */
    info: List[String],
    /** What makes the bundle impossible to import whatever is decided (a missing blob, a newer schema). */
    problems: List[String],
    /** Resolutions that could not be used: an id not in this plan (stale — the records changed since
      * — or never one), or a decision the issue does not allow. Errors, never ignored. */
    resolutionErrors: List[String],
    /** How many resolutions were used. */
    resolved: Int,
    /** How many rows `merge apply` would write (insert, update, fold or delete) as decided now. After
      * an apply, planning the same bundle again gives 0. */
    changes: Int = 0
) derives KindlingsCodecAsObject:
  def open: List[MergeIssue] = issues.filter(_.open)
  def blocking: List[MergeIssue] = issues.filter(_.severity == IssueSeverity.Blocking)
  def defaults: List[MergeIssue] = issues.filter(_.severity == IssueSeverity.Default)
object MergePlan:
  given Schema[MergePlan] = KindlingsSchema.derived[MergePlan].schema

// ── merge apply (§6.1, §6.4; milestone M5) ────────────────────────────────────────────────────────

/** `POST /api/merge/apply`: carry out the plan of an uploaded bundle. `planHash` is the hash of the
  * plan the user reviewed: the server plans again and refuses when the hash differs (the collection,
  * the bundle or the resolutions changed since). */
final case class MergeApplyRequest(bundle: String, planHash: String, resolutions: MergeResolutions = MergeResolutions())
    derives KindlingsCodecAsObject
object MergeApplyRequest:
  given Schema[MergeApplyRequest] = KindlingsSchema.derived[MergeApplyRequest].schema

/** What an apply did beyond inserting and updating rows (§6.4): the folds of matched records. */
final case class MergeTally(
    /** Bundle sources folded into a source here. */
    sourcesMerged: Int = 0,
    /** …of them matched by score rather than a strong key or an identity. */
    sourcesByScore: Int = 0,
    /** Sources given a new citekey (`rename-left`/`rename-right`). */
    sourcesRenamed: Int = 0,
    /** Old citekeys added (the bundle's citekey of a merged source among them). */
    aliases: Int = 0,
    findingsMerged: Int = 0,
    assetsMerged: Int = 0,
    attestationsReanchored: Int = 0,
    attestationsDropped: Int = 0,
    /** Self-loops a merge would have made (`A superseded_by A′`), left out. */
    selfLoopsDropped: Int = 0,
    articlesThreeWay: Int = 0,
    articlesFastForward: Int = 0,
    /** Articles kept twice, the bundle's under another slug (`both`). */
    articlesBoth: Int = 0,
    articlesFromFile: Int = 0,
    eventsMerged: Int = 0,
    goalsMerged: Int = 0,
    queueMerged: Int = 0,
    /** Redirects written (`uid_aliases`): one per folded record. */
    redirects: Int = 0,
    /** Places and named periods of the bundle that are one with one here (by id, or by label and
      * where/when). */
    placesMerged: Int = 0,
    periodsMerged: Int = 0
) derives KindlingsCodecAsObject
object MergeTally:
  given Schema[MergeTally] = KindlingsSchema.derived[MergeTally].schema

/** `POST /api/merge/apply` (§6.4). `problems` are blocking — when there are any nothing was written
  * (and no backup taken); among them every open issue and the stale plan hash. */
final case class MergeApplyResult(
    bundleId: UUID,
    from: BundleInstance,
    applied: Boolean,
    planHash: String,
    mergeId: Option[UUID] = None,
    /** The backup taken before anything was written (its file name in `backups.dir`). */
    backup: Option[String] = None,
    counts: List[BundleKindCounts] = Nil,
    tally: MergeTally = MergeTally(),
    /** The articles queued for `/review-article` (merged or changed by a decision). */
    reviews: List[String] = Nil,
    /** The `log` queue entry recording the merge. */
    logEntry: Option[Int] = None,
    /** What planning the same bundle again (with the same resolutions) would change — 0 after a
      * successful apply: the merge is idempotent. */
    replanChanges: Option[Int] = None,
    problems: List[String] = Nil,
    issues: List[MergeIssue] = Nil,
    notes: List[String] = Nil
) derives KindlingsCodecAsObject:
  def changed: Int = counts.map(_.changed).sum
object MergeApplyResult:
  given Schema[MergeApplyResult] = KindlingsSchema.derived[MergeApplyResult].schema

// ── merge apply as a job (§7.7 open question 3) ───────────────────────────────────────────────────

/** Where an apply is. On the wire as its lower-case name. `Interrupted`: the server stopped while it
  * ran — its transaction committed or rolled back as a whole, never half (`merge list` says which). */
enum MergeJobState:
  case Running, Done, Failed, Interrupted

object MergeJobState:
  given WireEnumValues[MergeJobState]:
    def all: Array[MergeJobState]                  = MergeJobState.values
    def wire(e: MergeJobState): String             = e.toString.toLowerCase(java.util.Locale.ROOT)
    def fromWire(s: String): Option[MergeJobState] = MergeJobState.values.find(_.toString.equalsIgnoreCase(s.trim))
  given Encoder[MergeJobState] = WireEnum.encoder
  given Decoder[MergeJobState] = WireEnum.decoder
  given Schema[MergeJobState]  = WireEnum.schema

/** `POST /api/merge/apply` and `GET /api/merge/apply/{job}`: the apply runs in the background (its
  * backup can take minutes on a large collection); the request waits up to `merge.apply-wait-seconds`
  * and answers where it is. `result` once it is done — refused or applied, both are `done`; `error`
  * when it crashed or was interrupted. The job is a `jobs` row (kind [[MergeApplyJob.JobKind]]), so it
  * outlives a restart (#888). */
final case class MergeApplyJob(job: String, state: MergeJobState, bundle: String, planHash: String, elapsedMs: Long,
    result: Option[MergeApplyResult] = None, error: Option[String] = None) derives KindlingsCodecAsObject
object MergeApplyJob:
  given Schema[MergeApplyJob] = KindlingsSchema.derived[MergeApplyJob].schema

  /** The `jobs.kind` of a merge apply. */
  val JobKind = com.histmersion.api.JobKind.MergeApply.wire

  /** What an interrupted apply says. */
  val InterruptedText: String =
    "the server stopped while this merge apply ran — its transaction was committed or rolled back as a whole, " +
      "never half applied; `histmersion merge list` shows whether it was applied, and planning the bundle again " +
      "shows what is left"

  /** A stored apply as the API answers it: the `jobs` row's state (a `failed` row whose error is
    * [[JobState.Interrupted]] was cut short by a restart), its bundle and plan, and the result it
    * recorded. Pure. */
  def of(id: String, status: JobState, bundle: String, planHash: String, elapsedMs: Long,
      error: Option[String], result: Option[MergeApplyResult]): MergeApplyJob =
    status match
      case JobState.Queued | JobState.Running => MergeApplyJob(id, MergeJobState.Running, bundle, planHash, elapsedMs)
      case JobState.Done =>
        result.fold(MergeApplyJob(id, MergeJobState.Failed, bundle, planHash, elapsedMs,
          error = Some("the merge apply finished but its result was not recorded — `histmersion merge list` shows what was applied")))(
          r => MergeApplyJob(id, MergeJobState.Done, bundle, planHash, elapsedMs, result = Some(r)))
      case JobState.Failed if error.contains(JobState.Interrupted) =>
        MergeApplyJob(id, MergeJobState.Interrupted, bundle, planHash, elapsedMs, error = Some(InterruptedText))
      // a merge apply is never paused or cancelled: one in either state did not apply
      case JobState.Failed | JobState.Paused | JobState.Cancelled =>
        MergeApplyJob(id, MergeJobState.Failed, bundle, planHash, elapsedMs, error = error)

// ── the review screen's draft (`merge_sessions`, V051) ───────────────────────────────────────────

/** The decisions made so far on the review screen for one uploaded bundle: kept on the server, so a
  * reload or another browser picks up where the review was, and exported as the resolution file. */
final case class MergeSession(bundle: String, resolutions: MergeResolutions = MergeResolutions(),
    updatedAt: Option[String] = None) derives KindlingsCodecAsObject
object MergeSession:
  given Schema[MergeSession] = KindlingsSchema.derived[MergeSession].schema

// ── merge list | show | ledger (M7) ───────────────────────────────────────────────────────────────

/** One row of `merges`: an import or an apply that changed something. */
final case class MergeInfo(
    id: UUID,
    bundleId: UUID,
    bundleSha256: String,
    from: BundleInstance,
    appliedAt: String,
    /** Set by `merge apply` (the plan it carried out); an import without matching has none. */
    planHash: Option[String] = None,
    /** The backup taken before the apply (its file name in `backups.dir`). */
    backup: Option[String] = None,
    /** Ledger rows and redirects this merge left. */
    ledgerRows: Int = 0,
    redirects: Int = 0
) derives KindlingsCodecAsObject
object MergeInfo:
  given Schema[MergeInfo] = KindlingsSchema.derived[MergeInfo].schema

/** `GET /api/merges/{id}`: the row, its report as it was written (an apply's or an import's), the
  * resolutions it was given and the decisions it left to remember. */
final case class MergeDetail(
    merge: MergeInfo,
    applied: Option[MergeApplyResult] = None,
    imported: Option[BundleImportResult] = None,
    resolutions: Option[MergeResolutions] = None,
    remembered: List[MergeResolution] = Nil
) derives KindlingsCodecAsObject
object MergeDetail:
  given Schema[MergeDetail] = KindlingsSchema.derived[MergeDetail].schema

/** One row of `merge_ledger`: the bundle's uid, the record here it maps to (and its label now), the
  * revs merged, and the decision. */
final case class LedgerRow(
    foreignUid: UUID,
    kind: String,
    mergeId: UUID,
    foreignRev: String,
    localUid: UUID,
    label: Option[String],
    localRevAfter: Option[String],
    decision: String,
    recordedAt: String
) derives KindlingsCodecAsObject
object LedgerRow:
  given Schema[LedgerRow] = KindlingsSchema.derived[LedgerRow].schema

/** A merged-away uid and the record here it redirects to (`uid_aliases`). */
final case class UidRedirect(aliasUid: UUID, uid: UUID, kind: String, mergeId: Option[UUID]) derives KindlingsCodecAsObject
object UidRedirect:
  given Schema[UidRedirect] = KindlingsSchema.derived[UidRedirect].schema

/** `GET /api/merges/ledger?uid=&merge=&limit=`: the ledger rows (of one record — a uid, here or
  * merged away, or a citekey — or of one merge, or the latest) and the redirects that name it. */
final case class MergeLedger(query: Option[String], entries: List[LedgerRow], redirects: List[UidRedirect] = Nil)
    derives KindlingsCodecAsObject
object MergeLedger:
  given Schema[MergeLedger] = KindlingsSchema.derived[MergeLedger].schema
