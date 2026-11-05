package com.histmersion.domain.catalogue.events

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.given_DbCodec_List

/** A stored event as the import's merge reads it, with its catalogue keys as `provider|id`. */
final private[events] case class StoredEventRow(
    id: StoredEventRow.Id,
    kind: StoredEventRow.Kind,
    notBefore: Option[StoredEventRow.NotBefore],
    beginBy: Option[StoredEventRow.BeginBy],
    endFrom: Option[StoredEventRow.EndFrom],
    notAfter: Option[StoredEventRow.NotAfter],
    lat: Option[com.histmersion.Latitude],
    lon: Option[com.histmersion.Longitude],
    wikidata: Option[StoredEventRow.Wikidata],
    keys: List[StoredEventRow.Keys],
    catalogue: Option[StoredEventRow.Catalogue],
    label: StoredEventRow.Label
) derives DbCodec

object StoredEventRow:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type Wikidata = Wikidata.T
  object Wikidata extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Keys = Keys.T
  object Keys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Catalogue = Catalogue.T
  object Catalogue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
