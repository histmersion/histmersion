package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.TimeSpan
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.Api

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
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
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  def datingNote: Option[String] = EventPrecision.note(central, plusMinus, datingMethod)

object EventSummary:

  type Label = Label.T
  object Label extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  export EventGeo.Historicity
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type Scale = Scale.T
  object Scale extends Newtype[String, Pure]
  type ScaleStated = ScaleStated.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object ScaleStated extends Newtype[Boolean, Pure]
  export Api.EventQuery.Tier
  export EventGeo.Precision
  export CatalogRecord.Central
  export CatalogRecord.PlusMinus
  type DatingMethod = DatingMethod.T
  object DatingMethod extends Newtype[String, Pure]
