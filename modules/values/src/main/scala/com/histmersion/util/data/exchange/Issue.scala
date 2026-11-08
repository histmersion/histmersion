package com.histmersion
package util.data.exchange

import com.histmersion.WireEnumValues
import com.histmersion.{Ascii, WireEnumValues}
import io.github.iltotore.iron.constraint.any.Pure

object Issue:

  /** What an issue is about. The wire name is part of the issue id, so it never changes. */
  enum Kind derives CanEqual:
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
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def wireOf(k: Kind): Wire = k match
      case SameWork                => Wire("same-work")
      case CitekeyCollision        => Wire("citekey-collision")
      case DoiDiffers              => Wire("doi-differs")
      case YearDiffers             => Wire("year-differs")
      case StatusDiffers           => Wire("status-differs")
      case TitleDiffers            => Wire("title-differs")
      case AuthorsDiffers          => Wire("authors-differs")
      case SummaryDiffers          => Wire("summary-differs")
      case NotesDiffers            => Wire("notes-differs")
      case PeriodDiffers           => Wire("period-differs")
      case BodyDiffers             => Wire("body-differs")
      case ChangedBothSides        => Wire("changed-both-sides")
      case DeletedThereChangedHere => Wire("deleted-there-changed-here")
      case ArticleDiverged         => Wire("article-diverged")
      case LabelTaken              => Wire("label-taken")
      case AliasConflict           => Wire("alias-conflict")
      case FindingNearDuplicate    => Wire("finding-near-duplicate")
      case AttestationUnanchored   => Wire("attestation-unanchored")
      case PlaceDiffers            => Wire("place-differs")
      case NamedPeriodDiffers      => Wire("named-period-differs")
      case GoalPlanDiffers         => Wire("goal-plan-differs")

    given WireEnumValues[Kind]:
      def all: Array[Kind] = values
      def wire(e: Kind): WireEnumValues.Wire = WireEnumValues.Wire((wireOf(e)).value)
      def fromWire(s: WireEnumValues.Wire): Option[Kind] = values.find(k => wireOf(k) == Wire((s).value.trim))

  /** How much an issue holds the merge up. */
  enum Severity derives CanEqual:
    /** Nothing is merged until it has a decision. */
    case Blocking

    /** Has a default that is applied unless overridden. */
    case Default

    /** Reported only. */
    case Info

  object Severity:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    given WireEnumValues[Severity]:
      def all: Array[Severity] = values
      def wire(e: Severity): WireEnumValues.Wire = WireEnumValues.Wire((Ascii.lower((e.toString))))
      def fromWire(s: WireEnumValues.Wire): Option[Severity] = values.find(_.toString.equalsIgnoreCase((s).value.trim))

  /** Who decided an issue. */
  enum DecidedBy derives CanEqual:
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
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    given WireEnumValues[DecidedBy]:
      def all: Array[DecidedBy] = values
      def wire(e: DecidedBy): WireEnumValues.Wire = WireEnumValues.Wire((Ascii.lower((e.toString))))
      def fromWire(s: WireEnumValues.Wire): Option[DecidedBy] = values.find(_.toString.equalsIgnoreCase((s).value.trim))
