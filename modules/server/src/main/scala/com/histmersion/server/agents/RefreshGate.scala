package com.histmersion.server.agents

import com.histmersion.api.PlanQuota.*
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
  def exclusive[A](p: PoolId, at: => Long)(body: Option[Long] => Either[String, A]): Either[String, A] =
    val lock = locks.computeIfAbsent(p, _ => ReentrantLock())
    lock.lockInterruptibly()
    try
      val r = body(Option(last.get(p)).map(_.longValue))
      if r.isRight then { val _ = last.put(p, at) }
      r
    finally lock.unlock()

  def lastAttempt(p: PoolId): Option[Long] = Option(last.get(p)).map(_.longValue)
