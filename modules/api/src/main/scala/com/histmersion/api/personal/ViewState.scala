package com.histmersion
package api.personal
import com.histmersion.api.json.NewtypeCodecs.given
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.util.json.JsonCodec

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema
import com.histmersion.lint.{tolerated, LintRule}

type ViewState = ViewState.T

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object ViewState extends Newtype[String, Pure]:
  val MaxChars = 8192
  given JsonValueCodec[ViewState] =
    JsonCodec.mapped[ViewState, String](_.value, s => Right(ViewState(s.trim.stripPrefix("#"))))
  given Schema[ViewState] = Schema.string[ViewState]
