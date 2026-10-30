package com.histmersion.util.database

import com.augustnagro.magnum.{connect as connectTo, transact as transactOn, DbCon}

import javax.sql.DataSource

/** How a service runs its repository calls: on one connection, or in one transaction that an exception rolls back. A
  * repository method takes the `DbCon` it runs on and never opens one itself.
  */
trait Transactor:
  def read[A](work: DbCon ?=> A): A
  def transact[A](work: DbCon ?=> A): A

object Transactor:
  def apply(dataSource: DataSource): Transactor = Implementation(dataSource)

  final private class Implementation(dataSource: DataSource) extends Transactor:
    def read[A](work: DbCon ?=> A): A = connectTo(dataSource)(work)
    def transact[A](work: DbCon ?=> A): A = transactOn(dataSource)(work)
