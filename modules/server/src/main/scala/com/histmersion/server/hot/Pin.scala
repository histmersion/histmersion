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

/** A pin on one generation: while it is held, none of that generation's components is closed. */
final class Pin private[hot] (private[hot] val snap: HotGraph.Snap[?]):
  private val released = AtomicBoolean(false)
  def generation: Long = snap.generation
  def release(): Unit = if released.compareAndSet(false, true) then snap.release()
