package com.histmersion.domain.catalogue.events

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID

/** An event as its bundle line reads it: the row, its parent's and its source's uids. */
final private[events] case class BEventRow(
    uid: UUID,
    origin: UUID,
    id: BEventRow.Id,
    label: BEventRow.Label,
    kind: BEventRow.Kind,
    notBefore: Option[BEventRow.NotBefore],
    beginBy: Option[BEventRow.BeginBy],
    endFrom: Option[BEventRow.EndFrom],
    notAfter: Option[BEventRow.NotAfter],
    historicity: BEventRow.Historicity,
    geom: Option[BEventRow.Geom],
    placeId: Option[com.histmersion.domain.catalogue.Place.Id],
    parentUid: Option[UUID],
    description: Option[BEventRow.Description],
    citekey: Option[com.histmersion.domain.library.Citekey],
    sourceUid: Option[UUID],
    updatedAt: BEventRow.UpdatedAt,
    scale: Option[BEventRow.Scale],
    catalogue: Option[BEventRow.Catalogue],
    names: List[BEventRow.Names],
    wikidata: Option[BEventRow.Wikidata],
    central: Option[BEventRow.Central],
    plusMinus: Option[BEventRow.PlusMinus],
    datingMethod: Option[BEventRow.DatingMethod]
) derives DbCodec

object BEventRow:
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
  type Geom = Geom.T
  object Geom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Scale = Scale.T
  object Scale extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Catalogue = Catalogue.T
  object Catalogue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Names = Names.T
  object Names extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Wikidata = Wikidata.T
  object Wikidata extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Central = Central.T
  object Central extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlusMinus = PlusMinus.T
  object PlusMinus extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DatingMethod = DatingMethod.T
  object DatingMethod extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
