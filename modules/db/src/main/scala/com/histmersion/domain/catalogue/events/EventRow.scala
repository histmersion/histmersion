package com.histmersion.domain.catalogue.events

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.{EventPrecision, EventScale, EventSummary}
import com.augustnagro.magnum.DbCodec

/** An `events` row as the reads select it, with what its scale is derived from (its place's kind, its geometry's type)
  * and its tier.
  */
final private[events] case class EventRow(
    id: EventRow.Id,
    label: EventRow.Label,
    kind: EventRow.Kind,
    notBefore: Option[EventRow.NotBefore],
    beginBy: Option[EventRow.BeginBy],
    endFrom: Option[EventRow.EndFrom],
    notAfter: Option[EventRow.NotAfter],
    historicity: EventRow.Historicity,
    placeId: Option[com.histmersion.domain.catalogue.Place.Id],
    parentId: Option[EventRow.ParentId],
    description: Option[EventRow.Description],
    citekey: Option[com.histmersion.domain.library.Citekey],
    scale: Option[EventRow.Scale],
    placeKind: Option[EventRow.PlaceKind],
    geometryType: Option[EventRow.GeometryType],
    tier: EventRow.Tier,
    central: Option[EventRow.Central],
    plusMinus: Option[EventRow.PlusMinus],
    datingMethod: Option[EventRow.DatingMethod],
    uncertainty: Option[EventRow.Uncertainty]
) derives DbCodec:
  def toSummary: EventSummary =
    EventSummary(
      id,
      label,
      kind,
      TimeSpan(notBefore, beginBy, endFrom, notAfter),
      historicity,
      placeId,
      parentId,
      description,
      citekey,
      EventScale.effective(scale, kind, parentId.isDefined, placeKind, geometryType),
      scale.isDefined,
      tier,
      EventPrecision.of(uncertainty),
      central,
      plusMinus,
      datingMethod
    )

object EventRow:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotBefore = NotBefore.T
  object NotBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BeginBy = BeginBy.T
  object BeginBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EndFrom = EndFrom.T
  object EndFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotAfter = NotAfter.T
  object NotAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Historicity = Historicity.T
  object Historicity extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParentId = ParentId.T
  object ParentId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Scale = Scale.T
  object Scale extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceKind = PlaceKind.T
  object PlaceKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeometryType = GeometryType.T
  object GeometryType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tier = Tier.T
  object Tier extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Central = Central.T
  object Central extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlusMinus = PlusMinus.T
  object PlusMinus extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DatingMethod = DatingMethod.T
  object DatingMethod extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Uncertainty = Uncertainty.T
  object Uncertainty extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
