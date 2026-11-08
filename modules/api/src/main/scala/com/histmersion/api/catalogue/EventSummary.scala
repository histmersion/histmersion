package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.TimeSpan
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class EventSummary(
    id: Event.Id,
    label: EventSummary.Label,
    kind: EventSummary.Kind,
    timeSpan: TimeSpan,
    historicity: EventSummary.Historicity,
    placeId: Option[Place.Id] = None,
    parentId: Option[Event.Id] = None,
    description: Option[EventSummary.Description] = None,
    citekey: Option[Citekey] = None,
    scale: EventSummary.Scale = EventSummary.Scale(EventScale.Local),
    scaleStated: EventSummary.ScaleStated = EventSummary.ScaleStated(false),
    tier: EventSummary.Tier = EventSummary.Tier(EventTier.Attested),
    precision: EventSummary.Precision = EventSummary.Precision(EventPrecision.Precise),
    central: Option[EventSummary.Central] = None,
    plusMinus: Option[EventSummary.PlusMinus] = None,
    datingMethod: Option[EventSummary.DatingMethod] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def datingNote: Option[String] = EventPrecision.note(central, plusMinus, datingMethod)

object EventSummary:

  type Label = Label.T
  object Label extends RefinedType[String, Pure]
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type Historicity = Historicity.T
  object Historicity extends RefinedType[String, Pure]
  type Description = Description.T
  object Description extends RefinedType[String, Pure]
  type Scale = Scale.T
  object Scale extends RefinedType[String, Pure]
  type ScaleStated = ScaleStated.T
  object ScaleStated extends RefinedType[Boolean, Pure]
  type Tier = Tier.T
  object Tier extends RefinedType[String, Pure]
  type Precision = Precision.T
  object Precision extends RefinedType[String, Pure]
  type Central = Central.T
  object Central extends RefinedType[Long, Pure]
  type PlusMinus = PlusMinus.T
  object PlusMinus extends RefinedType[Long, Pure]
  type DatingMethod = DatingMethod.T
  object DatingMethod extends RefinedType[String, Pure]
