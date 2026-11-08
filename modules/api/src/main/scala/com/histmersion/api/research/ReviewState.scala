package com.histmersion
package api.research
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

enum ReviewState(val wire: ReviewState.Wire, val meaning: ReviewState.Meaning):
  case Unreviewed extends ReviewState(ReviewState.Wire("unreviewed"), ReviewState.Meaning("no review of this article is recorded and none is queued"))
  case Queued extends ReviewState(ReviewState.Wire("queued"), ReviewState.Meaning("waiting for the review panel to take it up"))
  case InReview extends ReviewState(ReviewState.Wire("in-review"), ReviewState.Meaning("the review panel is working on it now"))
  case Passed extends ReviewState(ReviewState.Wire("passed"), ReviewState.Meaning("reviewed: no MAJOR finding is open"))
  case MajorsOpen extends ReviewState(ReviewState.Wire("majors-open"), ReviewState.Meaning("reviewed: MAJOR findings are still open"))
  case Stopped extends ReviewState(ReviewState.Wire("stopped"), ReviewState.Meaning("the review manager gave up on it; a person decides what happens next"))

object ReviewState:
  type Wire = Wire.T
  object Wire extends RefinedType[String, Pure]
  type Meaning = Meaning.T
  object Meaning extends RefinedType[String, Pure]


  def parse(s: Wire): Option[ReviewState] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
  given WireEnumValues[ReviewState]:
    def all: Array[ReviewState] = values
    def wire(e: ReviewState): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
    def fromWire(s: WireEnumValues.Wire): Option[ReviewState] = parse(Wire((s).value))
  given Encoder[ReviewState] = WireEnum.encoder
  given Decoder[ReviewState] = WireEnum.decoder
  given Schema[ReviewState] = WireEnum.schema
