package com.histmersion.domain.detectorfixtures.transactions

import TransactionHelpers.{rollback as revert}

object WrappedRepo:
  def commit(c: java.sql.Connection): Unit = TransactionHelpers.commit(c) // expect: repo-transaction
  def throughTwo(c: java.sql.Connection): Unit = Indirection.commit(c) // expect: repo-transaction
  def aliased(c: java.sql.Connection): Unit = revert(c) // expect: repo-transaction
  def recursive(c: java.sql.Connection): Unit = TransactionHelpers.cycleB(c, 2) // expect: repo-transaction
  def closeStatement(s: java.sql.Statement): Unit = TransactionHelpers.closeStatement(s)
  def borrowed(c: java.sql.Connection): Boolean = c.isReadOnly

object PermittedRepo:
  def commit(c: java.sql.Connection): Unit = Indirection.commit(c)

object UnrelatedHelpers:
  def commit(s: String): Unit = ()

object LookalikeRepo:
  def save(s: String): Unit = UnrelatedHelpers.commit(s)

object LocalLookalikeRepo:
  def safe(c: java.sql.Connection): Unit =
    def local(): Unit = ()
    local()
  def unsafe(c: java.sql.Connection): Unit = LocalHelpers.committing(c) // expect: repo-transaction

object ChainedCapabilities:
  def process() = new ProcessBuilder("true").redirectErrorStream(true).start() // expect: process
