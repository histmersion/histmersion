package com.histmersion
package server

import java.nio.file.{Files, Path}

import com.augustnagro.magnum.{sql, DbCon}
import sttp.shared.Identity
import sttp.tapir.*

import com.histmersion.util.database.Transactor

/** Routes planted for [[ReadOnlyRoutesSuite]], which reads their compiled trees: never served. Each GET reaches its
  * write by another way the check must follow, and the POST and the read-only GETs must not be flagged.
  */
object ReadOnlyRoutesFixture:
  /** A store: one implementation writes a file, the other nothing. */
  trait Store:
    def save(): Unit

  final class FileStore(dir: Path) extends Store:
    def save(): Unit =
      val _ = Files.writeString(dir.resolve("saved"), "x")

  final class NoStore extends Store:
    def save(): Unit = ()

  val reading = endpoint.get.in("reading").out(stringBody)
  val direct = endpoint.get.in("direct").out(stringBody)
  val dispatched = endpoint.get.in("dispatched").out(stringBody)
  val functionValued = endpoint.get.in("function").out(stringBody)
  val headed = endpoint.head.in("headed")
  val anyMethod = endpoint.in("any").out(stringBody)
  val posting = endpoint.post.in("posting").out(stringBody)
  val readOnly = endpoint.get.in("read-only").out(stringBody)
  val rolledBack = endpoint.get.in("rolled-back").out(stringBody)
  val setting = endpoint.get.in("setting").out(stringBody)
  val updating = endpoint.get.in("updating").out(stringBody)
  private val base = endpoint.in("api")
  val derived = base.get.in("derived").out(stringBody)

  private def writeFile(dir: Path): String =
    val _ = Files.writeString(dir.resolve("written"), "x")
    "w"

  def routes(dir: Path, store: Store, tx: Transactor, con: DbCon) =
    val writer: () => String = () => writeFile(dir)
    List(
      reading.serverLogicSuccess[Identity](_ => "r"),
      direct.serverLogicSuccess[Identity](_ => writeFile(dir)),
      dispatched.serverLogicSuccess[Identity] { _ =>
        store.save()
        "d"
      },
      functionValued.serverLogicSuccess[Identity](_ => writer()),
      headed.serverLogicSuccess[Identity] { _ =>
        val _ = ProcessBuilder("true").start()
      },
      anyMethod.serverLogicSuccess[Identity](_ => writeFile(dir)),
      posting.serverLogicSuccess[Identity](_ => writeFile(dir)),
      readOnly.serverLogicSuccess[Identity](_ => tx.readOnlyRollback(Transactor.TimeoutMs(1000))("ro")),
      rolledBack.serverLogicSuccess[Identity](_ => Transactor.onConnection(using con).rollback("rb")),
      derived.serverLogicSuccess[Identity](_ => writeFile(dir)),
      setting.serverLogicSuccess[Identity](_ => s"${sql"SET TRANSACTION READ ONLY".update.run()(using con)}"),
      updating.serverLogicSuccess[Identity](_ => s"${sql"UPDATE zz SET seen = now()".update.run()(using con)}")
    )

  /** An endpoint handed in from elsewhere: its method cannot be read here. */
  def generic(e: PublicEndpoint[Unit, Unit, String, Any]) = e.serverLogicSuccess[Identity](_ => "g")
