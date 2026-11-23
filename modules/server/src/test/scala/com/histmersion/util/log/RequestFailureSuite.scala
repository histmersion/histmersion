package com.histmersion
package util.log

import java.net.SocketException
import java.nio.channels.ClosedByInterruptException
import java.sql.SQLException

class RequestFailureSuite extends munit.FunSuite:
  private def onFreshThread(error: Throwable): RequestFailure =
    RequestFailure.of(error, Thread.ofVirtual().unstarted(() => ()))

  private def onInterruptedThread(error: Throwable): RequestFailure =
    Thread.currentThread().interrupt()
    val failure = RequestFailure.of(error, Thread.currentThread())
    val _ = Thread.interrupted()
    failure

  test("an interrupt among the causes is the client's going, however deep it is wrapped"):
    assertEquals(onFreshThread(InterruptedException()), RequestFailure.ClientGone)
    assertEquals(onFreshThread(IllegalStateException(ClosedByInterruptException())), RequestFailure.ClientGone)
    assertEquals(
      onFreshThread(RuntimeException("Error executing query", SQLException("I/O", InterruptedException()))),
      RequestFailure.ClientGone
    )

  test("a statement cut by the interrupt of a cancelled request, met on the interrupted handler, is the client's going"):
    val cut = SQLException("An I/O error occurred while sending to the backend.", SocketException("Closed by interrupt"))
    assertEquals(onInterruptedThread(RuntimeException("Error executing query", cut)), RequestFailure.ClientGone)
    assertEquals(onInterruptedThread(RuntimeException("any")), RequestFailure.ClientGone)

  test("any other error on a handler not interrupted is a fault"):
    assertEquals(onFreshThread(SocketException("Connection reset")), RequestFailure.Fault)
    assertEquals(onFreshThread(SQLException("relation \"places\" does not exist")), RequestFailure.Fault)
    assertEquals(onFreshThread(RuntimeException("no cause")), RequestFailure.Fault)

  test("a cycle of causes ends"):
    val a = RuntimeException("a")
    val b = RuntimeException("b", a)
    val _ = a.initCause(b)
    assertEquals(onFreshThread(a), RequestFailure.Fault)
