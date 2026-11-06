package com.histmersion.api.research
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given

enum ReviewState(val wire: ReviewState.Wire, val meaning: ReviewState.Meaning):
  case Unreviewed extends ReviewState(_root_.com.histmersion.api.research.ReviewState.Wire("unreviewed"), _root_.com.histmersion.api.research.ReviewState.Meaning("no review of this article is recorded and none is queued"))
  case Queued extends ReviewState(_root_.com.histmersion.api.research.ReviewState.Wire("queued"), _root_.com.histmersion.api.research.ReviewState.Meaning("waiting for the review panel to take it up"))
  case InReview extends ReviewState(_root_.com.histmersion.api.research.ReviewState.Wire("in-review"), _root_.com.histmersion.api.research.ReviewState.Meaning("the review panel is working on it now"))
  case Passed extends ReviewState(_root_.com.histmersion.api.research.ReviewState.Wire("passed"), _root_.com.histmersion.api.research.ReviewState.Meaning("reviewed: no MAJOR finding is open"))
  case MajorsOpen extends ReviewState(_root_.com.histmersion.api.research.ReviewState.Wire("majors-open"), _root_.com.histmersion.api.research.ReviewState.Meaning("reviewed: MAJOR findings are still open"))
  case Stopped extends ReviewState(_root_.com.histmersion.api.research.ReviewState.Wire("stopped"), _root_.com.histmersion.api.research.ReviewState.Meaning("the review manager gave up on it; a person decides what happens next"))

object ReviewState:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Meaning = Meaning.T
  object Meaning extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.api.research.ReviewState.ParseS): Option[ReviewState] = values.find(_.wire == _root_.com.histmersion.api.research.ReviewState.Wire((s).value.trim.toLowerCase))
  given WireEnumValues[ReviewState]:
    def all: Array[ReviewState] = ReviewState.values
    def wire(e: ReviewState): _root_.com.histmersion.api.WireEnumValues.WireResult = _root_.com.histmersion.api.WireEnumValues.WireResult((e.wire).value)
    def fromWire(s: _root_.com.histmersion.api.WireEnumValues.FromWireS): _root_.scala.Option[ReviewState] = parse(_root_.com.histmersion.api.research.ReviewState.ParseS((s).value))
  given Encoder[ReviewState] = WireEnum.encoder
  given Decoder[ReviewState] = WireEnum.decoder
  given Schema[ReviewState] = WireEnum.schema
