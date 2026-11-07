package com.histmersion.server.hot

import com.histmersion.util.jobs.{Carried, Pinning}

import hearth.kindlings.optics.syntax.*
import com.histmersion.config.{HotNode, HotPlan}
import ox.{supervised, Ox}

import java.util.concurrent.{CompletableFuture, CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}
import java.util.concurrent.locks.ReentrantLock
import scala.concurrent.duration.*
import scala.util.control.NonFatal
import com.histmersion.util.jobs.Pinning
import sttp.monad.MonadError
import sttp.shared.Identity
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.{EndpointInterceptor, RequestHandler, RequestInterceptor, RequestResult, Responder}

/** A pin on one generation: while it is held, none of that generation's components is closed. */
final class Pin private[hot] (private[hot] val snap: Hot.Graph.Snap[?]):
  private val released = AtomicBoolean(false)
  def generation: com.histmersion.server.hot.Hot.Graph.Instance.Generation = snap.generation
  def release(): Unit = if released.compareAndSet(false, true) then snap.release()

object Pin:

  /** Every HTTP request — REST, MCP, pages — runs pinned to the generation that was current when it arrived
    * (docs/research-plugins.md §3.6): it reads one config, takes every connection from one pool, and a reload meanwhile
    * does not close either under it. The netty-sync interpreter runs the whole server logic on the request's own
    * thread, which is what makes a thread-local pin hold for it.
    */

  final class Requests(pins: Pinning) extends RequestInterceptor[Identity]:
    def apply[R, B](
        responder: Responder[Identity, B],
        requestHandler: EndpointInterceptor[Identity] => RequestHandler[Identity, R, B]
    ): RequestHandler[Identity, R, B] =
      val next = requestHandler(EndpointInterceptor.noop)
      new RequestHandler[Identity, R, B]:
        def apply(request: ServerRequest, endpoints: List[ServerEndpoint[R, Identity]])(using
            monad: MonadError[Identity]
        ): RequestResult[B] =
          pins.pinned(next(request, endpoints))
