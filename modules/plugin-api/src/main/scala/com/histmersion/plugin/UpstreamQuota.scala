package com.histmersion.plugin

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
  def take(purpose: com.histmersion.plugin.UpstreamQuota.TakePurpose): Either[com.histmersion.plugin.UpstreamQuota.TakeResultPart1, Unit]

  /** What the upstream answered the call taken last: its status and headers (names in lower case). */
  def observe(status: com.histmersion.plugin.UpstreamQuota.ObserveStatus, headers: Map[com.histmersion.plugin.UpstreamQuota.ObserveHeadersPart1, com.histmersion.plugin.UpstreamQuota.ObserveHeadersPart2]): Unit

  /** The time the upstream asked not to be called before, as the budget keeps it (ISO-8601, `2026-10-26T18:52:11Z`),
    * once an answer asked to wait; `None` while it may be called.
    */
  def waitingUntil: Option[com.histmersion.plugin.UpstreamQuota.WaitingUntilResult] = None

object UpstreamQuota:
  /** No limit: every call may go, nothing is kept. What a host without a budget for the upstream lends. */
  type TakeResultPart1 = TakeResultPart1.T
  object TakeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TakePurpose = TakePurpose.T
  object TakePurpose extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ObserveStatus = _root_.com.histmersion.plugin.Plugin.HttpResponse.Status
  val ObserveStatus: _root_.com.histmersion.plugin.Plugin.HttpResponse.Status.type = _root_.com.histmersion.plugin.Plugin.HttpResponse.Status
  type ObserveHeadersPart1 = _root_.com.histmersion.plugin.Plugin.HttpResponse.HeadersKey
  val ObserveHeadersPart1: _root_.com.histmersion.plugin.Plugin.HttpResponse.HeadersKey.type = _root_.com.histmersion.plugin.Plugin.HttpResponse.HeadersKey
  type ObserveHeadersPart2 = _root_.com.histmersion.plugin.Plugin.HttpResponse.HeadersValue
  val ObserveHeadersPart2: _root_.com.histmersion.plugin.Plugin.HttpResponse.HeadersValue.type = _root_.com.histmersion.plugin.Plugin.HttpResponse.HeadersValue
  type WaitingUntilResult = WaitingUntilResult.T
  object WaitingUntilResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Unlimited: UpstreamQuota = new UpstreamQuota:
    def take(purpose: com.histmersion.plugin.UpstreamQuota.TakePurpose): Either[com.histmersion.plugin.UpstreamQuota.TakeResultPart1, Unit] = Right(())
    def observe(status: com.histmersion.plugin.UpstreamQuota.ObserveStatus, headers: Map[com.histmersion.plugin.UpstreamQuota.ObserveHeadersPart1, com.histmersion.plugin.UpstreamQuota.ObserveHeadersPart2]): Unit = ()
