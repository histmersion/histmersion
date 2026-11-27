package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.TimeSpan
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class EventDating(
    eventId: Event.Id,
    citekey: Citekey,
    timeSpan: TimeSpan,
    method: Option[EventDating.Method] = None,
    note: Option[EventDating.Note] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object EventDating:
  type Method = Method.T
  object Method extends Newtype[String, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]
