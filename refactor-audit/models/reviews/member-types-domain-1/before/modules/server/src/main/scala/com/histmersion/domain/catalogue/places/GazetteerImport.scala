package com.histmersion.domain.catalogue.places

import com.augustnagro.magnum.DbCon
import com.histmersion.api.catalogue.{Gazetteer, GazRecord}
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*
import scala.annotation.tailrec

private[places] object GazetteerImport:
  def session(
      source: String,
      version: String,
      apply: Boolean,
      windowYears: Int = Gazetteer.DefaultOpenStartWindowYears,
      pointAreaKinds: Set[String] = Gazetteer.DefaultPointAreaKinds.toSet
  )(using con: DbCon): Session =
    val transactions = Transactor.onConnection
    val repo = transactions.autocommit(GazetteerRepo.session(source, version, windowYears, pointAreaKinds))
    new Session(repo, transactions, apply)

  final class Session private[GazetteerImport] (
      repo: GazetteerRepo.Session,
      transactions: Transactor.Session,
      apply: Boolean
  ):
    private def write[A](work: DbCon ?=> A): A =
      if apply then transactions.transact(work) else transactions.rollback(work)

    def batch(records: List[GazRecord]): Gazetteer.LoadCounts =
      @tailrec def dedupe(rest: List[GazRecord], ok: Vector[GazRecord], duplicates: List[String]): (Vector[GazRecord], List[String]) =
        rest match
          case Nil => (ok, duplicates)
          case record :: tail if ok.exists(_.ref == record.ref) => dedupe(tail, ok, duplicates :+ s"${record.ref}: repeated")
          case record :: tail => dedupe(tail, ok :+ record, duplicates)
      val (unique, duplicates) = dedupe(records, Vector.empty, Nil)
      val rows = unique.toList
      val counts = write(repo.writeBatch(rows))
      transactions.autocommit(repo.recordSeen(rows))
      counts.modify(_.skipped).setTo((duplicates ++ counts.skipped).take(20))

    def prune(batch: Int): Long =
      if !apply then 0L
      else
        val ids = transactions.autocommit(repo.pruneCandidates)
        ids.grouped(math.max(1, batch)).foldLeft(0L) { (count, chunk) =>
          count + transactions.transact(repo.deletePruneChunk(chunk))
        }

    def sweep(): Gazetteer.LoadCounts = write(repo.sweep())
