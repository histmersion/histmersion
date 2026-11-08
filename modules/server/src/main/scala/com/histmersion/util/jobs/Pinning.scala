package com.histmersion
package util.jobs

/** Keeping work on the generation it started on (docs/research-plugins.md §3.6). The server's [[HotGraph]] is one;
  * [[Pinning.none]] is for code running without a graph (a test's fixed `Deps`).
  */
trait Pinning extends WorkCarrier:
  /** Run `f` pinned: on the calling thread's generation when it has one, else on the current one. */
  def pinned[A](f: => A): A

  /** `f`, to be run later — on another thread — on the generation the calling thread sees NOW. */
  def carry[A](f: => A): Carried[A]

  def carryWork(work: () => Unit): () => Unit =
    val carried = carry(work())
    () => carried.run()

object Pinning:
  val none: Pinning = new Pinning:
    def pinned[A](f: => A): A = f
    def carry[A](f: => A): Carried[A] = Carried(() => f, () => ())
