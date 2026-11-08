package com.histmersion
package util.data.exchange

import com.histmersion.api.exchange.Bundle

import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.exchange.BundleEntities.BlobUse
import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.util.storage.ContentStore

import com.augustnagro.magnum.DbCon
import io.circe.Encoder
import io.github.iltotore.iron.constraint.any.Pure

/** A kind of record a bundle carries for its owner: one entry of the archive, its lines out for a scope (with the blobs
  * they use and where their bytes are read from), the blobs it takes in, and its rows in.
  *
  * `files` is the content-addressed store the server keeps files in (the originals stored at ingest); a kind whose
  * lines name no file ignores it.
  */
trait CarriedKind:
  def owner: DataOwner

  /** Its entry in the bundle. */
  def entry: BundleEntry

  /** The kind of record its lines are, each stamped with its rev; `None` for a set its records own, which is not. */
  def record: Option[Bundle.Kind] = None

  /** The table its records are kept in: a tombstone of the kind deletes from it, a merge's fold touches it. */
  def table: Option[TableName] = None

  /** Its lines in `scope`, read in the export's transaction. */
  def out(scope: BundleScope, files: ContentStore)(using DbCon): CarriedOut

  /** Its lines in `scope` put into the bundle's in-memory form: what a merge compares a bundle's lines with. */
  def into(scope: BundleScope, files: ContentStore)(using DbCon): BundleEntities => BundleEntities

  /** Where an import puts a blob its lines use; `None` for a use it does not own. */
  def blobIn(use: BlobUse, sha256: Sha256, writer: BundleImport.Writer, files: ContentStore): Option[BlobSink] = None

  /** Its steps of an import. */
  def rowsIn: List[RowsIn] = Nil

object CarriedKind:
  type OwnedClock = OwnedClock.T
  object OwnedClock extends Newtype[String, Pure]
  type RecordsClock = RecordsClock.T
  object RecordsClock extends Newtype[String, Pure]

  /** A kind of record whose lines use no blob: its lines stamped with their revs, `clock` the change each carries. */

  def records[A: Encoder.AsObject](owner: DataOwner, kind: Bundle.Kind, table: TableName, steps: List[RowsIn])(
      lines: BundleScope => DbCon ?=> List[A]
  )(clock: A => RecordsClock, set: (BundleEntities, List[A]) => BundleEntities): CarriedKind =
    Lines(
      owner,
      BundleEntry(kind.file),
      Some(kind),
      Some(table),
      steps,
      lines,
      a => BundleFormat.stamp(a).line,
      a => Some(CarriedOut.AsOf(clock(a).value)),
      set
    )

  /** A set its records own: its lines as they are, with no rev; `clock` the change a line carries, when it has one. */
  def owned[A: Encoder.AsObject](owner: DataOwner, entry: BundleEntry, steps: List[RowsIn])(
      lines: BundleScope => DbCon ?=> List[A]
  )(clock: A => Option[OwnedClock], set: (BundleEntities, List[A]) => BundleEntities): CarriedKind =
    Lines(owner, entry, None, None, steps, lines, a => BundleFormat.line(a), a => clock(a).map(c => CarriedOut.AsOf(c.value)), set)

  final private class Lines[A](
      val owner: DataOwner,
      val entry: BundleEntry,
      override val record: Option[Bundle.Kind],
      override val table: Option[TableName],
      override val rowsIn: List[RowsIn],
      lines: BundleScope => DbCon ?=> List[A],
      line: A => String,
      clock: A => Option[CarriedOut.AsOf],
      set: (BundleEntities, List[A]) => BundleEntities
  ) extends CarriedKind:
    def out(scope: BundleScope, files: ContentStore)(using DbCon): CarriedOut =
      val as = lines(scope)
      CarriedOut(CarriedOut.Payload(BundleFormat.ndjson(as.map(line))), as.flatMap(clock).maxByOption(_.value))
    def into(scope: BundleScope, files: ContentStore)(using DbCon): BundleEntities => BundleEntities =
      val as = lines(scope)
      es => set(es, as)
