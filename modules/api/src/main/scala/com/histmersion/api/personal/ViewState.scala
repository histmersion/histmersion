package com.histmersion.api.personal

import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema

/** Explore's state as its address carries it after `#`: the filters, the span, the layers. */
type ViewState = ViewState.T

object ViewState extends RefinedType[String, Pure]:
  /** The most characters a saved view keeps. */
  val MaxChars = 8192
  given Encoder[ViewState] = Encoder.encodeString.contramap(_.value)
  given Decoder[ViewState] = Decoder.decodeString.map(s => ViewState(s.trim.stripPrefix("#")))
  given Schema[ViewState] = Schema.string[ViewState]
