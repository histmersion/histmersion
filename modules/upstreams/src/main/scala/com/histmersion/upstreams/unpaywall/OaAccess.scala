package com.histmersion
package upstreams.unpaywall

import io.github.iltotore.iron.constraint.any.Pure
final case class OaAccess(isOa: OaAccess.IsOa, status: Option[OaAccess.Status], url: Option[OaAccess.Url])

object OaAccess:
  type IsOa = IsOa.T
  object IsOa extends Newtype[Boolean, Pure]
  type Status = Status.T
  object Status extends Newtype[String, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]
