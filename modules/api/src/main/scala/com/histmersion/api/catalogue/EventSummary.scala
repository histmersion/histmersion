package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.TimeSpan
import com.histmersion.api.given

final case class EventSummary(
    id: com.histmersion.api.catalogue.Event.Id,
    label: EventSummary.Label,
    kind: EventSummary.Kind,
    timeSpan: TimeSpan,
    historicity: EventSummary.Historicity,
    placeId: Option[com.histmersion.api.catalogue.Place.Id] = None,
    parentId: Option[com.histmersion.api.catalogue.Event.Id] = None,
    description: Option[EventSummary.Description] = None,
    citekey: Option[com.histmersion.api.library.Citekey] = None,
    scale: EventSummary.Scale = _root_.com.histmersion.api.catalogue.EventSummary.Scale(EventScale.Local),
    scaleStated: EventSummary.ScaleStated = _root_.com.histmersion.api.catalogue.EventSummary.ScaleStated(false),
    tier: EventSummary.Tier = _root_.com.histmersion.api.catalogue.EventSummary.Tier(EventTier.Attested),
    precision: EventSummary.Precision = _root_.com.histmersion.api.catalogue.EventSummary.Precision(EventPrecision.Precise),
    central: Option[EventSummary.Central] = None,
    plusMinus: Option[EventSummary.PlusMinus] = None,
    datingMethod: Option[EventSummary.DatingMethod] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def datingNote: Option[String] = EventPrecision.note(central, plusMinus, datingMethod)

object EventSummary:

  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Historicity = Historicity.T
  object Historicity extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Scale = Scale.T
  object Scale extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ScaleStated = ScaleStated.T
  object ScaleStated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tier = Tier.T
  object Tier extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Precision = Precision.T
  object Precision extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Central = Central.T
  object Central extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlusMinus = PlusMinus.T
  object PlusMinus extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DatingMethod = DatingMethod.T
  object DatingMethod extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
