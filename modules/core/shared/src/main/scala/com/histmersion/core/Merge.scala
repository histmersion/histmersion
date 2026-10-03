package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

import java.util.UUID

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

/** How much an issue holds the merge up. */
enum IssueSeverity:
  /** Nothing is merged until it has a decision. */
  case Blocking
  /** Has a default that is applied unless overridden. */
  case Default
  /** Reported only. */
  case Info

object IssueSeverity:
  given WireEnumValues[IssueSeverity]:
    def all: Array[IssueSeverity]                  = IssueSeverity.values
    def wire(e: IssueSeverity): String             = e.toString.toLowerCase(java.util.Locale.ROOT)
    def fromWire(s: String): Option[IssueSeverity] = IssueSeverity.values.find(_.toString.equalsIgnoreCase(s.trim))
  given Encoder[IssueSeverity] = WireEnum.encoder
  given Decoder[IssueSeverity] = WireEnum.decoder
  given Schema[IssueSeverity]  = WireEnum.schema

/** What an issue is about. The wire name is part of the issue id, so it never changes. */
enum IssueKind:
  /** Two sources may be one work (a score between the thresholds, or a hard flag such as two
    * different DOIs or years far apart): merge them (`map:<citekey>`) or keep both. */
  case SameWork
  /** One citekey (or an old one, an alias) names two different works. */
  case CitekeyCollision
  /** Two sources that are being merged disagree on a field. */
  case DoiDiffers, YearDiffers, StatusDiffers, TitleDiffers, AuthorsDiffers, SummaryDiffers, NotesDiffers,
    PeriodDiffers
  /** The OCR bodies of two sources that are being merged differ (a default: keep this side's). */
  case BodyDiffers
  /** A record changed on both sides since the last import (the ledger is the common ancestor). */
  case ChangedBothSides
  /** Deleted over there, changed here since the last import. */
  case DeletedThereChangedHere
  /** Two different articles under one slug (created on both sides: no common base). */
  case ArticleDiverged
  /** The bundle's record has a label (event id, goal slug, finding id, asset name) that a different
    * record has here. */
  case LabelTaken
  /** An old citekey of the bundle names a different source here. */
  case AliasConflict

object IssueKind:
  def wireOf(k: IssueKind): String = k match
    case SameWork                => "same-work"
    case CitekeyCollision        => "citekey-collision"
    case DoiDiffers              => "doi-differs"
    case YearDiffers             => "year-differs"
    case StatusDiffers           => "status-differs"
    case TitleDiffers            => "title-differs"
    case AuthorsDiffers          => "authors-differs"
    case SummaryDiffers          => "summary-differs"
    case NotesDiffers            => "notes-differs"
    case PeriodDiffers           => "period-differs"
    case BodyDiffers             => "body-differs"
    case ChangedBothSides        => "changed-both-sides"
    case DeletedThereChangedHere => "deleted-there-changed-here"
    case ArticleDiverged         => "article-diverged"
    case LabelTaken              => "label-taken"
    case AliasConflict           => "alias-conflict"

  given WireEnumValues[IssueKind]:
    def all: Array[IssueKind]                  = IssueKind.values
    def wire(e: IssueKind): String             = wireOf(e)
    def fromWire(s: String): Option[IssueKind] = IssueKind.values.find(k => wireOf(k) == s.trim)
  given Encoder[IssueKind] = WireEnum.encoder
  given Decoder[IssueKind] = WireEnum.decoder
  given Schema[IssueKind]  = WireEnum.schema

/** The kinds of decision, as the resolution file spells them (`map:<citekey>` is [[DecisionKind.Map]]
  * with its argument). */
enum DecisionKind:
  /** Keep this collection's version. */
  case KeepLeft
  /** Take the bundle's version. */
  case KeepRight
  /** Keep both (two sources; the bundle's article under another slug). */
  case Both
  /** Join both texts (summary, notes). */
  case Concat
  /** The smallest period covering both. */
  case Hull
  /** One source, under the given citekey; the other citekey becomes an alias. */
  case Map
  /** Keep both, the bundle's under a new citekey. */
  case RenameRight
  /** Keep both, this collection's under a new citekey. */
  case RenameLeft
  /** The merged text is in a file (an article). */
  case File

  /** `true` when the decision needs an argument (`map:<citekey>`). */
  def takesArgument: Boolean = this match
    case Map | RenameRight | RenameLeft | File => true
    case _                                     => false

object DecisionKind:
  def wireOf(k: DecisionKind): String = k match
    case KeepLeft    => "keep-left"
    case KeepRight   => "keep-right"
    case Both        => "both"
    case Concat      => "concat"
    case Hull        => "hull"
    case Map         => "map"
    case RenameRight => "rename-right"
    case RenameLeft  => "rename-left"
    case File        => "file"

  given WireEnumValues[DecisionKind]:
    def all: Array[DecisionKind]                  = DecisionKind.values
    def wire(e: DecisionKind): String             = wireOf(e)
    def fromWire(s: String): Option[DecisionKind] = DecisionKind.values.find(k => wireOf(k) == s.trim)
  given Encoder[DecisionKind] = WireEnum.encoder
  given Decoder[DecisionKind] = WireEnum.decoder
  given Schema[DecisionKind]  = WireEnum.schema

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

/** Who decided an issue. */
enum DecidedBy:
  /** A resolution (the file or `--resolve`). */
  case Resolution
  /** A policy (`--editions`). */
  case Policy
  /** The issue's default, nobody overrode it. */
  case Default

