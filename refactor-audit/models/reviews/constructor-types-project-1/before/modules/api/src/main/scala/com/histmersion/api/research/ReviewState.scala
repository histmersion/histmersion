package com.histmersion.api.research
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given

enum ReviewState(val wire: String, val meaning: String):
  case Unreviewed extends ReviewState("unreviewed", "no review of this article is recorded and none is queued")
  case Queued extends ReviewState("queued", "waiting for the review panel to take it up")
  case InReview extends ReviewState("in-review", "the review panel is working on it now")
  case Passed extends ReviewState("passed", "reviewed: no MAJOR finding is open")
  case MajorsOpen extends ReviewState("majors-open", "reviewed: MAJOR findings are still open")
  case Stopped extends ReviewState("stopped", "the review manager gave up on it; a person decides what happens next")

object ReviewState:
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.api.research.ReviewState.ParseS): Option[ReviewState] = values.find(_.wire == s.trim.toLowerCase)
  given WireEnumValues[ReviewState]:
    def all: Array[ReviewState] = ReviewState.values
    def wire(e: ReviewState): com.histmersion.api.research.ReviewState.WireResult = e.wire
    def fromWire(s: com.histmersion.api.research.ReviewState.FromWireS): Option[ReviewState] = parse(s)
  given Encoder[ReviewState] = WireEnum.encoder
  given Decoder[ReviewState] = WireEnum.decoder
  given Schema[ReviewState] = WireEnum.schema
