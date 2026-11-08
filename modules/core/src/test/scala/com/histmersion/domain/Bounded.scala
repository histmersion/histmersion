package com.histmersion
package domain

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import scala.concurrent.{Await, Future, Promise, TimeoutException}
import scala.concurrent.duration.Duration

/** munit bounds only ASYNCHRONOUS tests by `munitTimeout`; a synchronous body that hangs (a busy loop, a lock never
  * released) stalls the whole run — one did, for hours (#865). Mixed into a suite, every test body runs on its own
  * daemon thread, and the test fails once `munitTimeout` passes (the thread is interrupted and, being a daemon, never
  * keeps the JVM alive).
  *
  * The body's thread completes a promise directly: waiting on anything scheduled on munit's own execution context would
  * deadlock, since that context runs on the thread that waits.
  */
trait Bounded extends munit.FunSuite:
  override val munitTimeout: Duration = Duration(5, "min")

  override def munitTestTransforms: List[TestTransform] =
    super.munitTestTransforms :+ new TestTransform(
      "bounded",
      t =>
        t.withBody { () =>
          val started = Promise[Future[Any]]()
          val thread = Thread
            .ofPlatform()
            .daemon(true)
            .name(s"test: ${t.name}")
            .start { () =>
              val _ = started.complete(scala.util.Try(t.body()))
            }
          scala.util.Try(Await.result(started.future, munitTimeout)) match
            case scala.util.Success(f)                   => f
            case scala.util.Failure(_: TimeoutException) =>
              thread.interrupt()
              Future.failed(new TimeoutException(s"${t.name}: did not finish within $munitTimeout"))
            case scala.util.Failure(e) => Future.failed(e)
        }
    )