object DecidedBy:
  given WireEnumValues[DecidedBy]:
    def all: Array[DecidedBy]                  = DecidedBy.values
    def wire(e: DecidedBy): String             = e.toString.toLowerCase(java.util.Locale.ROOT)
    def fromWire(s: String): Option[DecidedBy] = DecidedBy.values.find(_.toString.equalsIgnoreCase(s.trim))
  given Encoder[DecidedBy] = WireEnum.encoder
  given Decoder[DecidedBy] = WireEnum.decoder
  given Schema[DecidedBy]  = WireEnum.schema

/** One side of an issue: the record's label, its uid, and a few lines describing it. */
final case class IssueSide(label: String, uid: UUID, lines: List[String] = Nil) derives KindlingsCodecAsObject
object IssueSide:
  given Schema[IssueSide] = KindlingsSchema.derived[IssueSide].schema

/** An issue that exists only under one alternative of another, still open, issue: `if S-3c1e7a2b = map`.
  * It is listed now so that no new issue appears after the other one is decided (§6.1). */
final case class IssueCondition(issue: String, when: DecisionKind) derives KindlingsCodecAsObject
object IssueCondition:
  given Schema[IssueCondition] = KindlingsSchema.derived[IssueCondition].schema

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
    decidedBy: Option[DecidedBy] = None
) derives KindlingsCodecAsObject:
  /** Open and holding the merge up: blocking, unconditional, undecided. */
  def open: Boolean = severity == IssueSeverity.Blocking && condition.isEmpty && decision.isEmpty
object MergeIssue:
  given Schema[MergeIssue] = KindlingsSchema.derived[MergeIssue].schema

/** How a bundle's source was paired with one here. */
enum MatchMethod:
  /** The same uid. */
  case Uid
  /** A redirect (`uid_aliases`) or the ledger of an earlier merge. */
  case Ledger
  /** A strong key: the same DOI, original file (sha256) or ISBN. */
  case StrongKey
  /** The weighted score. */
  case Score
  /** Nothing here is that work. */
  case Unmatched

object MatchMethod:
  given WireEnumValues[MatchMethod]:
    def all: Array[MatchMethod]                  = MatchMethod.values
    def wire(e: MatchMethod): String             = e.toString.toLowerCase(java.util.Locale.ROOT)
    def fromWire(s: String): Option[MatchMethod] = MatchMethod.values.find(_.toString.equalsIgnoreCase(s.trim))
  given Encoder[MatchMethod] = WireEnum.encoder
  given Decoder[MatchMethod] = WireEnum.decoder
  given Schema[MatchMethod]  = WireEnum.schema

/** What becomes of a bundle's source. */
enum MatchOutcome:
  /** The same record here (by uid or ledger): imported as an update, if anything. */
  case Same
  /** Another record here is the same work, sure enough to merge without asking. */
  case AutoMerge
  /** Another record here may be the same work: a [[IssueKind.SameWork]] issue decides it. */
  case NeedsDecision
  /** A new source. */
  case New
  /** Deleted here: not brought back. */
  case DeletedHere

object MatchOutcome:
  def wireOf(o: MatchOutcome): String = o match
    case Same          => "same"
    case AutoMerge     => "auto-merge"
    case NeedsDecision => "needs-decision"
    case New           => "new"
    case DeletedHere   => "deleted-here"
  given WireEnumValues[MatchOutcome]:
    def all: Array[MatchOutcome]                  = MatchOutcome.values
    def wire(e: MatchOutcome): String             = wireOf(e)
    def fromWire(s: String): Option[MatchOutcome] = MatchOutcome.values.find(o => wireOf(o) == s.trim)
  given Encoder[MatchOutcome] = WireEnum.encoder
  given Decoder[MatchOutcome] = WireEnum.decoder
  given Schema[MatchOutcome]  = WireEnum.schema

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

/** `--editions`: whether a work found again under a year far from ours is the same source. */
enum EditionsPolicy:
  /** Merge them (the year issue then takes the earliest year). */
  case Merge
  /** Keep them as two sources. */
  case Separate

object EditionsPolicy:
  given WireEnumValues[EditionsPolicy]:
    def all: Array[EditionsPolicy]                  = EditionsPolicy.values
    def wire(e: EditionsPolicy): String             = e.toString.toLowerCase(java.util.Locale.ROOT)
    def fromWire(s: String): Option[EditionsPolicy] = EditionsPolicy.values.find(_.toString.equalsIgnoreCase(s.trim))
  given Encoder[EditionsPolicy] = WireEnum.encoder
  given Decoder[EditionsPolicy] = WireEnum.decoder
  given Schema[EditionsPolicy]  = WireEnum.schema

final case class MergePolicy(editions: Option[EditionsPolicy] = None) derives KindlingsCodecAsObject
object MergePolicy:
  given Schema[MergePolicy] = KindlingsSchema.derived[MergePolicy].schema

/** One line of a resolution file: an issue id and the decision, as the user wrote it (checked by the
  * plan, which reports every bad one at once). */
final case class MergeResolution(issue: String, decision: String) derives KindlingsCodecAsObject
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
    resolved: Int
) derives KindlingsCodecAsObject:
  def open: List[MergeIssue] = issues.filter(_.open)
  def blocking: List[MergeIssue] = issues.filter(_.severity == IssueSeverity.Blocking)
  def defaults: List[MergeIssue] = issues.filter(_.severity == IssueSeverity.Default)
object MergePlan:
  given Schema[MergePlan] = KindlingsSchema.derived[MergePlan].schema
