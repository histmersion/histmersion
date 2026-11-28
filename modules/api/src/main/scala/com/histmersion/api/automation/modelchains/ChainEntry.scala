package com.histmersion
package api.automation.modelchains

import com.histmersion.util.problem.Problem
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

final case class ChainEntry(
    model: ChainEntry.Model,
    ready: ChainEntry.Ready,
    usable: ChainEntry.Usable,
    problems: List[Problem] = Nil,
    freesAt: Option[ChainEntry.FreesAt] = None,
    billing: Option[ChainEntry.Billing] = None,
    cooling: ChainEntry.Cooling = ChainEntry.Cooling.Warm
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ChainEntry:
  type Model = Model.T
  object Model extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  enum Ready derives CanEqual:
    case Ready, Unready
  object Ready extends TwoStates[Ready]:
    extension (ready: Ready) def ready: Boolean = ready == Ready
  enum Usable derives CanEqual:
    case Usable, Unusable
  object Usable extends TwoStates[Usable]:
    extension (usable: Usable) def usable: Boolean = usable == Usable
  export LimitStatus.FreesAt
  type Billing = Billing.T
  object Billing extends Newtype[String, Pure]
  enum Cooling derives CanEqual:
    case Cooling, Warm
  object Cooling extends TwoStates[Cooling]:
    extension (cooling: Cooling) def cooling: Boolean = cooling == Cooling
