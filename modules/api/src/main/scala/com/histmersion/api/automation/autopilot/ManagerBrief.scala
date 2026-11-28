package com.histmersion
package api.automation.autopilot

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Wording
import com.histmersion.api.automation.ItemStatus

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class ManagerBrief(
    id: ManagerBrief.Id,
    kind: ManagerBrief.Kind,
    state: ManagerBrief.State,
    note: Option[ManagerBrief.Note] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ManagerBrief:
  export Wording.Id
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  export ItemStatus.Wire as State
  type Note = Note.T
  object Note extends Newtype[String, Pure]
