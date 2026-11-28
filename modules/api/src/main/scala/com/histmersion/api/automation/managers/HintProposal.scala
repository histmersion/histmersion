package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Managers.Rationale
import io.github.iltotore.iron.constraint.any.Pure

final case class HintProposal(
    managerId: ManagerInfo.Id,
    hint: HintProposal.Hint,
    text: HintProposal.Text,
    effect: HintEffect,
    by: HintProposal.By = HintProposal.By(""),
    why: Rationale = Rationale("")
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object HintProposal:
  type Hint = Hint.T
  object Hint extends Newtype[Long, Pure]:
    given Display[T] = Display(hint => s"${hint.value}")
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  type By = By.T
  object By extends Newtype[String, Pure]
