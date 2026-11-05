package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class EventLinkCandidate(
    eventId: com.histmersion.api.catalogue.Event.Id,
    citekey: com.histmersion.api.library.Citekey,
    score: EventLinkCandidate.Score,
    status: EventLinkCandidate.StatusValue = EventLinkCandidate.Status.Proposed,
    title: Option[com.histmersion.api.library.Title] = None,
    findings: List[com.histmersion.api.library.Finding.Id] = Nil,
    passages: List[EventPassage] = Nil,
    reasons: List[EventLinkCandidate.Reasons] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object EventLinkCandidate:

  /** Where a candidate link between an event and a source stands. */
  type Score = Score.T
  object Score extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusValue = StatusValue.T
  object StatusValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reasons = Reasons.T
  object Reasons extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Status:
    val Proposed = "proposed"
    val Confirmed = "confirmed"
    val Rejected = "rejected"
    val all: List[String] = List(Proposed, Confirmed, Rejected)
