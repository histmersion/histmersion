package com.histmersion
package upstreams.unpaywall

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
final case class OaAccess(isOa: OaAccess.IsOa, status: Option[OaAccess.Status], url: Option[OaAccess.Url])

object OaAccess:
  type IsOa = IsOa.T
  object IsOa extends RefinedType[Boolean, Pure]
  type Status = Status.T
  object Status extends RefinedType[String, Pure]
  type Url = Url.T
  object Url extends RefinedType[String, Pure]
