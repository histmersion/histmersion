package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Creation

final case class EventCandidatesRequest(
    all: EventCandidatesRequest.All = EventCandidatesRequest.All(false),
    event: Option[Event.Id] = None,
    limit: Option[EventCandidatesRequest.Limit] = None,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

object EventCandidatesRequest:
  type All = All.T
  object All extends Newtype[Boolean, Pure]
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
