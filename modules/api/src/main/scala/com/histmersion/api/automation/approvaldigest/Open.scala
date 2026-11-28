package com.histmersion
package api.automation.approvaldigest

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Approval

final case class Open(id: Open.Id, kind: Open.Kind, subject: Option[Open.Subject], createdAt: Open.CreatedAt)

object Open:
  type Id = Id.T
  object Id extends Newtype[Long, Pure]
  export Approval.Kind.Wire as Kind
  type Subject = Subject.T
  object Subject extends Newtype[String, Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends Newtype[String, Pure]
