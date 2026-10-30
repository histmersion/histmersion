package com.histmersion.api

import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.values.WireEnumValues

/** Where an article stands with the `review-article` panel (rules reviewer, cold reader, skeptic and the mechanical
  * checks), as one word: derived by the server from the review manager's item for the article and the article's review
  * ledger (`domain.ArticleReviewStatus`).
  */
enum ReviewState(val wire: String, val meaning: String):
  case Unreviewed extends ReviewState("unreviewed", "no review of this article is recorded and none is queued")
  case Queued extends ReviewState("queued", "waiting for the review panel to take it up")
  case InReview extends ReviewState("in-review", "the review panel is working on it now")
  case Passed extends ReviewState("passed", "reviewed: no MAJOR finding is open")
  case MajorsOpen extends ReviewState("majors-open", "reviewed: MAJOR findings are still open")
  case Stopped extends ReviewState("stopped", "the review manager gave up on it; a person decides what happens next")

object ReviewState:
  def parse(s: String): Option[ReviewState] = values.find(_.wire == s.trim.toLowerCase)
  given WireEnumValues[ReviewState]:
    def all: Array[ReviewState] = ReviewState.values
    def wire(e: ReviewState): String = e.wire
    def fromWire(s: String): Option[ReviewState] = parse(s)
  given Encoder[ReviewState] = WireEnum.encoder
  given Decoder[ReviewState] = WireEnum.decoder
  given Schema[ReviewState] = WireEnum.schema
