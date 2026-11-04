//> using scala 3.10.0
package com.histmersion.domain.detectorfixtures

import java.lang.System.{getenv as readEnvironment}

trait Environment:
  def get(name: String): Option[String]

object Good:
  def getenv(name: String): String = name
  def println(message: String): Unit = ()
  def run(env: Environment): Option[String] =
    println(getenv("PATH"))
    env.get("PATH")

object Bad:
  def direct = System.getenv("PATH") // expect: environment
  def alias = readEnvironment("PATH") // expect: environment
  val captured = System.out // expect: console
  def methodValue = scala.Predef.println(_: String) // expect: console
  def property = System.getProperty("user.home") // expect: properties
  def environment = sys.env // expect: environment
  def output = println("message") // expect: console
  def wrapped = List(1).map(_ => System.getenv("PATH")) // expect: environment

object Bootstrap:
  def environment = System.getenv("PATH")
  def output = println("still forbidden") // expect: console

object SampleRepo:
  def commit(c: java.sql.Connection): Unit = c.commit() // expect: repo-transaction
  def rollback(c: java.sql.Connection): Unit = c.rollback() // expect: repo-transaction
  def close(c: java.sql.Connection): Unit = c.close() // expect: repo-transaction
  def borrow(ds: javax.sql.DataSource): Unit = () // expect: repo-factory
  def borrowed(c: java.sql.Connection): Boolean = c.isReadOnly

object Service:
  def commit(c: java.sql.Connection): Unit = c.commit()

object RawAccess:
  def file(p: java.nio.file.Path) = java.nio.file.Files.readString(p) // expect: filesystem
  def http = java.net.http.HttpClient.newHttpClient() // expect: http
  def path = java.nio.file.Path.of("test")

object StorageAdapter:
  def read(p: java.nio.file.Path) = java.nio.file.Files.readString(p)

object Store:
  def commit(c: java.sql.Connection): Unit = c.commit() // expect: repo-transaction
