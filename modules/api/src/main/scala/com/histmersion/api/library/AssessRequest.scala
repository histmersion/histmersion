package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Creation
import com.histmersion.api.json.TwoStates

final case class AssessRequest(
    id: AssessRequest.Id,
    apply: AssessRequest.Apply = AssessRequest.Apply.DryRun,
    forceStatus: AssessRequest.ForceStatus = AssessRequest.ForceStatus.KeepStatus,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

object AssessRequest:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  enum Apply derives CanEqual:
    case Apply, DryRun
  object Apply extends TwoStates[Apply]:
    extension (apply: Apply) def applies: Boolean = apply == Apply
  enum ForceStatus derives CanEqual:
    case ForceStatus, KeepStatus
  object ForceStatus extends TwoStates[ForceStatus]:
    extension (forceStatus: ForceStatus) def forceStatus: Boolean = forceStatus == ForceStatus
