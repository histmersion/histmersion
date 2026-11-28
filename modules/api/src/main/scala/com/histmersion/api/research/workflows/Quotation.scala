package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.api.library.Citekey
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure

final case class Quotation(
    citekey: Citekey,
    offset: Option[Quotation.Offset],
    quote: Quotation.Quote,
    ratio: Quotation.Ratio
) derives JsonCodec, KindlingsSchema, Schema

object Quotation:
  type Offset = Offset.T
  object Offset extends Newtype[Int, Pure]
  type Quote = Quote.T
  object Quote extends Newtype[String, Pure]
  type Ratio = Ratio.T
  object Ratio extends Newtype[Double, Pure]
