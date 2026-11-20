package com.histmersion
package db

import com.augustnagro.magnum.{sql, DbCon}
import com.histmersion.util.database.{Transactor, Unused, WritePermit}

// G94: a write's answer dropped (discardedOutcome) or dropped with its reason (acceptedOutcome)
final class Writes(tx: Transactor):
  def touched(id: Int)(using DbCon): Boolean = sql"UPDATE t SET x = 1 WHERE id = $id".update.run() > 0
  def found(id: Int)(using DbCon): Option[Int] = Option.when(touched(id))(id)

  def wildcard(id: Int)(using DbCon): Unit =
    val _ = sql"UPDATE t SET x = 2 WHERE id = $id".update.run() // assert: GuidelineInventory.discardedOutcome
  def unitBody(id: Int)(using DbCon): Unit = sql"DELETE FROM t WHERE id = $id".update.run() // assert: GuidelineInventory.discardedOutcome
  def statement(id: Int)(using DbCon): Int =
    touched(id) // assert: GuidelineInventory.discardedOutcome
    id
  def inForeach(ids: List[Int])(using DbCon): Unit = ids.foreach(i => found(i)) // assert: GuidelineInventory.discardedOutcome
  def handedBack(id: Int)(using WritePermit): Unit =
    val _ = tx.write(touched(id)) // assert: GuidelineInventory.discardedOutcome
  def accepted(id: Int)(using DbCon): Unit =
    Unused(sql"DELETE FROM t WHERE id = $id".update.run(), Unused.Why.Idempotent) // assert: GuidelineInventory.acceptedOutcome

  // no finding: the answer is used, or the statement's count says nothing (an INSERT of VALUES, an unconditional
  // upsert, a command)
  def used(id: Int)(using DbCon): Boolean = !touched(id)
  def answered(id: Int)(using WritePermit): Option[Int] = tx.write(found(id))
  def inserted(id: Int)(using DbCon): Unit = sql"INSERT INTO t (id) VALUES ($id)".update.run()
  def upserted(id: Int)(using DbCon): Unit =
    val _ = sql"INSERT INTO t (id) VALUES ($id) ON CONFLICT (id) DO UPDATE SET x = 3".update.run()
  def command()(using DbCon): Unit = sql"ANALYZE t".update.run()
