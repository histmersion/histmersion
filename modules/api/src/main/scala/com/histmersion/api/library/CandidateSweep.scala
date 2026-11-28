package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.Schema
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class CandidateSweep(
    goal: CandidateSweep.Goal,
    watches: List[CandidateWatch],
    found: CandidateSweep.Found,
    added: CandidateSweep.Added,
    again: CandidateSweep.Again,
    skipped: Map[CandidateSweep.SkippedKey, CandidateSweep.SkippedValue],
    unseen: CandidateSweep.Unseen,
    dryRun: CandidateSweep.DryRun = CandidateSweep.DryRun.Apply,
    notes: List[CandidateSweep.Notes] = Nil
) derives JsonCodec,
      KindlingsSchema,
      Schema
object CandidateSweep:

  export api.research.ReviewGoal.Slug as Goal
  type Found = Found.T
  object Found extends Newtype[Int, Pure]
  type Added = Added.T
  object Added extends Newtype[Int, Pure]
  type Again = Again.T
  object Again extends Newtype[Int, Pure]
  type SkippedKey = SkippedKey.T
  object SkippedKey extends Newtype[String, Pure]
  type SkippedValue = SkippedValue.T
  object SkippedValue extends Newtype[Int, Pure]
  type Unseen = Unseen.T
  object Unseen extends Newtype[Int, Pure]
  enum DryRun derives CanEqual:
    case DryRun, Apply
  object DryRun extends TwoStates[DryRun]:
    extension (dryRun: DryRun) def dryRun: Boolean = dryRun == DryRun
  type Notes = Notes.T
  object Notes extends Newtype[String, Pure]

  final case class Request(
      goal: Request.Goal,
      dryRun: Request.DryRun = Request.DryRun.Apply,
      createdBy: Option[Creation.By] = None,
      via: Option[Creation.Via] = None
  ) extends Creation.Requested derives JsonCodec, KindlingsSchema, Schema

  object Request:
    export api.research.ReviewGoal.Slug as Goal
    enum DryRun derives CanEqual:
      case DryRun, Apply
    object DryRun extends TwoStates[DryRun]:
      extension (dryRun: DryRun) def dryRun: Boolean = dryRun == DryRun
