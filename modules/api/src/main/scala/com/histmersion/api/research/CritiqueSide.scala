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
import com.histmersion.api.TwoStates

final case class CritiqueSide(
    citekey: Citekey,
    title: Option[Title] = None,
    year: Option[Year] = None,
    cited: CritiqueSide.Cited = CritiqueSide.Cited.Uncited,
    mentions: CritiqueSide.Mentions = CritiqueSide.Mentions(0)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CritiqueSide:
  enum Cited derives CanEqual:
    case Cited, Uncited
  object Cited extends TwoStates[Cited]:
    extension (cited: Cited) def cited: Boolean = cited == Cited
  type Mentions = Mentions.T
  object Mentions extends Newtype[Int, Pure]
