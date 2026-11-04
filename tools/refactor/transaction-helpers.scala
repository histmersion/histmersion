package com.histmersion.domain.detectorfixtures.transactions

object TransactionHelpers:
  def commit(c: java.sql.Connection): Unit = c.commit()
  def rollback(c: java.sql.Connection): Unit = c.rollback()
  def closeStatement(s: java.sql.Statement): Unit = s.close()
  def cycleA(c: java.sql.Connection, depth: Int): Unit =
    if depth > 0 then cycleB(c, depth - 1) else c.commit()
  def cycleB(c: java.sql.Connection, depth: Int): Unit = cycleA(c, depth)

object Indirection:
  def commit(c: java.sql.Connection): Unit = TransactionHelpers.commit(c)

object Service:
  def transaction(c: java.sql.Connection): Unit = TransactionHelpers.commit(c)

object LocalHelpers:
  def committing(c: java.sql.Connection): Unit =
    def local(): Unit = c.commit()
    local()
