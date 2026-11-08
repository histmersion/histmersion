package com.histmersion
package util.data.diagnostics

import hearth.kindlings.optics.syntax.*

import java.util.concurrent.{CompletableFuture, ExecutionException, TimeoutException, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.maintenance.Storage
import io.github.iltotore.iron.constraint.any.Pure

/** The last [[StorageFigures]] measurement, reused for `ttl` and measured again in the background after it — ONE
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
    prepare: () => () => Storage.Report,
    ttlMillis: () => StorageCache.TimeToLive,
    clock: () => StorageCache.Instant = () => StorageCache.Instant(System.currentTimeMillis()),
    spawn: (() => Unit) => Unit = r => { val _ = Thread.ofVirtual().name("histmersion-storage").start(() => r()) }
):

  private val last = AtomicReference[Option[(Long, Storage.Report)]](None)
  private val running = AtomicReference[Option[CompletableFuture[Storage.Report]]](None)

  def get(waitMillis: StorageCache.WaitMillis): Storage.Report =
    last.get match
      case Some((at, r)) if clock().value - at < ttlMillis().value => r
      case Some((_, r))                                =>
        val _ = kick()
        r.modify(_.measuring).setTo(Storage.Report.Measuring(true))
      case None =>
        val f = kick()
        try f.get(math.max(0L, waitMillis.value: Long), TimeUnit.MILLISECONDS)
        catch
          case _: TimeoutException   => Storage.Report(measuring = Storage.Report.Measuring(true))
          case e: ExecutionException =>
            Storage.Report(problems =
              List(Storage.Report.Problems(s"storage: ${Option(e.getCause).getOrElse(e).getMessage}"))
            )
          case _: InterruptedException => Storage.Report(measuring = Storage.Report.Measuring(true))

  /** The measurement under way, or a new one. */
  private def kick(): CompletableFuture[Storage.Report] =
    running.get match
      case Some(f) => f
      case None    =>
        val f = CompletableFuture[Storage.Report]()
        val claimed: Option[CompletableFuture[Storage.Report]] = Some(f)
        if !running.compareAndSet(None, claimed) then kick()
        else
          val work = scala.util.Try(prepare())
          spawn { () =>
            try
              val r = work.get()
              last.set(Some((clock().value, r)))
              f.complete(r)
            catch
              case e: Throwable =>
                scribe.warn(s"storage: the measurement failed: ${e.getMessage}")
                f.completeExceptionally(e)
            finally running.set(None)
            ()
          }
          f

object StorageCache:
  type TimeToLive = TimeToLive.T
  object TimeToLive extends Newtype[Long, Pure]
  type WaitMillis = WaitMillis.T
  object WaitMillis extends Newtype[Long, Pure]
  type Instant = Instant.T
  object Instant extends Newtype[Long, Pure]

