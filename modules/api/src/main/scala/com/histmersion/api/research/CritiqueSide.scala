package com.histmersion
package api.research
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure

final case class CritiqueSide(
    citekey: Citekey,
    title: Option[Title] = None,
    year: Option[Year] = None,
    cited: CritiqueSide.Cited = CritiqueSide.Cited(false),
    mentions: CritiqueSide.Mentions = CritiqueSide.Mentions(0)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CritiqueSide:
  type Cited = Cited.T
  object Cited extends Newtype[Boolean, Pure]
  type Mentions = Mentions.T
  object Mentions extends Newtype[Int, Pure]
