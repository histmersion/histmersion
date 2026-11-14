package com.histmersion
package server.agents

import com.histmersion.api.automation.PlanQuota.*

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure

/** One refresh at a time per pool, and the time of its last attempt: a second caller WAITS for the one in flight and
  * then sees what it recorded (single flight), instead of reading again.
  */
final class RefreshGate:
  private val locks = ConcurrentHashMap[PoolId, ReentrantLock]()
  private val last = ConcurrentHashMap[PoolId, java.lang.Long]()

  /** Run `body` alone for `p`, given the pool's last attempt. A `Right` is an attempt (successful or not), stamped `at`
    * for the throttle; a `Left` is "not taken", and leaves the stamp alone.
    */
  def exclusive[E, A](p: PoolId, at: => Long)(
      body: Option[RefreshGate.Instant] => Either[E, A]
  ): Either[E, A] =
    val lock = locks.computeIfAbsent(p, _ => ReentrantLock())
    lock.lockInterruptibly()
    try
      val r = body(Option(last.get(p)).map(l => RefreshGate.Instant(l.longValue)))
      if r.isRight then { val _ = last.put(p, at) }
      r
    finally lock.unlock()

  def lastAttempt(p: PoolId): Option[RefreshGate.Instant] =
    Option(last.get(p)).map(l => RefreshGate.Instant(l.longValue))

object RefreshGate:
  type Instant = Instant.T
  object Instant extends Newtype[Long, Pure]
