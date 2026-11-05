package com.histmersion.domain.media.assets

import com.augustnagro.magnum.DbCodec

final private[assets] case class AssetBytesRow(mime: AssetBytesRow.Mime, bytes: Array[AssetBytesRow.Bytes]) derives DbCodec

object AssetBytesRow:
  type Mime = Mime.T
  object Mime extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Bytes = Bytes.T
  object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
