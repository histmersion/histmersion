package com.histmersion
package util.database

import com.augustnagro.magnum.DbCon

// stand-ins for the write permit, the transactor and Unused (G94)
final class WritePermit
trait Transactor:
  def write[A](work: DbCon ?=> A)(using WritePermit): A
object Unused:
  enum Why derives CanEqual:
    case Idempotent, Held, Reported, ReadBack
  def apply[A](answer: A, why: Why): Unit = () // assert: GuidelineInventory.G3misplaced
