package com.histmersion
package api.automation.modelchains

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class AllowanceStatus(
    target: AllowanceStatus.Target,
    source: SettingSource.Wire,
    reservePct: AllowanceStatus.ReservePct,
    limits: List[LimitStatus],
    usable: AllowanceStatus.Usable,
    freesAt: Option[AllowanceStatus.FreesAt] = None,
    note: Option[AllowanceStatus.Note] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object AllowanceStatus:
  type Target = Target.T
  object Target extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  export Allowance.ReservePct
  enum Usable derives CanEqual:
    case Usable, Unusable
  object Usable extends TwoStates[Usable]:
    extension (usable: Usable) def usable: Boolean = usable == Usable
  export LimitStatus.FreesAt
  type Note = Note.T
  object Note extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
