package com.histmersion
package domain.media.assets

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.catalogue.events.EventSelection

final private[assets] case class AssetDetailRow(
    name: AssetDetailRow.Name,
    mime: AssetDetailRow.Mime,
    bytes: AssetDetailRow.Bytes,
    sha256: Sha256,
    width: Option[AssetDetailRow.Width],
    height: Option[AssetDetailRow.Height],
    kind: Option[AssetDetailRow.Kind],
    caption: Option[AssetDetailRow.Caption],
    license: Option[AssetDetailRow.License],
    lat: Option[Latitude],
    lon: Option[Longitude],
    periodStart: Option[Year],
    periodEnd: Option[Year]
) derives DbCodec

object AssetDetailRow:
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Mime = Mime.T
  object Mime extends Newtype[String, Pure]
  type Bytes = Bytes.T
  object Bytes extends Newtype[Int, Pure]
  type Width = Width.T
  object Width extends Newtype[Int, Pure]
  type Height = Height.T
  object Height extends Newtype[Int, Pure]
  export EventSelection.Kind
  type Caption = Caption.T
  object Caption extends Newtype[String, Pure]
  type License = License.T
  object License extends Newtype[String, Pure]
