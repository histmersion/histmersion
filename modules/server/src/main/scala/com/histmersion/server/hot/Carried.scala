package com.histmersion.server.hot

import java.util.concurrent.atomic.AtomicBoolean

/** Work carried to another thread with its generation pinned: [[run]] it (once; the pin is released when it returns) or
  * [[drop]] it (the pin is released, nothing runs).
  */
final class Carried[A](body: () => A, unpin: () => Unit):
  private val done = AtomicBoolean(false)
  def run(): A =
    try body()
    finally if done.compareAndSet(false, true) then unpin()
  def drop(): Unit = if done.compareAndSet(false, true) then unpin()
