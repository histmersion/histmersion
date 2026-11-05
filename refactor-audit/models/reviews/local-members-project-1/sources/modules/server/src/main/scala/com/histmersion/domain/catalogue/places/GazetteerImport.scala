package com.histmersion.domain.catalogue.places

import com.augustnagro.magnum.DbCon
import com.histmersion.api.catalogue.{Gazetteer, GazRecord}
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*
import scala.annotation.tailrec

private[places] object GazetteerImport:
  type SessionSource = SessionSource.T
  object SessionSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SessionVersion = SessionVersion.T
  object SessionVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SessionApply = SessionApply.T
  object SessionApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SessionWindowYears = SessionWindowYears.T
  object SessionWindowYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SessionPointAreaKinds = SessionPointAreaKinds.T
  object SessionPointAreaKinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def session(
      source: com.histmersion.domain.catalogue.places.GazetteerImport.SessionSource,
      version: com.histmersion.domain.catalogue.places.GazetteerImport.SessionVersion,
      apply: com.histmersion.domain.catalogue.places.GazetteerImport.SessionApply,
      windowYears: com.histmersion.domain.catalogue.places.GazetteerImport.SessionWindowYears = Gazetteer.DefaultOpenStartWindowYears,
      pointAreaKinds: Set[com.histmersion.domain.catalogue.places.GazetteerImport.SessionPointAreaKinds] = Gazetteer.DefaultPointAreaKinds.toSet
  )(using con: DbCon): Session =
    val transactions = Transactor.onConnection
    val repo = transactions.autocommit(GazetteerRepo.session(source, version, windowYears, pointAreaKinds))
    new Session(repo, transactions, apply)

  final class Session private[GazetteerImport] (
      repo: GazetteerRepo.Session,
      transactions: Transactor.Session,
      apply: Session.Apply
  ):
    private def write[A](work: DbCon ?=> A): A =
      if apply then transactions.transact(work) else transactions.rollback(work)

    def batch(records: List[GazRecord]): Gazetteer.LoadCounts =
      @tailrec def dedupe(rest: List[GazRecord], ok: Vector[GazRecord], duplicates: List[com.histmersion.domain.catalogue.places.GazetteerImport.Session.BatchDedupeDuplicates]): (Vector[GazRecord], List[com.histmersion.domain.catalogue.places.GazetteerImport.Session.BatchDedupeResultPart2]) =
        rest match
          case Nil => (ok, duplicates)
          case record :: tail if ok.exists(_.ref == record.ref) => dedupe(tail, ok, duplicates :+ s"${record.ref}: repeated")
          case record :: tail => dedupe(tail, ok :+ record, duplicates)
      val (unique, duplicates) = dedupe(records, Vector.empty, Nil)
      val rows = unique.toList
      val counts = write(repo.writeBatch(rows))
      transactions.autocommit(repo.recordSeen(rows))
      counts.modify(_.skipped).setTo((duplicates ++ counts.skipped).take(20))

    def prune(batch: com.histmersion.domain.catalogue.places.GazetteerImport.Session.PruneBatch): com.histmersion.domain.catalogue.places.GazetteerImport.Session.PruneResult =
      if !apply then 0L
      else
        val ids = transactions.autocommit(repo.pruneCandidates)
        ids.grouped(math.max(1, batch)).foldLeft(0L) { (count, chunk) =>
          count + transactions.transact(repo.deletePruneChunk(chunk))
        }

    def sweep(): Gazetteer.LoadCounts = write(repo.sweep())

  object Session:
    type BatchDedupeResultPart2 = BatchDedupeResultPart2.T
    object BatchDedupeResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BatchDedupeDuplicates = BatchDedupeDuplicates.T
    object BatchDedupeDuplicates extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Apply = Apply.T
    object Apply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type PruneResult = PruneResult.T
    object PruneResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PruneBatch = PruneBatch.T
    object PruneBatch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
