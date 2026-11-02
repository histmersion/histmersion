package com.histmersion.domain.media.assets

import com.histmersion.api.given
import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.exchange.BundleEntities.BlobUse
import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.util.data.exchange.{
  BlobOut,
  BlobSink,
  BundleEntry,
  BundleFormat,
  BundleScope,
  CarriedKind,
  CarriedOut,
  ImportRank,
  ImportStage,
  RowsIn
}
import com.histmersion.util.storage.ContentStore
import com.histmersion.values.BundleKind

import com.augustnagro.magnum.DbCon
import hearth.kindlings.optics.syntax.*

/** What a bundle carries of the figures: each asset's line stamped with its rev, its bytes a blob; an import writes the
  * metadata of an asset whose bytes are here already among the rows, the rest as their bytes arrive.
  */
object AssetsCarried extends CarriedKind:
  val owner: DataOwner = DataOwner("media")
  val entry: BundleEntry = BundleEntry(BundleKind.Asset.file)
  override val record: Option[BundleKind] = Some(BundleKind.Asset)
  override val table: Option[TableName] = Some(TableName("assets"))

  def out(scope: BundleScope, files: ContentStore)(using DbCon): CarriedOut =
    val rs = AssetBundleRepo.assets(scope).toList
    val as = rs.map(AssetBundleRepo.line)
    CarriedOut(
      BundleFormat.ndjson(as.map(BundleFormat.stamp(_).line)),
      as.map(_.updatedAt).maxOption,
      rs.map(r => BlobOut(r.sha, r.bytes, AssetBundleRepo.bytes(r.uid)))
    )

  def into(scope: BundleScope, files: ContentStore)(using DbCon): BundleEntities => BundleEntities =
    val as = AssetBundleRepo.assets(scope).toList.map(AssetBundleRepo.line)
    es => es.modify(_.assets).setTo(as)

  override def blobIn(
      use: BlobUse,
      sha256: String,
      writer: BundleImport.Writer,
      files: ContentStore
  ): Option[BlobSink] = use match
    case BlobUse.AssetBytes(u) =>
      Some(BlobSink.Held(bytes => AssetBundleRepo.write(writer, writer.once(AssetBundleRepo.byUid)(u), Some(bytes))))
    case _ => None

  override val rowsIn: List[RowsIn] =
    List(RowsIn(ImportStage.Rows, ImportRank(51), AssetBundleRepo.metadataIn))
