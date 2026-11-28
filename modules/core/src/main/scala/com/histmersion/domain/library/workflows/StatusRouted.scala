package com.histmersion
package domain.library.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class StatusRouted(
    applied: Option[StatusRouted.Applied],
    approval: Option[StatusRouted.Approval],
    note: StatusRouted.Note
) derives JsonCodec

object StatusRouted:
  type Applied = Applied.T
  object Applied extends Newtype[String, Pure]
  type Approval = Approval.T
  object Approval extends Newtype[Long, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]
