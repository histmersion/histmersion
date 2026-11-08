package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.library.Finding
import io.github.iltotore.iron.constraint.any.Pure

final case class EventLinkCandidate(
    eventId: Event.Id,
    citekey: Citekey,
    score: EventLinkCandidate.Score,
    status: EventLinkCandidate.StatusValue = EventLinkCandidate.StatusValue(EventLinkCandidate.Status.Proposed),
    title: Option[Title] = None,
    findings: List[Finding.Id] = Nil,
    passages: List[EventPassage] = Nil,
    reasons: List[EventLinkCandidate.Reasons] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema
object EventLinkCandidate:

  /** Where a candidate link between an event and a source stands. */
  type Score = Score.T
  object Score extends Newtype[Double, Pure]
  type StatusValue = StatusValue.T
  object StatusValue extends Newtype[String, Pure]
  type Reasons = Reasons.T
  object Reasons extends Newtype[String, Pure]

  object Status:
    type All = All.T
    object All extends Newtype[String, Pure]

    val Proposed = "proposed"
    val Confirmed = "confirmed"
    val Rejected = "rejected"
    val all: List[All] = List(All(Proposed), All(Confirmed), All(Rejected))
