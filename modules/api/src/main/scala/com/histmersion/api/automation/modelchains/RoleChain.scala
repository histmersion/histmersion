package com.histmersion
package api.automation.modelchains

import com.histmersion.util.problem.Problem
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Wording

final case class RoleChain(
    role: RoleChain.Role,
    source: SettingSource.Wire,
    models: List[ChainEntry],
    active: Option[RoleChain.Active] = None,
    problems: List[Problem] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object RoleChain:
  type Role = Role.T
  object Role extends Newtype[String, Pure]:
    given Display[T] = Display(role => Wording.humanize(Wording.Id(role.value)))
  type Active = Active.T
  object Active extends Newtype[String, Pure]
