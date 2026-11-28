package com.histmersion
package api.automation

import com.histmersion.util.problem.Problem

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Citekey
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.Paging

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
object Activity:
  type Actor = Actor.T
  object Actor extends Newtype[String, Pure]
  export Paging.Cursor
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  export Paging.Limit
  type Subject = Subject.T
  object Subject extends Newtype[String, Pure]

  val MaxLimit = 200
  val DefaultLimit = 30
