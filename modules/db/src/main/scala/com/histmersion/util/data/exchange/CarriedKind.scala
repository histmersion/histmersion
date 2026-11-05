package com.histmersion.util.data.exchange

import com.histmersion.api.exchange.Bundle

import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.exchange.BundleEntities.BlobUse
import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.util.storage.ContentStore

import com.augustnagro.magnum.DbCon
import io.circe.Encoder

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
  def blobIn(use: BlobUse, sha256: com.histmersion.util.data.exchange.CarriedKind.BlobInSha256, writer: BundleImport.Writer, files: ContentStore): Option[BlobSink] = None

  /** Its steps of an import. */
  def rowsIn: List[RowsIn] = Nil

object CarriedKind:

  /** A kind of record whose lines use no blob: its lines stamped with their revs, `clock` the change each carries. */
  type BlobInSha256 = BlobInSha256.T
  object BlobInSha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordsClockResult = RecordsClockResult.T
  object RecordsClockResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OwnedClockResult = OwnedClockResult.T
  object OwnedClockResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def records[A: Encoder.AsObject](owner: DataOwner, kind: Bundle.Kind, table: TableName, steps: List[RowsIn])(
      lines: BundleScope => DbCon ?=> List[A]
  )(clock: A => com.histmersion.util.data.exchange.CarriedKind.RecordsClockResult, set: (BundleEntities, List[A]) => BundleEntities): CarriedKind =
    Lines(
      owner,
      BundleEntry(kind.file),
      Some(kind),
      Some(table),
      steps,
      lines,
      BundleFormat.stamp(_).line,
      a => Some(clock(a)),
      set
    )

  /** A set its records own: its lines as they are, with no rev; `clock` the change a line carries, when it has one. */
  def owned[A: Encoder.AsObject](owner: DataOwner, entry: BundleEntry, steps: List[RowsIn])(
      lines: BundleScope => DbCon ?=> List[A]
  )(clock: A => Option[com.histmersion.util.data.exchange.CarriedKind.OwnedClockResult], set: (BundleEntities, List[A]) => BundleEntities): CarriedKind =
    Lines(owner, entry, None, None, steps, lines, BundleFormat.line(_), clock, set)

  final private class Lines[A](
      val owner: DataOwner,
      val entry: BundleEntry,
      override val record: Option[Bundle.Kind],
      override val table: Option[TableName],
      override val rowsIn: List[RowsIn],
      lines: BundleScope => DbCon ?=> List[A],
      line: A => Lines.LineResult,
      clock: A => Option[Lines.ClockResult],
      set: (BundleEntities, List[A]) => BundleEntities
  ) extends CarriedKind:
    def out(scope: BundleScope, files: ContentStore)(using DbCon): CarriedOut =
      val as = lines(scope)
      CarriedOut(BundleFormat.ndjson(as.map(line)), as.flatMap(clock).maxOption)
    def into(scope: BundleScope, files: ContentStore)(using DbCon): BundleEntities => BundleEntities =
      val as = lines(scope)
      es => set(es, as)

  object Lines:
    type LineResult = LineResult.T
    object LineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ClockResult = ClockResult.T
    object ClockResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
