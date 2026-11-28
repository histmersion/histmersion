package com.histmersion
package domain.catalogue.events

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.Jsonb
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.util.identity.AliasTable

/** An event as its bundle line reads it: the row, its parent's and its source's uids. */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
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
    datingMethod: Option[BEventRow.DatingMethod],
    creator: Option[Jsonb.JsonText] = None
) derives DbCodec

object BEventRow:
  export AliasTable.Id
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
  type Geom = Geom.T
  object Geom extends Newtype[String, Pure]
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
  type Scale = Scale.T
  object Scale extends Newtype[String, Pure]
  type Catalogue = Catalogue.T
  object Catalogue extends Newtype[String, Pure]
  type Names = Names.T
  object Names extends Newtype[String, Pure]
  type Wikidata = Wikidata.T
  object Wikidata extends Newtype[String, Pure]
  type Central = Central.T
  object Central extends Newtype[Long, Pure]
  type PlusMinus = PlusMinus.T
  object PlusMinus extends Newtype[Long, Pure]
  type DatingMethod = DatingMethod.T
  object DatingMethod extends Newtype[String, Pure]
