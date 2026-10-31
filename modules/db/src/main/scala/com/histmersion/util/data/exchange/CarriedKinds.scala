package com.histmersion.util.data.exchange

import com.histmersion.api.BundleFiles
import com.histmersion.db.BundleImport
import com.histmersion.files.BundleEntities.BlobUse
import com.histmersion.util.data.TableName
import com.histmersion.util.storage.ContentStore
import com.histmersion.values.BundleKind

/** The carried kinds an export and an import run over, from the registry. The entries come in the bundle format's
  * order (the manifest lists them so, and a bundle is byte for byte the same whoever registered first); the steps of
  * an import in their stage and rank.
  */
final class CarriedKinds private (val inBundleOrder: List[CarriedKind]):

  /** The table of a kind of record. */
  def tableOf(kind: BundleKind): TableName =
    inBundleOrder
      .collectFirst { case k if k.record.contains(kind) && k.table.isDefined => k.table.get }
      .getOrElse(throw IllegalStateException(s"no carried kind keeps ${kind.wire} records"))

  /** The steps of `stage`, in rank order (registration order among equal ranks). */
  def steps(stage: ImportStage): List[RowsIn] =
    inBundleOrder.flatMap(_.rowsIn).filter(_.stage == stage).sortBy(s => (s.rank.value: Int))

  /** Where the first kind that owns `use` puts it. */
  def sink(use: BlobUse, sha256: String, writer: BundleImport.Writer, files: ContentStore): Option[BlobSink] =
    inBundleOrder.iterator.flatMap(_.blobIn(use, sha256, writer, files)).nextOption()

object CarriedKinds:
  /** The kinds in the format's order, or what is wrong with them: an entry no kind carries, one carried twice, one the
    * format does not have.
    */
  def of(kinds: List[CarriedKind]): Either[List[String], CarriedKinds] =
    val entries = kinds.map(_.entry.value)
    val missing = BundleFiles.all.filterNot(entries.contains).map(e => s"no carried kind writes $e")
    val twice = entries.diff(entries.distinct).distinct.map(e => s"$e is carried twice")
    val unknown = entries.filterNot(BundleFiles.all.contains).map(e => s"$e is not a file of the bundle format")
    val problems = missing ++ twice ++ unknown
    Either.cond(
      problems.isEmpty,
      new CarriedKinds(kinds.sortBy(k => BundleFiles.all.indexOf(k.entry.value))),
      problems
    )

  /** The registry's kinds; an inconsistent registry is a programming error, found at start. */
  def apply(kinds: List[CarriedKind]): CarriedKinds =
    of(kinds).fold(ps => throw IllegalStateException(ps.mkString("; ")), identity)

  /** Today's kinds as the transition adapters register them, for the callers that are not given the registry. */
  lazy val transition: CarriedKinds = apply(CarriedKindsAdapter.all)
