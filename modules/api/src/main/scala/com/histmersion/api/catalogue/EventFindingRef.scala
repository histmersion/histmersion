package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class EventFindingRef(
    id: EventFindingRef.Id,
    citekey: Citekey,
    claim: EventFindingRef.Claim,
    km: EventFindingRef.Km,
    yearGap: EventFindingRef.YearGap,
    periodStart: Option[Year] = None,
    periodEnd: Option[Year] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object EventFindingRef:
  type Id = Id.T
  object Id extends RefinedType[String, Pure]
  type Claim = Claim.T
  object Claim extends RefinedType[String, Pure]
  type Km = Km.T
  object Km extends RefinedType[Double, Pure]
  type YearGap = YearGap.T
  object YearGap extends RefinedType[Long, Pure]
