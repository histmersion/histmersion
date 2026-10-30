package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.files.*
import com.histmersion.config.*
import com.histmersion.domain.*

import java.util.concurrent.{CompletableFuture, ExecutionException, TimeoutException, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

/** The last [[StorageProbe]] measurement, reused for `ttl` and measured again in the background after it — ONE
  * measurement at a time, however many Abouts ask. A request that finds nothing measured yet waits up to `wait` for the
  * first one, then answers "measuring…"; one that finds an old measurement gets it at once (marked measuring) while the
  * next one runs.
  *
  * @param prepare
  *   called on the requesting thread (it pins the work to that thread's config generation), returns the measurement to
  *   run on another thread
  * @param spawn
  *   runs it (a virtual thread; a test runs it inline or holds it)
  */
final class StorageCache(
    prepare: () => () => StorageReport,
    ttlMillis: () => Long,
    clock: () => Long = () => System.currentTimeMillis(),
    spawn: (() => Unit) => Unit = r => { val _ = Thread.ofVirtual().name("histmersion-storage").start(() => r()) }
):

  private val last = AtomicReference[Option[(Long, StorageReport)]](None)
  private val running = AtomicReference[Option[CompletableFuture[StorageReport]]](None)

  def get(waitMillis: Long): StorageReport =
    last.get match
      case Some((at, r)) if clock() - at < ttlMillis() => r
      case Some((_, r))                                =>
        val _ = kick()
        r.copy(measuring = true)
      case None =>
        val f = kick()
        try f.get(math.max(0L, waitMillis), TimeUnit.MILLISECONDS)
        catch
          case _: TimeoutException   => StorageReport(measuring = true)
          case e: ExecutionException =>
            StorageReport(problems = List(s"storage: ${Option(e.getCause).getOrElse(e).getMessage}"))
          case _: InterruptedException => StorageReport(measuring = true)

  /** The measurement under way, or a new one. */
  private def kick(): CompletableFuture[StorageReport] =
    running.get match
      case Some(f) => f
      case None    =>
        val f = CompletableFuture[StorageReport]()
        val claimed: Option[CompletableFuture[StorageReport]] = Some(f)
        if !running.compareAndSet(None, claimed) then kick()
        else
          val work = scala.util.Try(prepare())
          spawn { () =>
            try
              val r = work.get()
              last.set(Some((clock(), r)))
              f.complete(r)
            catch
              case e: Throwable =>
                scribe.warn(s"storage: the measurement failed: ${e.getMessage}")
                f.completeExceptionally(e)
            finally running.set(None)
            ()
          }
          f
