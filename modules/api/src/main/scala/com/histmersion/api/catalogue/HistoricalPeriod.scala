package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Follows the PeriodO model. `region` is PeriodO's own statement of where the period applies; `preset` only names the
  * heading the timeline lists it under.
  */
final case class HistoricalPeriod(
    id: HistoricalPeriod.Id,
    label: HistoricalPeriod.Label,
    authority: Option[HistoricalPeriod.Authority] = None,
    period: Period,
    region: Option[HistoricalPeriod.Region] = None,
    parentId: Option[HistoricalPeriod.ParentId] = None,
    preset: Option[PresetRegion] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object HistoricalPeriod:
  type Id = Id.T
  object Id extends RefinedType[String, Pure]
  type Label = Label.T
  object Label extends RefinedType[String, Pure]
  type Authority = Authority.T
  object Authority extends RefinedType[String, Pure]
  type Region = Region.T
  object Region extends RefinedType[String, Pure]
  type ParentId = ParentId.T
  object ParentId extends RefinedType[String, Pure]
