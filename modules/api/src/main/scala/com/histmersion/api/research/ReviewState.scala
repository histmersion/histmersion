package com.histmersion
package api.research
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

enum ReviewState(val wire: ReviewState.Wire, val meaning: ReviewState.Meaning) derives CanEqual:
  case Unreviewed
      extends ReviewState(
        ReviewState.Wire("unreviewed"),
        ReviewState.Meaning("no review of this article is recorded and none is queued")
      )
  case Queued
      extends ReviewState(ReviewState.Wire("queued"), ReviewState.Meaning("waiting for the review panel to take it up"))
  case InReview
      extends ReviewState(ReviewState.Wire("in-review"), ReviewState.Meaning("the review panel is working on it now"))
  case Passed extends ReviewState(ReviewState.Wire("passed"), ReviewState.Meaning("reviewed: no MAJOR finding is open"))
  case MajorsOpen
      extends ReviewState(
        ReviewState.Wire("majors-open"),
        ReviewState.Meaning("reviewed: MAJOR findings are still open")
      )
  case Stopped
      extends ReviewState(
        ReviewState.Wire("stopped"),
        ReviewState.Meaning("the review manager gave up on it; a person decides what happens next")
      )

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object ReviewState:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  type Meaning = Meaning.T
  object Meaning extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[ReviewState] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
  given WireEnumValues[ReviewState]:
    def all: Array[ReviewState] = values
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire(e: ReviewState): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromWire(s: WireEnumValues.Wire): Option[ReviewState] = parse(Wire((s).value))
  given JsonValueCodec[ReviewState] = WireEnum.codec
  given Schema[ReviewState] = WireEnum.schema
