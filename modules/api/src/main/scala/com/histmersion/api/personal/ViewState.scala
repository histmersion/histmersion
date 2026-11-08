package com.histmersion
package api.personal

import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema

type ViewState = ViewState.T

object ViewState extends Newtype[String, Pure]:
  val MaxChars = 8192
  given Encoder[ViewState] = Encoder.encodeString.contramap(_.value)
  given Decoder[ViewState] = Decoder.decodeString.map(s => ViewState(s.trim.stripPrefix("#")))
  given Schema[ViewState] = Schema.string[ViewState]
