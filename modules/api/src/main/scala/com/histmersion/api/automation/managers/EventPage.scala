package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Paging

@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
final case class EventPage(events: List[ManagerEvent], before: Option[EventPage.Before] = None)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object EventPage:
  export Paging.Limit
  type Before = Before.T
  object Before extends Newtype[Long, Pure]
