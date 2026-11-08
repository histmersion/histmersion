package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

/** A metered upstream's budget, kept by the host for the whole server — across plugin generations, config reloads and
  * restarts — and shared with the host's own calls to the same upstream. A plugin asks [[Plugin.Context.quota]] for it
  * by the upstream's name.
  *
  * A call goes: [[take]] (the host may wait for the call's slot, or refuse it), then the request, sent once
  * ([[Plugin.Http.fetch]]), then [[observe]] with what the upstream answered, which the budget reads for a slow-down or
  * the upstream's own rate headers.
  *
  * {{{
  * val core = ctx.quota("core")
  * core.take("lookup") match
  *   case Left(why) => Fetch.Miss(s"CORE not asked: $why")
  *   case Right(_)  =>
  *     ctx.http.fetch(url, Map("Authorization" -> s"Bearer $key")).map { r => core.observe(r.status, r.headers); r }
  * }}}
  */
trait UpstreamQuota:
  /** Take one call's share for `purpose` (the upstream's own names: `lookup`, `search`), waiting for the call's slot
    * when the budget paces calls. `Left` says why the call is not made now — the upstream asked to wait, the day's
    * budget is spent, the slot is too far — and nothing is counted then.
    */
  def take(purpose: UpstreamQuota.TakePurpose): Either[Problem, Unit]

  /** What the upstream answered the call taken last: its status and headers (names in lower case). */
  def observe(
      status: UpstreamQuota.ObserveStatus,
      headers: Map[Header.Name, Header.Value]
  ): Unit

  /** The time the upstream asked not to be called before, as the budget keeps it (ISO-8601, `2026-10-26T18:52:11Z`),
    * once an answer asked to wait; `None` while it may be called.
    */
  def waitingUntil: Option[UpstreamQuota.WaitingUntil] = None

object UpstreamQuota:
  /** No limit: every call may go, nothing is kept. What a host without a budget for the upstream lends. */
  type TakePurpose = TakePurpose.T
  object TakePurpose extends Newtype[String, Pure]
  type ObserveStatus = Plugin.HttpResponse.Status
  val ObserveStatus: Plugin.HttpResponse.Status.type = Plugin.HttpResponse.Status
  type WaitingUntil = WaitingUntil.T
  object WaitingUntil extends Newtype[String, Pure]

  val Unlimited: UpstreamQuota = new UpstreamQuota:
    def take(purpose: TakePurpose): Either[Problem, Unit] = Right(())
    def observe(status: ObserveStatus, headers: Map[Header.Name, Header.Value]): Unit = ()
