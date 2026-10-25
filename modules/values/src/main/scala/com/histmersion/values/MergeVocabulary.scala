package com.histmersion.values

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
    def wire(e: IssueSeverity): String             = Ascii.lower(e.toString)
    def fromWire(s: String): Option[IssueSeverity] = IssueSeverity.values.find(_.toString.equalsIgnoreCase(s.trim))

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
  /** Two findings of a merged pair say nearly the same thing (a default: keep both). */
  case FindingNearDuplicate
  /** An attestation's quote is not found in the OCR body the merge keeps. */
  case AttestationUnanchored
  /** A place added on both sides under one id, with a different geometry or time span. */
  case PlaceDiffers
  /** A named period added on both sides under one id, with a different span or label. */
  case NamedPeriodDiffers
  /** A goal that is one on both sides has two different approved plans, neither newer. */
  case GoalPlanDiffers

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
    case FindingNearDuplicate    => "finding-near-duplicate"
    case AttestationUnanchored   => "attestation-unanchored"
    case PlaceDiffers            => "place-differs"
    case NamedPeriodDiffers      => "named-period-differs"
    case GoalPlanDiffers         => "goal-plan-differs"

  given WireEnumValues[IssueKind]:
    def all: Array[IssueKind]                  = IssueKind.values
    def wire(e: IssueKind): String             = wireOf(e)
    def fromWire(s: String): Option[IssueKind] = IssueKind.values.find(k => wireOf(k) == s.trim)

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
  /** Two records are one: fold the bundle's into this collection's (a near-duplicate finding). */
  case Merge
  /** Leave the bundle's record out (an attestation that cannot be re-anchored). */
  case Drop
  /** Keep the attestation without a position in the text (its quote is kept). */
  case KeepUnanchored

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
    case Merge       => "merge"
    case Drop        => "drop"
    case KeepUnanchored => "keep-unanchored"

  given WireEnumValues[DecisionKind]:
    def all: Array[DecisionKind]                  = DecisionKind.values
    def wire(e: DecisionKind): String             = wireOf(e)
    def fromWire(s: String): Option[DecisionKind] = DecisionKind.values.find(k => wireOf(k) == s.trim)

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

/** `--editions`: whether a work found again under a year far from ours is the same source. */
enum EditionsPolicy:
  /** Merge them (the year issue then takes the earliest year). */
  case Merge
  /** Keep them as two sources. */
  case Separate

object EditionsPolicy:
  given WireEnumValues[EditionsPolicy]:
    def all: Array[EditionsPolicy]                  = EditionsPolicy.values
    def wire(e: EditionsPolicy): String             = Ascii.lower(e.toString)
    def fromWire(s: String): Option[EditionsPolicy] = EditionsPolicy.values.find(_.toString.equalsIgnoreCase(s.trim))
