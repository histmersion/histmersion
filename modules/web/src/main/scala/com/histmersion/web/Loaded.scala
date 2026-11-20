package com.histmersion
package web

import com.histmersion.api.Api

import scala.util.{Failure, Success, Try}

/** What a view shows of one request (G107, review finding W4): every outcome is a state the view renders, none is
  * dropped or flattened to a message.
  *
  *   - [[Loaded.Ready]]: the answer;
  *   - [[Loaded.Failed]]: the server answered with problems, kept typed as it sent them, with the HTTP status (a 412
  *     conflict and a 500 are told apart);
  *   - [[Loaded.Unreachable]]: no answer at all (the server is down or restarting, the network is gone) — not a problem
  *     of the request, so what the view showed before stays (`last`);
  *   - [[Loaded.Loading]]: asked, no answer yet, `last` still shown meanwhile; [[Loaded.Idle]]: not asked yet.
  *
  * A cached answer shown while the server cannot be reached (the offline design, notes/design-offline-web.md §3.2) is
  * one more state of this type, carrying the answer and when it was fetched; the views that render `last` already show
  * what it would hold.
  */
enum Loaded[+A] derives CanEqual:
  case Idle
  case Loading(last: Option[A])
  case Ready(value: A)
  case Failed(status: ApiFailure.Status, error: Api.Error)
  case Unreachable(last: Option[A])

  /** The answer this state shows: the new one, or the one kept while loading or unreachable. */
  def shown: Option[A] = this match
    case Ready(a)       => Some(a)
    case Loading(l)     => l
    case Unreachable(l) => l
    case Idle           => None
    case Failed(_, _)   => None

  def isLoading: Boolean = this match
    case Loading(_) => true
    case _          => false

object Loaded:

  /** A request's outcome as a state: a [[ApiFailure]] keeps the server's problems and status, a [[ServerUnreachable]]
    * keeps `last`. Pure.
    */
  def of[A](outcome: Try[A], last: Option[A]): Loaded[A] = outcome match
    case Success(a)                    => Ready(a)
    case Failure(f: ApiFailure)        => Failed(f.status, f.error)
    case Failure(_: ServerUnreachable) => Unreachable(last)
    case Failure(other)                => Failed(ApiFailure.Status(0), ServerUnreachable.unexpected(other))
