package com.histmersion
package api.personal
import com.histmersion.api.json.NewtypeCodecs.given
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.util.json.JsonCodec

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema
import com.histmersion.lint.{tolerated, LintRule}

type ItemKey = ItemKey.T

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object ItemKey extends Newtype[String, Pure]:
  given JsonValueCodec[ItemKey] = JsonCodec.mapped[ItemKey, String](_.value, s => Right(ItemKey(s.trim)))
  given Schema[ItemKey] = Schema.string[ItemKey]
