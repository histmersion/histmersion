package com.histmersion.server.agents

import com.histmersion.api.automation.PlanQuota.*
import io.circe.{parser, Json}

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import scala.jdk.CollectionConverters.*

/** One refresh at a time per pool, and the time of its last attempt: a second caller WAITS for the one in flight and
  * then sees what it recorded (single flight), instead of reading again.
  */
final class RefreshGate:
  private val locks = ConcurrentHashMap[PoolId, ReentrantLock]()
  private val last = ConcurrentHashMap[PoolId, java.lang.Long]()

  /** Run `body` alone for `p`, given the pool's last attempt. A `Right` is an attempt (successful or not), stamped `at`
    * for the throttle; a `Left` is "not taken", and leaves the stamp alone.
    */
  def exclusive[A](p: PoolId, at: => Long)(body: Option[com.histmersion.server.agents.RefreshGate.Instant] => Either[com.histmersion.server.agents.RefreshGate.Problem, A]): Either[com.histmersion.server.agents.RefreshGate.Problem, A] =
    val lock = locks.computeIfAbsent(p, _ => ReentrantLock())
    lock.lockInterruptibly()
    try
      val r = body(Option(last.get(p)).map(_.longValue))
      if r.isRight then { val _ = last.put(p, at) }
      r
    finally lock.unlock()

  def lastAttempt(p: PoolId): Option[com.histmersion.server.agents.RefreshGate.Instant] = Option(last.get(p)).map(_.longValue)

object RefreshGate:
  type Instant = Instant.T
  object Instant extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
