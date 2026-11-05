package com.histmersion.domain.media.assets

import com.augustnagro.magnum.DbCodec

final private[assets] case class AssetDetailRow(
    name: AssetDetailRow.Name,
    mime: AssetDetailRow.Mime,
    bytes: AssetDetailRow.Bytes,
    sha256: com.histmersion.Sha256,
    width: Option[AssetDetailRow.Width],
    height: Option[AssetDetailRow.Height],
    kind: Option[AssetDetailRow.Kind],
    caption: Option[AssetDetailRow.Caption],
    license: Option[AssetDetailRow.License],
    lat: Option[com.histmersion.Latitude],
    lon: Option[com.histmersion.Longitude],
    periodStart: Option[com.histmersion.Year],
    periodEnd: Option[com.histmersion.Year]
) derives DbCodec

object AssetDetailRow:
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
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Caption = Caption.T
  object Caption extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type License = License.T
  object License extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
