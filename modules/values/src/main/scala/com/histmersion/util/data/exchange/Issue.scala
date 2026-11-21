package com.histmersion
package util.data.exchange

import com.histmersion.WireEnumValues
import com.histmersion.{Ascii, WireEnumValues}
import io.github.iltotore.iron.constraint.any.Pure

object Issue:

  enum Kind:
    case SameWork

    case CitekeyCollision

    case DoiDiffers, YearDiffers, StatusDiffers, TitleDiffers, AuthorsDiffers, SummaryDiffers, NotesDiffers,
      PeriodDiffers

    case BodyDiffers

    case ChangedBothSides

    case DeletedThereChangedHere

    case ArticleDiverged

    case LabelTaken

    case AliasConflict

    case FindingNearDuplicate

    case AttestationUnanchored

    case PlaceDiffers

    case NamedPeriodDiffers

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

  enum Severity:
    case Blocking

    case Default

    case Info

  object Severity:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    given WireEnumValues[Severity]:
      def all: Array[Severity] = values
      def wire(e: Severity): WireEnumValues.Wire = WireEnumValues.Wire((Ascii.lower((e.toString))))
      def fromWire(s: WireEnumValues.Wire): Option[Severity] = values.find(_.toString.equalsIgnoreCase((s).value.trim))

  enum DecidedBy:
    case Resolution

    case Policy

    case Default

    case Remembered

  object DecidedBy:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    given WireEnumValues[DecidedBy]:
      def all: Array[DecidedBy] = values
      def wire(e: DecidedBy): WireEnumValues.Wire = WireEnumValues.Wire((Ascii.lower((e.toString))))
      def fromWire(s: WireEnumValues.Wire): Option[DecidedBy] = values.find(_.toString.equalsIgnoreCase((s).value.trim))
