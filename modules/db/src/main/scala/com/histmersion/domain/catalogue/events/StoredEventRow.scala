package com.histmersion
package domain.catalogue.events

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.given_DbCodec_List
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure

/** A stored event as the import's merge reads it, with its catalogue keys as `provider|id`. */
final private[events] case class StoredEventRow(
    id: StoredEventRow.Id,
    kind: StoredEventRow.Kind,
    notBefore: Option[StoredEventRow.NotBefore],
    beginBy: Option[StoredEventRow.BeginBy],
    endFrom: Option[StoredEventRow.EndFrom],
    notAfter: Option[StoredEventRow.NotAfter],
    lat: Option[Latitude],
    lon: Option[Longitude],
    wikidata: Option[StoredEventRow.Wikidata],
    keys: List[StoredEventRow.Keys],
    catalogue: Option[StoredEventRow.Catalogue],
    label: StoredEventRow.Label
) derives DbCodec

object StoredEventRow:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
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
  type Wikidata = Wikidata.T
  object Wikidata extends Newtype[String, Pure]
  type Keys = Keys.T
  object Keys extends Newtype[String, Pure]
  type Catalogue = Catalogue.T
  object Catalogue extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
