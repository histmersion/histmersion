package com.histmersion
package util.data.exchange
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

import com.histmersion.api.exchange.Bundle

import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.exchange.BundleEntities.BlobUse
import com.histmersion.util.data.TableName
import com.histmersion.util.storage.ContentStore

import com.augustnagro.magnum.DbCon
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** The carried kinds an export and an import run over, from the registry. The entries come in the bundle format's order
  * (the manifest lists them so, and a bundle is byte for byte the same whoever registered first); the steps of an
  * import in their stage and rank.
  */
@tolerated(LintRule.FileSystem, "pending G23 (files only through util.storage)")
final class CarriedKinds private (val inBundleOrder: List[CarriedKind]):

  /** The table of a kind of record. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def tableOf(kind: Bundle.Kind): TableName =
    inBundleOrder
      .collectFirst { case k if k.record.contains(kind) && k.table.isDefined => k.table.get }
      .getOrElse(throw IllegalStateException(s"no carried kind keeps ${kind.wire} records"))

  /** The steps of `stage`, in rank order (registration order among equal ranks). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
  def sink(use: BlobUse, sha256: Sha256, writer: BundleImport.Writer, files: ContentStore): Option[BlobSink] =
    inBundleOrder.iterator.flatMap(_.blobIn(use, sha256, writer, files)).nextOption()

object CarriedKinds:

  /** The kinds in the format's order, or what is wrong with them: an entry no kind carries, one carried twice, one the
    * format does not have.
    */

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(kinds: List[CarriedKind]): Either[List[Problem], CarriedKinds] =
    val entries = kinds.map(_.entry.value)
    def entry(e: String, fault: Problem.Io.Fault): Problem =
      Problem.Io.Entry(Problem.Io.Format.Bundle, Problem.Io.Path(e), fault)
    val missing = Bundle.Files.all.filterNot(entries.contains).map(entry(_, Problem.Io.Fault.Missing))
    val twice = entries.diff(entries.distinct).distinct.map(entry(_, Problem.Io.Fault.Repeated))
    val unknown = entries.filterNot(Bundle.Files.all.contains).map(entry(_, Problem.Io.Fault.Foreign))
    val problems = missing ++ twice ++ unknown
    Either.cond(
      problems.isEmpty,
      new CarriedKinds(kinds.sortBy(k => Bundle.Files.all.indexOf(k.entry.value))),
      problems
    )

  /** The registry's kinds; an inconsistent registry is a programming error, found at start. */
  def apply(kinds: List[CarriedKind]): CarriedKinds =
    of(kinds).fold(ps => throw IllegalStateException(ps.map(_.sentence).mkString("; ")), identity)
