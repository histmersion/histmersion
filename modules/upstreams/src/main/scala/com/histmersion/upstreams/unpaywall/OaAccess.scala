package com.histmersion
package upstreams.unpaywall

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.acquisition.WorkRef
final case class OaAccess(isOa: OaAccess.IsOa, status: Option[OaAccess.Status], url: Option[OaAccess.Url])

object OaAccess:
  export WorkRef.IsOa
  type Status = Status.T
  object Status extends Newtype[String, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]
