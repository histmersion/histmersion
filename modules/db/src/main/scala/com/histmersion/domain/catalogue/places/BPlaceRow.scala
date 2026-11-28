package com.histmersion
package domain.catalogue.places

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.Jsonb
import com.histmersion.TwoCases

/** A place of this collection's own, as its bundle line reads it. */
final private[places] case class BPlaceRow(
    uid: UUID,
    origin: UUID,
    id: BPlaceRow.Id,
    label: BPlaceRow.Label,
    kind: BPlaceRow.Kind,
    geom: Option[BPlaceRow.Geom],
    pNull: BPlaceRow.PNull,
    pLo: Option[BPlaceRow.PLo],
    pHi: Option[BPlaceRow.PHi],
    source: Option[BPlaceRow.Source],
    properties: Option[BPlaceRow.Properties],
    updatedAt: BPlaceRow.UpdatedAt,
    creator: Option[Jsonb.JsonText] = None
) derives DbCodec

object BPlaceRow:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Geom = Geom.T
  object Geom extends Newtype[String, Pure]
  enum PNull derives CanEqual:
    case Undated, Dated
  object PNull extends TwoCases[PNull]:
    extension (pNull: PNull) def pNull: Boolean = pNull == Undated
  type PLo = PLo.T
  object PLo extends Newtype[Long, Pure]
  type PHi = PHi.T
  object PHi extends Newtype[Long, Pure]
  type Source = Source.T
  object Source extends Newtype[String, Pure]
  type Properties = Properties.T
  object Properties extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
