package com.histmersion.server.hot

import java.util.concurrent.atomic.AtomicBoolean

/** Keeping work on the generation it started on (docs/research-plugins.md §3.6). The server's
  * [[HotGraph]] is one; [[Pinning.none]] is for code running without a graph (a test's fixed `Deps`). */
trait Pinning:
  /** Run `f` pinned: on the calling thread's generation when it has one, else on the current one. */
  def pinned[A](f: => A): A

  /** `f`, to be run later — on another thread — on the generation the calling thread sees NOW. */
  def carry[A](f: => A): Carried[A]

object Pinning:
  val none: Pinning = new Pinning:
    def pinned[A](f: => A): A = f
    def carry[A](f: => A): Carried[A] = Carried(() => f, () => ())

/** Work carried to another thread with its generation pinned: [[run]] it (once; the pin is released
  * when it returns) or [[drop]] it (the pin is released, nothing runs). */
final class Carried[A](body: () => A, unpin: () => Unit):
  private val done = AtomicBoolean(false)
  def run(): A =
    try body()
    finally if done.compareAndSet(false, true) then unpin()
  def drop(): Unit = if done.compareAndSet(false, true) then unpin()
