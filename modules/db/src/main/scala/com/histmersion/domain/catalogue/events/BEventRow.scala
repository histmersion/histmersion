package com.histmersion
package domain.catalogue.events

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
    placeId: Option[Place.Id],
    parentUid: Option[UUID],
    description: Option[BEventRow.Description],
    citekey: Option[Citekey],
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
  object Id extends RefinedType[String, Pure]
  type Label = Label.T
  object Label extends RefinedType[String, Pure]
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type NotBefore = NotBefore.T
  object NotBefore extends RefinedType[Long, Pure]
  type BeginBy = BeginBy.T
  object BeginBy extends RefinedType[Long, Pure]
  type EndFrom = EndFrom.T
  object EndFrom extends RefinedType[Long, Pure]
  type NotAfter = NotAfter.T
  object NotAfter extends RefinedType[Long, Pure]
  type Historicity = Historicity.T
  object Historicity extends RefinedType[String, Pure]
  type Geom = Geom.T
  object Geom extends RefinedType[String, Pure]
  type Description = Description.T
  object Description extends RefinedType[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends RefinedType[String, Pure]
  type Scale = Scale.T
  object Scale extends RefinedType[String, Pure]
  type Catalogue = Catalogue.T
  object Catalogue extends RefinedType[String, Pure]
  type Names = Names.T
  object Names extends RefinedType[String, Pure]
  type Wikidata = Wikidata.T
  object Wikidata extends RefinedType[String, Pure]
  type Central = Central.T
  object Central extends RefinedType[Long, Pure]
  type PlusMinus = PlusMinus.T
  object PlusMinus extends RefinedType[Long, Pure]
  type DatingMethod = DatingMethod.T
  object DatingMethod extends RefinedType[String, Pure]
