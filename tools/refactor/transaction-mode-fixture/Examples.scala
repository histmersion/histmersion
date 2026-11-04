package com.histmersion.domain.transactionmodefixtures

object ModeRepo:
  def mode(c: java.sql.Connection): Unit = c.setReadOnly(true) // expect: repo-transaction
  def restore(c: java.sql.Connection): Unit = c.setReadOnly(false) // expect: repo-transaction
  def sql(s: java.sql.Statement): Boolean = s.execute("SET TRANSACTION READ ONLY") // expect: repo-transaction
  def commit(s: java.sql.Statement): Int = s.executeUpdate(" commit;") // expect: repo-transaction
  def prepare(c: java.sql.Connection) = c.prepareStatement("START TRANSACTION") // expect: repo-transaction
  def ordinary(s: java.sql.Statement): Boolean = s.execute("SELECT 'COMMIT'")
  def close(s: java.sql.Statement): Unit = s.close()
  def getter(c: java.sql.Connection): Boolean = c.isReadOnly
  def dynamic(s: java.sql.Statement, text: String): Boolean = s.execute(text)
  def commented(s: java.sql.Statement): Boolean = s.execute("/* prefix */ COMMIT")
  def compound(s: java.sql.Statement): Boolean = s.execute("SELECT 1; COMMIT")
  def helper(s: java.sql.Statement): Unit = Helper.begin(s) // expect: repo-transaction

object Helper:
  def begin(s: java.sql.Statement): Unit = { val _ = s.execute("BEGIN") }

object Service:
  def mode(c: java.sql.Connection): Unit = c.setReadOnly(true)
  def sql(s: java.sql.Statement): Boolean = s.execute("SET TRANSACTION READ ONLY")

object Lookalike:
  def execute(sql: String): Boolean = false
object OtherRepo:
  def save(): Boolean = Lookalike.execute("COMMIT")
