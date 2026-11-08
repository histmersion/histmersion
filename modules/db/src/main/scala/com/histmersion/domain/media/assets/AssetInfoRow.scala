package com.histmersion
package domain.media.assets

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A figure's row as its listing reads it (Magnum maps a SELECT's columns positionally). */
final private[assets] case class AssetInfoRow(
    name: AssetInfoRow.Name,
    mime: AssetInfoRow.Mime,
    bytes: AssetInfoRow.Bytes,
    sha256: Sha256,
    width: Option[AssetInfoRow.Width],
    height: Option[AssetInfoRow.Height]
) derives DbCodec

object AssetInfoRow:
  type Name = Name.T
  object Name extends RefinedType[String, Pure]
  type Mime = Mime.T
  object Mime extends RefinedType[String, Pure]
  type Bytes = Bytes.T
  object Bytes extends RefinedType[Int, Pure]
  type Width = Width.T
  object Width extends RefinedType[Int, Pure]
  type Height = Height.T
  object Height extends RefinedType[Int, Pure]
