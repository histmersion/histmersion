package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class EventSourceRef(
    citekey: Citekey,
    title: Option[EventSourceRef.Title] = None,
    year: Option[Year] = None,
    via: EventSourceRef.Via = EventSourceRef.Via("citekey"),
    quote: Option[EventSourceRef.Quote] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object EventSourceRef:
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Via = Via.T
  object Via extends Newtype[String, Pure]
  export EventPassage.Quote
