package com.histmersion.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/** Drops the answers to superseded requests (#1798 item 5) — the `reqId` / `mine == reqId` guard the views spelled out
  * by hand: `start()` hands out a ticket, and only the NEWEST ticket's answer is applied, so an older response can
  * never overwrite a newer query's.
  */
final class Latest:
  // `var`: the newest ticket handed out
  private var newest: com.histmersion.web.Latest.Ticket = 0

  /** A new request: the tickets handed out before it are superseded. */
  def start(): com.histmersion.web.Latest.Ticket = { newest += 1; newest }

  /** Whether `ticket` is still the newest request. */
  def isCurrent(ticket: com.histmersion.web.Latest.Ticket): Boolean = ticket == newest

  /** Supersede every request still out: none of their answers is applied (a layer switched off, an input unmounted).
    */
  def cancel(): Unit = { val _ = start() }

  /** `f`'s raw outcome, handed to `k` only while it is the newest request — for a view that answers a failure its own
    * way (or not at all), and the guard each view used to spell out with a counter.
    */
  def onCurrent[A](f: Future[A])(k: Try[A] => Unit)(using ExecutionContext): Unit =
    val ticket = start()
    f.onComplete(r => if isCurrent(ticket) then k(r))

  /** `f`'s outcome, applied only while it is the newest request: `ok` on success, `failed` with the error message
    * otherwise — a failure is never left unanswered (a view stuck on "Loading…").
    */
  def apply[A](f: Future[A])(ok: A => Unit, failed: com.histmersion.web.Latest.Problem => Unit)(using ExecutionContext): Unit =
    val ticket = start()
    f.onComplete(r => if isCurrent(ticket) then answer(r, ok, failed))

  private[web] def answer[A](r: Try[A], ok: A => Unit, failed: com.histmersion.web.Latest.Problem => Unit): Unit = r match
    case Success(a) => ok(a)
    case Failure(e) => failed(Option(e.getMessage).filter(_.nonEmpty).getOrElse(e.toString))

object Latest:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Ticket = Ticket.T
  object Ticket extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
