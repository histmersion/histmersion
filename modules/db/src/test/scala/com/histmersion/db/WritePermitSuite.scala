package com.histmersion
package db

import com.augustnagro.magnum.DbCon
import com.histmersion.util.database.{Transactor, WritePermit}
import com.histmersion.util.problem.{Problem, Raised}

import java.io.PrintWriter
import java.sql.Connection
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import java.util.logging.Logger
import javax.sql.DataSource
import scala.concurrent.duration.*

/** The maintenance gate's mechanism (G89): permits for the writes, admission for every connection, and the epoch that
  * fences off work begun before a restore. No database: the connections are a stub's.
  */
class WritePermitSuite extends munit.FunSuite:
  import WritePermit.Refusal

  /** A transactor whose transaction just runs the work: what `write` checks is the permit, not the database. */
  private object Runs extends Transactor:
    def read[A](work: DbCon ?=> A): A = work(using null.asInstanceOf[DbCon])
    def transact[A](work: DbCon ?=> A): A = work(using null.asInstanceOf[DbCon])

  /** A data source that counts the connections asked of it (and hands out none). */
  final private class Counting extends DataSource:
    val asked = AtomicInteger(0)
    def getConnection(): Connection = { val _ = asked.incrementAndGet(); null.asInstanceOf[Connection] }
    def getConnection(u: String, p: String): Connection = getConnection()
    def getLogWriter: PrintWriter = null.asInstanceOf[PrintWriter]
    def setLogWriter(out: PrintWriter): Unit = ()
    def setLoginTimeout(seconds: Int): Unit = ()
    def getLoginTimeout: Int = 0
    def getParentLogger: Logger = Logger.getGlobal
    def unwrap[T](iface: Class[T]): T = throw java.sql.SQLException("not a wrapper")
    def isWrapperFor(iface: Class[?]): Boolean = false

  /** `f` on a thread of its own; its outcome once it ends. */
  private def onAnotherThread[A](f: => A): () => Either[Throwable, A] =
    val out = AtomicReference[Either[Throwable, A]]()
    val t = Thread.ofPlatform().start(() => out.set(scala.util.Try(f).toEither))
    () => { t.join(10000); out.get() }

  test("a write runs under a permit, and a nested one reuses it: one permit out, not two"):
    val gate = WritePermit.Gate()
    val inner = gate.permit { outer ?=>
      gate.permit(summon[WritePermit] eq outer)
    }
    assertEquals(inner, Right(Right(true)))
    assertEquals(gate.permit(Runs.write(42)), Right(42))

  test("a permit kept past its block writes nothing"):
    val gate = WritePermit.Gate()
    val kept = gate.permit(summon[WritePermit]).toOption.get
    assert(!kept.live)
    intercept[IllegalStateException](Runs.write(1)(using kept))

  test("a restore draining the gate: new permits are refused, its own thread's too, and the gate opens again after"):
    val gate = WritePermit.Gate()
    assert(gate.drain())
    assert(!gate.drain(), "a second restore is refused")
    assertEquals(gate.permit(1), Left(Refusal.Restoring))
    assertEquals(onAnotherThread(gate.permit(1))(), Right(Left(Refusal.Restoring)))
    assertEquals(Refusal.Restoring.problem, Problem.Conflict.Busy(Problem.Conflict.Activity.Restore))
    gate.reopen()
    assertEquals(gate.permit(1), Right(1))

  test("a restore waits for a write in flight, and only until its deadline"):
    val gate = WritePermit.Gate()
    val inside = CountDownLatch(1)
    val finish = CountDownLatch(1)
    val writer = onAnotherThread(gate.permit { inside.countDown(); finish.await(10, TimeUnit.SECONDS) })
    assert(inside.await(10, TimeUnit.SECONDS))
    assert(gate.drain())
    assert(!gate.awaitPermits(50.millis.fromNow).none, "the write is still out when the wait runs out")
    val released = onAnotherThread { Thread.sleep(100); finish.countDown() }
    assert(gate.awaitPermits(10.seconds.fromNow).none, "the wait ends when the write gives its permit back")
    assertEquals(writer(), Right(Right(true)))
    val _ = released()
    gate.reopen()

  test("while the restore runs, only its own thread gets a connection"):
    val gate = WritePermit.Gate()
    val inner = Counting()
    val ds = gate.dataSource(inner)
    assert(gate.drain())
    val _ = ds.getConnection() // draining: the workers still record where they stopped
    gate.close()
    val _ = ds.getConnection() // the restore's own thread
    onAnotherThread(ds.getConnection())() match
      case Left(r: Raised) =>
        assertEquals(r.problems.toVector, Vector(Problem.Conflict.Busy(Problem.Conflict.Activity.Restore)))
      case other => fail(s"another thread was given a connection: $other")
    gate.reopen()
    assert(onAnotherThread(ds.getConnection())().isRight)
    assertEquals(inner.asked.get, 3)

  test("work begun before a restore is fenced off after it; work begun after is not, nor a request's"):
    val gate = WritePermit.Gate()
    val ds = gate.dataSource(Counting())
    val begun = CountDownLatch(1)
    val restored = CountDownLatch(1)
    val unit = onAnotherThread(gate.unit {
      begun.countDown()
      restored.await(10, TimeUnit.SECONDS)
      // the unit, and a thread it starts, after the restore
      val child = onAnotherThread(gate.stale)()
      (gate.permit(1), scala.util.Try(ds.getConnection()).toEither.left.map(_.getClass.getSimpleName), child)
    })
    assert(begun.await(10, TimeUnit.SECONDS))
    assert(gate.drain())
    gate.close()
    gate.fence()
    gate.reopen()
    restored.countDown()
    assertEquals(unit(), Right((Left(Refusal.Stale), Left("Raised"), Right(true))))
    assertEquals(Refusal.Stale.problem, Problem.Run.Restored)
    assertEquals(gate.unit(gate.permit(1)), Right(1), "a unit begun after the restore")
    assertEquals(gate.permit(1), Right(1), "a request is no unit")

  test("a restore given up before it ran fences nothing off"):
    val gate = WritePermit.Gate()
    val epoch = gate.epochNow
    assert(gate.drain())
    gate.close()
    gate.reopen()
    assertEquals(gate.epochNow, epoch)
