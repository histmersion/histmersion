package com.histmersion.util.data.exchange

import com.histmersion.db.BundleImport
import com.histmersion.files.BundleEntities.BlobUse
import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.util.storage.ContentStore
import com.histmersion.values.BundleKind

import com.augustnagro.magnum.DbCon

/** A kind of record a bundle carries for its owner: one entry of the archive, its lines out for a scope (with the
  * blobs they use and where their bytes are read from), the blobs it takes in, and its rows in.
  *
  * `files` is the content-addressed store the server keeps files in (the originals stored at ingest); a kind whose
  * lines name no file ignores it.
  */
trait CarriedKind:
  def owner: DataOwner

  /** Its entry in the bundle. */
  def entry: BundleEntry

  /** The kind of record its lines are, each stamped with its rev; `None` for a set its records own, which is not. */
  def record: Option[BundleKind] = None

  /** The table its records are kept in: a tombstone of the kind deletes from it, a merge's fold touches it. */
  def table: Option[TableName] = None

  /** Its lines in `scope`, read in the export's transaction. */
  def out(scope: BundleScope, files: ContentStore)(using DbCon): CarriedOut

  /** Where an import puts a blob its lines use; `None` for a use it does not own. */
  def blobIn(use: BlobUse, sha256: String, writer: BundleImport.Writer, files: ContentStore): Option[BlobSink] = None

  /** Its steps of an import. */
  def rowsIn: List[RowsIn] = Nil
