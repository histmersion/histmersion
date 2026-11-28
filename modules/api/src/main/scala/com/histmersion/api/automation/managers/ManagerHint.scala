package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class ManagerHint(
    id: ManagerHint.Id,
    managerId: ManagerInfo.Id,
    itemId: Option[ManagerHint.ItemId] = None,
    text: ManagerHint.Text = ManagerHint.Text(""),
    effect: Option[HintEffect] = None,
    author: ManagerHint.Author = ManagerHint.Author(""),
    standing: ManagerHint.Standing = ManagerHint.Standing.Standing,
    createdAt: ManagerHint.CreatedAt = ManagerHint.CreatedAt(""),
    retiredAt: Option[ManagerHint.RetiredAt] = None,
    uses: ManagerHint.Uses = ManagerHint.Uses(0),
    proposal: Option[ManagerHint.Proposal] = None,
    proposed: Option[HintEffect] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ManagerHint:
  type Id = Id.T
  object Id extends Newtype[Long, Pure]
  type ItemId = ItemId.T
  object ItemId extends Newtype[Long, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  type Author = Author.T
  object Author extends Newtype[String, Pure]
  enum Standing derives CanEqual:
    case Standing, OneOff
  object Standing extends TwoStates[Standing]:
    extension (standing: Standing) def standing: Boolean = standing == Standing
  type CreatedAt = CreatedAt.T
  object CreatedAt extends Newtype[String, Pure]
  type RetiredAt = RetiredAt.T
  object RetiredAt extends Newtype[String, Pure]
  type Uses = Uses.T
  object Uses extends Newtype[Int, Pure]
  type Proposal = Proposal.T
  object Proposal extends Newtype[Long, Pure]
