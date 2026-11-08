package com.histmersion
package domain.media.assets

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure

final private[assets] case class AssetBytesRow(mime: AssetBytesRow.Mime, bytes: AssetBytesRow.Bytes) derives DbCodec

object AssetBytesRow:
  type Bytes = Bytes.T
  object Bytes extends Newtype[Array[Byte], Pure]

  type Mime = Mime.T
  object Mime extends Newtype[String, Pure]
