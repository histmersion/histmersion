package com.histmersion.domain.media.assets

import com.augustnagro.magnum.DbCodec
import java.util.UUID

/** An asset as a bundle reads it: its line's fields, the size of its bytes, its period's inclusive bounds. */
final private[assets] case class AssetBundleRow(
    uid: UUID,
    origin: UUID,
    sourceUid: UUID,
    name: AssetBundleRow.Name,
    mime: AssetBundleRow.Mime,
    sha: AssetBundleRow.Sha,
    bytes: AssetBundleRow.Bytes,
    width: Option[AssetBundleRow.Width],
    height: Option[AssetBundleRow.Height],
    kind: Option[AssetBundleRow.Kind],
    caption: Option[AssetBundleRow.Caption],
    license: Option[AssetBundleRow.License],
    geom: Option[AssetBundleRow.Geom],
    pNull: AssetBundleRow.PNull,
    pLo: Option[AssetBundleRow.PLo],
    pHi: Option[AssetBundleRow.PHi],
    updatedAt: AssetBundleRow.UpdatedAt
) derives DbCodec

object AssetBundleRow:
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Mime = Mime.T
  object Mime extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Sha = com.histmersion.Sha256
  val Sha: _root_.com.histmersion.Sha256.type = _root_.com.histmersion.Sha256
  type Bytes = com.histmersion.ByteCount
  val Bytes: _root_.com.histmersion.ByteCount.type = _root_.com.histmersion.ByteCount
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
  type Geom = Geom.T
  object Geom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PNull = PNull.T
  object PNull extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PLo = com.histmersion.domain.DeepTime.SignedYear
  val PLo: _root_.com.histmersion.domain.DeepTime.SignedYear.type = _root_.com.histmersion.domain.DeepTime.SignedYear
  type PHi = com.histmersion.domain.DeepTime.SignedYear
  val PHi: _root_.com.histmersion.domain.DeepTime.SignedYear.type = _root_.com.histmersion.domain.DeepTime.SignedYear
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
