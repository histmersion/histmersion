package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

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
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Authority = Authority.T
  object Authority extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Region = Region.T
  object Region extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParentId = ParentId.T
  object ParentId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
