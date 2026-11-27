package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class EventParticipant(
    eventId: Event.Id,
    actor: EventParticipant.Actor,
    role: EventParticipant.Role = EventParticipant.Role("participant"),
    citekey: Option[Citekey] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object EventParticipant:
  type Actor = Actor.T
  object Actor extends Newtype[String, Pure]
  type Role = Role.T
  object Role extends Newtype[String, Pure]
