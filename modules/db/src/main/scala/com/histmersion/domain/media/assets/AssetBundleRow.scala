package com.histmersion
package domain.media.assets

import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.DeepTime
import com.histmersion.util.database.Jsonb

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
    updatedAt: AssetBundleRow.UpdatedAt,
    creator: Option[Jsonb.JsonText] = None
) derives DbCodec

object AssetBundleRow:
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Mime = Mime.T
  object Mime extends Newtype[String, Pure]
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Sha = Sha256
  val Sha: Sha256.type = Sha256
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Bytes = ByteCount
  val Bytes: ByteCount.type = ByteCount
  type Width = Width.T
  object Width extends Newtype[Int, Pure]
  type Height = Height.T
  object Height extends Newtype[Int, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Caption = Caption.T
  object Caption extends Newtype[String, Pure]
  type License = License.T
  object License extends Newtype[String, Pure]
  type Geom = Geom.T
  object Geom extends Newtype[String, Pure]
  type PNull = PNull.T
  object PNull extends Newtype[Boolean, Pure]
  export DeepTime.{SignedYear as PHi, SignedYear as PLo}
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
