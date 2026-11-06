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
    type WireOfResult = WireOfResult.T
    object WireOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def wireOf(k: Kind): com.histmersion.util.data.exchange.Issue.Kind.WireOfResult = k match
      case SameWork                => _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult("same-work")
      case CitekeyCollision        => _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult("citekey-collision")
      case DoiDiffers              => _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult("doi-differs")
      case YearDiffers             => _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult("year-differs")
      case StatusDiffers           => _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult("status-differs")
      case TitleDiffers            => _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult("title-differs")
      case AuthorsDiffers          => _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult("authors-differs")
      case SummaryDiffers          => _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult("summary-differs")
      case NotesDiffers            => _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult("notes-differs")
      case PeriodDiffers           => _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult("period-differs")
      case BodyDiffers             => _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult("body-differs")
      case ChangedBothSides        => _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult("changed-both-sides")
      case DeletedThereChangedHere => _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult("deleted-there-changed-here")
      case ArticleDiverged         => _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult("article-diverged")
      case LabelTaken              => _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult("label-taken")
      case AliasConflict           => _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult("alias-conflict")
      case FindingNearDuplicate    => _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult("finding-near-duplicate")
      case AttestationUnanchored   => _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult("attestation-unanchored")
      case PlaceDiffers            => _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult("place-differs")
      case NamedPeriodDiffers      => _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult("named-period-differs")
      case GoalPlanDiffers         => _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult("goal-plan-differs")

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): _root_.com.histmersion.WireEnumValues.WireResult = _root_.com.histmersion.WireEnumValues.WireResult((wireOf(e)).value)
      def fromWire(s: _root_.com.histmersion.WireEnumValues.FromWireS): _root_.scala.Option[Kind] = Kind.values.find(k => wireOf(k) == _root_.com.histmersion.util.data.exchange.Issue.Kind.WireOfResult((s).value.trim))

  /** How much an issue holds the merge up. */
  enum Severity:
    /** Nothing is merged until it has a decision. */
    case Blocking

    /** Has a default that is applied unless overridden. */
    case Default

    /** Reported only. */
    case Info

  object Severity:
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given WireEnumValues[Severity]:
      def all: Array[Severity] = Severity.values
      def wire(e: Severity): _root_.com.histmersion.WireEnumValues.WireResult = _root_.com.histmersion.WireEnumValues.WireResult((Ascii.lower(_root_.com.histmersion.Ascii.LowerS(e.toString))).value)
      def fromWire(s: _root_.com.histmersion.WireEnumValues.FromWireS): _root_.scala.Option[Severity] = Severity.values.find(_.toString.equalsIgnoreCase((s).value.trim))

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
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given WireEnumValues[DecidedBy]:
      def all: Array[DecidedBy] = DecidedBy.values
      def wire(e: DecidedBy): _root_.com.histmersion.WireEnumValues.WireResult = _root_.com.histmersion.WireEnumValues.WireResult((Ascii.lower(_root_.com.histmersion.Ascii.LowerS(e.toString))).value)
      def fromWire(s: _root_.com.histmersion.WireEnumValues.FromWireS): _root_.scala.Option[DecidedBy] = DecidedBy.values.find(_.toString.equalsIgnoreCase((s).value.trim))
