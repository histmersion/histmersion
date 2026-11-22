package com.histmersion
package plugin

import com.histmersion.util.problem.Problem
import io.github.iltotore.iron.constraint.any.Pure
import Fetch.{Absence, Refusal}

enum Fetch:
  case Found(file: FetchedFile)

  /** No copy here. The host stores `absence` with the attempt and decides from it who acts next: `Unanswered` and
    * `RateLimited` are tried again by the agents, `NotHeld` from every fetcher means no free legal copy was found.
    */
  case Miss(absence: Absence, why: Problem)

  /** A copy exists but may not be taken. The host stores `refusal` with the attempt: `BotChallenge` tells the person to
    * download it in a browser, `LendingOnly` to borrow it. It never asks the same fetcher again for this work.
    */
  case Refused(refusal: Refusal, why: Problem)

object Fetch:

  enum Absence derives CanEqual:
    case NoIdentifier, NotHeld, Unanswered, RateLimited, Unconfigured

  enum Refusal derives CanEqual:
    case BotChallenge, LendingOnly, NoEntitlement, LicenceForbids

  def failed(why: Problem): Fetch = why match
    case _: Problem.Upstream.Challenged => Refused(Refusal.BotChallenge, why)
    case Problem.Upstream.Http(_, _, status, _, _) if status == Problem.Upstream.Status.Forbidden =>
      Refused(Refusal.BotChallenge, why)
    case Problem.Upstream.Http(_, _, status, _, _) if status.refusesAccess || status.wantsPayment =>
      Refused(Refusal.NoEntitlement, why)
    case Problem.Upstream.Http(_, _, status, _, _) if status.asksToWait    => Miss(Absence.RateLimited, why)
    case Problem.Upstream.Http(_, _, status, _, _) if status.isServerError => Miss(Absence.Unanswered, why)
    case _: Problem.Upstream.Limited | _: Problem.Upstream.Unasked         => Miss(Absence.RateLimited, why)
    case _: Problem.Upstream.Unreachable | _: Problem.Upstream.Timeout | _: Problem.Upstream.Offline |
        _: Problem.Upstream.Unanswered =>
      Miss(Absence.Unanswered, why)
    case _ => Miss(Absence.NotHeld, why)

  def firstFound(tries: Iterator[Fetch], none: => Fetch): Fetch =
    @scala.annotation.tailrec
    def loop(strongest: Option[Fetch]): Fetch =
      if !tries.hasNext then strongest.filter(weight(_) > 0).getOrElse(none)
      else
        tries.next() match
          case found: Found => found
          case other        => loop(Some(strongest.filter(s => weight(s) >= weight(other)).getOrElse(other)))
    loop(None)

  private def weight(answer: Fetch): Int = answer match
    case _: Found                                          => 3
    case _: Refused                                        => 2
    case Miss(Absence.Unanswered | Absence.RateLimited, _) => 1
    case _: Miss                                           => 0

  /** What the host lends a fetcher for ONE fetch of one work. */

  trait Context:
    /** A value computed at most once per fetch of this work and shared by every fetcher of the chain — an index record
      * two steps both read. `compute` runs on the first call for `key`; later calls return that value. Name keys
      * `<plugin id>:<what>:<identifier>`: the value is stored untyped, so a key reused with another type fails.
      */
    def memo[A](key: Context.MemoKey)(compute: => A): A

    /** The host's log, with the fetcher's id on every line. */
    def log: Plugin.Log

    /** Whether the plugin `pluginId` is registered in the generation this fetch runs in: a fetcher that reads, on
      * another plugin's behalf, what that plugin's upstream holds asks it only while that plugin is on. `false` from a
      * host that does not say.
      */
    def registered(pluginId: Context.RegisteredPluginId): Boolean = false

  object Context:
    type MemoKey = MemoKey.T
    object MemoKey extends Newtype[String, Pure]
    type RegisteredPluginId = RegisteredPluginId.T
    object RegisteredPluginId extends Newtype[String, Pure]
