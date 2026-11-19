package com.histmersion
package server.hot

import com.histmersion.util.jobs.{Carried, Pinning}

import hearth.kindlings.optics.syntax.*
import com.histmersion.config.{HotNode, HotPlan}
import ox.{supervised, Ox}

import java.util.concurrent.{CompletableFuture, CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}
import java.util.concurrent.locks.ReentrantLock
import scala.concurrent.duration.*
import scala.util.control.NonFatal

/** What a node's build is given: the config being switched to, the values of its dependencies in the generation being
  * built, and — on a rebuild — its own previous value and config (so a node may carry over what did not change, as the
  * plugin node does per plugin).
  */
final class BuildCtx[C, A] private[hot] (
    val config: C,
    val previous: Option[A],
    val previousConfig: Option[C],
    /** The config keys this reload changed (empty for the first build). */
    val changed: List[BuildCtx.Changed],
    dep: Hot.Graph.Def.Name => Any
):
  /** A dependency's value in the generation being built. */
  def apply[B](h: Hot[B]): B =
    // the map behind `dep` holds, under a node's name, a value of the type that node's Hot was made
    // with (HotGraph.node is the only writer), so the name fixes the type
    dep(h.name).asInstanceOf[B]

object BuildCtx:
  export com.histmersion.config.HotNode.Name as Changed
