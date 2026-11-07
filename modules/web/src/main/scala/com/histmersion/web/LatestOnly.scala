package com.histmersion.web

import org.scalajs.dom

import scala.scalajs.js

/** One request in flight, and the NEWEST wish waiting behind it: `apply(a)` starts `run(a, done)` at once when nothing
  * runs; while one runs, further calls only replace the waiting value, and when the running one calls `done` the newest
  * waiting value (if any) starts. Values in between are never fetched — a playback ticking 60 times a second against a
  * 20 ms answer asks ~ 50 times, not 60, and a slow answer never piles up a queue of stale ones behind it.
  */
final class LatestOnly[A](run: (A, () => Unit) => Unit):
  // `var`s: whether a run is in flight, and the newest value waiting for it
  private var busy = false
  private var waiting: Option[A] = None

  def apply(a: A): Unit =
    if busy then waiting = Some(a)
    else start(a)

  /** Forget the waiting value (playback stopped): the run in flight finishes, nothing follows it. */
  def drop(): Unit = waiting = None

  private def start(a: A): Unit =
    busy = true
    run(
      a,
      () => {
        busy = false
        val next = waiting
        waiting = None
        next.foreach(start)
      }
    )
