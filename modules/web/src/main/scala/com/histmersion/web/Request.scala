package com.histmersion
package web

import com.raquo.laminar.api.L.*

import scala.concurrent.{ExecutionContext, Future}

/** The one request type of the views (G107, review finding W4): a view asks through it and renders its [[Loaded]]
  * state.
  *
  *   - **Newest wins.** [[ask]] supersedes every earlier request of this one still out: an older answer that arrives
  *     last is dropped, never shown over the newer one ([[Latest]] inside).
  *   - **Every outcome is a state.** A failure is [[Loaded.Failed]] with the server's typed problems; no answer at all
  *     is [[Loaded.Unreachable]], keeping what the view showed. Nothing is left on "Loading…", nothing is a bare
  *     message.
  *
  * A raw `ApiClient` call consumed by a view is counted by `ContractsSuite`'s `raw-request` ratchet until every view
  * asks through this type.
  */
final class Request[A]:
  private val guard = Latest()
  private val state: Var[Loaded[A]] = Var(Loaded.Idle)

  def signal: Signal[Loaded[A]] = state.signal

  def now: Loaded[A] = state.now()

  /** Send a request (`f` is called now): the view shows [[Loaded.Loading]] with what it showed before, then its outcome
    * — unless a newer request was asked meanwhile.
    */
  def ask(f: => Future[A])(using ExecutionContext): Unit = askThen(f)(_ => ())

  /** [[ask]], and `k` with the newest request's outcome once it is in: for a view whose content is not bound to
    * [[signal]] (a server page put into a window).
    */
  def askThen(f: => Future[A])(k: Loaded[A] => Unit)(using ExecutionContext): Unit =
    val last = state.now().shown
    state.set(Loaded.Loading(last))
    guard.onCurrent(f) { r =>
      val outcome = Loaded.of(r, last)
      state.set(outcome)
      k(outcome)
    }

  /** Drop the answers of every request still out (the view closed, its input changed to nothing). */
  def cancel(): Unit = guard.cancel()

  /** Show `a` as the answer, as a request would (a change the view made itself). */
  def set(a: A): Unit =
    guard.cancel()
    state.set(Loaded.Ready(a))
