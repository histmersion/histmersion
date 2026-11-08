package com.histmersion
package domain.library.acquisition

import com.histmersion.plugin.Doi
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
final case class UpdateNotice(kind: Option[UpdateNotice.Kind], label: Option[UpdateNotice.Label], doi: Option[Doi])

object UpdateNotice:
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type Label = Label.T
  object Label extends RefinedType[String, Pure]
