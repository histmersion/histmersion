package com.histmersion.domain.media.assets

import com.augustnagro.magnum.DbCodec

/** A figure's row as its listing reads it (Magnum maps a SELECT's columns positionally). */
final private[assets] case class AssetInfoRow(
    name: AssetInfoRow.Name,
    mime: AssetInfoRow.Mime,
    bytes: AssetInfoRow.Bytes,
    sha256: com.histmersion.Sha256,
    width: Option[AssetInfoRow.Width],
    height: Option[AssetInfoRow.Height]
) derives DbCodec

object AssetInfoRow:
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Mime = Mime.T
  object Mime extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Bytes = Bytes.T
  object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Width = Width.T
  object Width extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Height = Height.T
  object Height extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
