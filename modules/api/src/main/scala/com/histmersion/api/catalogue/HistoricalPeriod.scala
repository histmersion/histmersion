package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
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
    parentId: Option[HistoricalPeriod.Id] = None,
    preset: Option[PresetRegion] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object HistoricalPeriod:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Authority = Authority.T
  object Authority extends Newtype[String, Pure]
  type Region = Region.T
  object Region extends Newtype[String, Pure]
