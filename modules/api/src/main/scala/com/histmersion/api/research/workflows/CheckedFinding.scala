package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class CheckedFinding(id: CheckedFinding.Id, fixed: CheckedFinding.Fixed, quote: CheckedFinding.Quote, note: CheckedFinding.Note) derives JsonCodec

object CheckedFinding:
  export ReviewFinding.Id
  type Fixed = Fixed.T
  object Fixed extends Newtype[Boolean, Pure]
  export Quotation.Quote
  type Note = Note.T
  object Note extends Newtype[String, Pure]
