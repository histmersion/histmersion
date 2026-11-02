package com.histmersion.values

/** What an issue is about. The wire name is part of the issue id, so it never changes. */

import com.histmersion.WireEnumValues
enum IssueKind:
  /** Two sources may be one work (a score between the thresholds, or a hard flag such as two different DOIs or years
    * far apart): merge them (`map:<citekey>`) or keep both.
    */
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

  /** The bundle's record has a label (event id, goal slug, finding id, asset name) that a different record has here.
    */
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
    def all: Array[IssueKind] = IssueKind.values
    def wire(e: IssueKind): String = wireOf(e)
    def fromWire(s: String): Option[IssueKind] = IssueKind.values.find(k => wireOf(k) == s.trim)
