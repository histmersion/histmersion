package com.histmersion
package api.automation.queue

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype
import com.histmersion.api.automation.queue.Docket

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class QueueImported(
    list: QueueImported.List,
    items: QueueImported.Items,
    notes: QueueImported.Notes,
    replaced: QueueImported.Replaced
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object QueueImported:
  export Docket.Label as List
  type Items = Items.T
  object Items extends Newtype[Int, Pure]
  type Notes = Notes.T
  object Notes extends Newtype[Int, Pure]
  type Replaced = Replaced.T
  object Replaced extends Newtype[Int, Pure]
