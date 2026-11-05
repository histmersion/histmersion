package com.histmersion.util.data.exchange

import com.histmersion.api.exchange.Bundle

import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.exchange.BundleEntities.BlobUse
import com.histmersion.util.data.TableName
import com.histmersion.util.storage.ContentStore

import com.augustnagro.magnum.DbCon

/** The carried kinds an export and an import run over, from the registry. The entries come in the bundle format's order
  * (the manifest lists them so, and a bundle is byte for byte the same whoever registered first); the steps of an
  * import in their stage and rank.
  */
final class CarriedKinds private (val inBundleOrder: List[CarriedKind]):

  /** The table of a kind of record. */
  def tableOf(kind: Bundle.Kind): TableName =
    inBundleOrder
      .collectFirst { case k if k.record.contains(kind) && k.table.isDefined => k.table.get }
      .getOrElse(throw IllegalStateException(s"no carried kind keeps ${kind.wire} records"))

  /** The steps of `stage`, in rank order (registration order among equal ranks). */
  def steps(stage: ImportStage): List[RowsIn] =
    inBundleOrder.flatMap(_.rowsIn).filter(_.stage == stage).sortBy(s => (s.rank.value: Int))

  /** Every kind's lines in `scope`, in the bundle's in-memory form: what a merge compares a bundle's lines with. */
  def entities(scope: BundleScope, files: ContentStore)(using DbCon): BundleEntities =
    @annotation.tailrec
    def loop(rest: List[CarriedKind], acc: BundleEntities): BundleEntities = rest match
      case k :: tail => loop(tail, k.into(scope, files)(acc))
      case Nil       => acc
    loop(inBundleOrder, BundleEntities())

  /** Where the first kind that owns `use` puts it. */
  def sink(use: BlobUse, sha256: com.histmersion.util.data.exchange.CarriedKinds.SinkSha256, writer: BundleImport.Writer, files: ContentStore): Option[BlobSink] =
    inBundleOrder.iterator.flatMap(_.blobIn(use, sha256, writer, files)).nextOption()

object CarriedKinds:
  /** The kinds in the format's order, or what is wrong with them: an entry no kind carries, one carried twice, one the
    * format does not have.
    */
  type SinkSha256 = SinkSha256.T
  object SinkSha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfResultPart1 = OfResultPart1.T
  object OfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(kinds: List[CarriedKind]): Either[List[com.histmersion.util.data.exchange.CarriedKinds.OfResultPart1], CarriedKinds] =
    val entries = kinds.map(_.entry.value)
    val missing =
      com.histmersion.api.exchange.Bundle.Files.all.filterNot(entries.contains).map(e => s"no carried kind writes $e")
    val twice = entries.diff(entries.distinct).distinct.map(e => s"$e is carried twice")
    val unknown = entries
      .filterNot(com.histmersion.api.exchange.Bundle.Files.all.contains)
      .map(e => s"$e is not a file of the bundle format")
    val problems = missing ++ twice ++ unknown
    Either.cond(
      problems.isEmpty,
      new CarriedKinds(kinds.sortBy(k => com.histmersion.api.exchange.Bundle.Files.all.indexOf(k.entry.value))),
      problems
    )

  /** The registry's kinds; an inconsistent registry is a programming error, found at start. */
  def apply(kinds: List[CarriedKind]): CarriedKinds =
    of(kinds).fold(ps => throw IllegalStateException(ps.mkString("; ")), identity)
