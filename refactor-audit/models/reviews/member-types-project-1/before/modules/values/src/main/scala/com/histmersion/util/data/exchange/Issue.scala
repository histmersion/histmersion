package com.histmersion.util.data.exchange

import com.histmersion.WireEnumValues
import com.histmersion.{Ascii, WireEnumValues}

object Issue:

  /** What an issue is about. The wire name is part of the issue id, so it never changes. */
  enum Kind:
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

  object Kind:
    def wireOf(k: Kind): String = k match
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

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): String = wireOf(e)
      def fromWire(s: String): Option[Kind] = Kind.values.find(k => wireOf(k) == s.trim)

  /** How much an issue holds the merge up. */
  enum Severity:
    /** Nothing is merged until it has a decision. */
    case Blocking

    /** Has a default that is applied unless overridden. */
    case Default

    /** Reported only. */
    case Info

  object Severity:
    given WireEnumValues[Severity]:
      def all: Array[Severity] = Severity.values
      def wire(e: Severity): String = Ascii.lower(e.toString)
      def fromWire(s: String): Option[Severity] = Severity.values.find(_.toString.equalsIgnoreCase(s.trim))

  /** Who decided an issue. */
  enum DecidedBy:
    /** A resolution (the file or `--resolve`). */
    case Resolution

    /** A policy (`--editions`). */
    case Policy

    /** The issue's default, nobody overrode it. */
    case Default

    /** The decision an earlier `merge apply` carried out for this very issue (the same records, unchanged): remembered,
      * as git's rerere does, so a decision is never asked twice.
      */
    case Remembered

  object DecidedBy:
    given WireEnumValues[DecidedBy]:
      def all: Array[DecidedBy] = DecidedBy.values
      def wire(e: DecidedBy): String = Ascii.lower(e.toString)
      def fromWire(s: String): Option[DecidedBy] = DecidedBy.values.find(_.toString.equalsIgnoreCase(s.trim))
