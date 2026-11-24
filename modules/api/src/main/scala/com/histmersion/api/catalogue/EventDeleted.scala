package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class EventDeleted(id: Event.Id, deleted: EventDeleted.Deleted)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object EventDeleted:
  type Deleted = Deleted.T
  object Deleted extends Newtype[Boolean, Pure]
