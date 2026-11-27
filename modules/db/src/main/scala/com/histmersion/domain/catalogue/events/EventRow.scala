package com.histmersion
package domain.catalogue.events

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.{EventPrecision, EventScale, EventSummary}
import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.Event
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** An `events` row as the reads select it, with what its scale is derived from (its place's kind, its geometry's type)
  * and its tier.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final private[events] case class EventRow(
    id: BEventRow.Id,
    label: EventRow.Label,
    kind: EventRow.Kind,
    notBefore: Option[EventRow.NotBefore],
    beginBy: Option[EventRow.BeginBy],
    endFrom: Option[EventRow.EndFrom],
    notAfter: Option[EventRow.NotAfter],
    historicity: EventRow.Historicity,
    placeId: Option[Place.Id],
    parentId: Option[EventRow.ParentId],
    description: Option[EventRow.Description],
    citekey: Option[Citekey],
    scale: Option[EventRow.Scale],
    placeKind: Option[EventRow.PlaceKind],
    geometryType: Option[EventRow.GeometryType],
    tier: EventRow.Tier,
    central: Option[EventRow.Central],
    plusMinus: Option[EventRow.PlusMinus],
    datingMethod: Option[EventRow.DatingMethod],
    uncertainty: Option[EventRow.Uncertainty]
) derives DbCodec:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def toSummary: EventSummary =
    def year(y: Option[Long]) = y.map(Year(_))
    EventSummary(
      Event.Id(id.value),
      EventSummary.Label(label.value),
      EventSummary.Kind(kind.value),
      TimeSpan(
        year(notBefore.map(_.value)),
        year(beginBy.map(_.value)),
        year(endFrom.map(_.value)),
        year(notAfter.map(_.value))
      ),
      EventSummary.Historicity(historicity.value),
      placeId.map(p => com.histmersion.api.catalogue.Place.Id(p.value)),
      parentId.map(p => Event.Id(p.value)),
      description.map(d => EventSummary.Description(d.value)),
      citekey.map(c => com.histmersion.api.library.Citekey(c.value)),
      EventScale.effective(
        scale.map(s => EventSummary.Scale(s.value)),
        EventScale.EffectiveKind(kind.value),
        parentId.isDefined,
        placeKind.map(k => com.histmersion.api.catalogue.Place.Kind.Wire(k.value)),
        geometryType.map(g => EventScale.GeometryType(g.value))
      ),
      EventSummary.ScaleStated(scale.isDefined),
      EventSummary.Tier(tier.value),
      EventPrecision.of(uncertainty.map(u => EventPrecision.Uncertainty(u.value))),
      central.map(c => EventSummary.Central(c.value)),
      plusMinus.map(p => EventSummary.PlusMinus(p.value)),
      datingMethod.map(d => EventSummary.DatingMethod(d.value))
    )

object EventRow:
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type NotBefore = NotBefore.T
  object NotBefore extends Newtype[Long, Pure]
  type BeginBy = BeginBy.T
  object BeginBy extends Newtype[Long, Pure]
  type EndFrom = EndFrom.T
  object EndFrom extends Newtype[Long, Pure]
  type NotAfter = NotAfter.T
  object NotAfter extends Newtype[Long, Pure]
  type Historicity = Historicity.T
  object Historicity extends Newtype[String, Pure]
  type ParentId = ParentId.T
  object ParentId extends Newtype[String, Pure]
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type Scale = Scale.T
  object Scale extends Newtype[String, Pure]
  type PlaceKind = PlaceKind.T
  object PlaceKind extends Newtype[String, Pure]
  type GeometryType = GeometryType.T
  object GeometryType extends Newtype[String, Pure]
  type Tier = Tier.T
  object Tier extends Newtype[String, Pure]
  type Central = Central.T
  object Central extends Newtype[Long, Pure]
  type PlusMinus = PlusMinus.T
  object PlusMinus extends Newtype[Long, Pure]
  type DatingMethod = DatingMethod.T
  object DatingMethod extends Newtype[String, Pure]
  type Uncertainty = Uncertainty.T
  object Uncertainty extends Newtype[Long, Pure]
