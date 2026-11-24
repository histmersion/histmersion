package com.histmersion
package domain.catalogue.places

import com.augustnagro.magnum.DbCon
import com.histmersion.api.catalogue.{Gazetteer, GazRecord}
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*
import scala.annotation.tailrec
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.{CreatedAt, Creator}
import java.time.Clock

@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
private[places] object GazetteerImport:
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def session(
      source: Gazetteer.SourceInfo.Key,
      version: Gazetteer.LoadCounts.Version,
      apply: Boolean,
      clock: Clock,
      windowYears: Gazetteer.ValidityWindowYears =
        Gazetteer.ValidityWindowYears(Gazetteer.DefaultOpenStartWindowYears.value),
      pointAreaKinds: Set[Gazetteer.PointAreaKinds] = Gazetteer.DefaultPointAreaKinds.toSet
  )(using con: DbCon)(using WritePermit): Session =
    val transactions = Transactor.onConnection
    val repo = transactions.writeAutocommit(GazetteerRepo.session(source, version, windowYears, pointAreaKinds))
    new Session(repo, transactions, apply, clock)

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  final class Session private[GazetteerImport] (
      repo: GazetteerRepo.Session,
      transactions: Transactor.Session,
      apply: Boolean,
      clock: Clock
  ):
    private def write[A](work: DbCon ?=> A)(using WritePermit): A =
      if apply then transactions.write(work) else transactions.rollback(work)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def batch(records: List[GazRecord])(using WritePermit)(using Creator.New): Gazetteer.LoadCounts =
      @tailrec def dedupe(
          rest: List[GazRecord],
          ok: Vector[GazRecord],
          duplicates: List[Gazetteer.LoadCounts.Skipped]
      ): (Vector[GazRecord], List[Gazetteer.LoadCounts.Skipped]) =
        rest match
          case Nil                                              => (ok, duplicates)
          case record :: tail if ok.exists(_.ref == record.ref) =>
            dedupe(tail, ok, duplicates :+ Gazetteer.LoadCounts.Skipped(s"${record.ref}: repeated"))
          case record :: tail => dedupe(tail, ok :+ record, duplicates)
      val (unique, duplicates) = dedupe(records, Vector.empty, Nil)
      val rows = unique.toList
      val counts = write:
        given CreatedAt = CreatedAt.now(clock)
        repo.writeBatch(rows)
      transactions.writeAutocommit(repo.recordSeen(rows))
      counts.modify(_.skipped).setTo((duplicates ++ counts.skipped).take(20))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def prune(batch: Int)(using WritePermit): Session.Pruned =
      if !apply then Session.Pruned(0L)
      else
        val ids = transactions.writeAutocommit(repo.pruneCandidates)
        Session.Pruned(ids.grouped(math.max(1, batch)).foldLeft(0L) { (count, chunk) =>
          count + transactions.write(repo.deletePruneChunk(chunk)).value
        })

    def sweep()(using WritePermit): Gazetteer.LoadCounts = write(repo.sweep())

  object Session:
    type Pruned = Pruned.T
    object Pruned extends Newtype[Long, Pure]
