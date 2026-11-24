package com.histmersion
package api.personal

import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.ItemKind
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Title
import com.histmersion.api.library.AuthorName
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class UnreadRow(
    kind: ItemKind,
    id: ItemKey,
    title: Title,
    authors: List[AuthorName] = Nil,
    year: Option[Year] = None,
    status: Option[UnreadRow.Status] = None,
    periodStart: Option[Year] = None,
    periodEnd: Option[Year] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object UnreadRow:
  type Status = Status.T
  object Status extends Newtype[String, Pure]
