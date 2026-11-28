package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.research.workflows.Finding
import com.histmersion.domain.research.workflows.Quotation
import com.histmersion.Newtype

final case class Check(id: Check.Id, fixed: Check.Fixed, quote: Check.Quote, note: Check.Note)

object Check:
  export Finding.Id
  type Fixed = Fixed.T
  object Fixed extends Newtype[Boolean, Pure]
  export Quotation.Quote
  type Note = Note.T
  object Note extends Newtype[String, Pure]
