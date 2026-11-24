package com.histmersion
package domain.catalogue.places

import com.augustnagro.magnum.DbCon
import com.histmersion.api.catalogue.{Gazetteer, GazRecord}
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*
import scala.annotation.tailrec
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator

private[places] object GazetteerImport:
  def session(
      source: Gazetteer.SourceInfo.Key,
      version: Gazetteer.LoadCounts.Version,
      apply: Boolean,
      windowYears: Gazetteer.ValidityWindowYears =
        Gazetteer.ValidityWindowYears(Gazetteer.DefaultOpenStartWindowYears.value),
      pointAreaKinds: Set[Gazetteer.PointAreaKinds] = Gazetteer.DefaultPointAreaKinds.toSet
  )(using con: DbCon)(using WritePermit): Session =
    val transactions = Transactor.onConnection
    val repo = transactions.writeAutocommit(GazetteerRepo.session(source, version, windowYears, pointAreaKinds))
    new Session(repo, transactions, apply)

  final class Session private[GazetteerImport] (
      repo: GazetteerRepo.Session,
      transactions: Transactor.Session,
      apply: Boolean
  ):
    private def write[A](work: DbCon ?=> A)(using WritePermit): A =
      if apply then transactions.write(work) else transactions.rollback(work)

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
      val counts = write(repo.writeBatch(rows))
      transactions.writeAutocommit(repo.recordSeen(rows))
      counts.modify(_.skipped).setTo((duplicates ++ counts.skipped).take(20))

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
