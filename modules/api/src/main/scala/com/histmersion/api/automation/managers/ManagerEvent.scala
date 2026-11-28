package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.Agents
import com.histmersion.api.automation.agents.RunInfo
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Wording

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class ManagerEvent(
    id: ManagerEvent.Id,
    managerId: ManagerInfo.Id,
    itemId: Option[ManagerEvent.ItemId] = None,
    runId: Option[RunInfo.Id] = None,
    at: ManagerEvent.At = ManagerEvent.At(""),
    kind: ManagerEvent.Kind = ManagerEvent.Kind(""),
    text: ManagerEvent.Text = ManagerEvent.Text("")
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ManagerEvent:
  type Id = Id.T
  object Id extends Newtype[Long, Pure]
  type ItemId = ItemId.T
  object ItemId extends Newtype[Long, Pure]
  type At = At.T
  object At extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]:
    given Display[T] = Display(kind => Wording.humanize(Wording.Id(kind.value)))
  type Text = Text.T
  object Text extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
