package com.histmersion
package domain.reviewarticle

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.reviewarticle.Finding
import com.histmersion.domain.reviewarticle.Quotation
import com.histmersion.Newtype

final case class Check(id: Check.Id, fixed: Check.Fixed, quote: Check.Quote, note: Check.Note) derives JsonCodec

object Check:
  export Finding.Id
  type Fixed = Fixed.T
  object Fixed extends Newtype[Boolean, Pure]
  export Quotation.Quote
  type Note = Note.T
  object Note extends Newtype[String, Pure]
