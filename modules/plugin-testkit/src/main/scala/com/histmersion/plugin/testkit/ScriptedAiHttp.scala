package com.histmersion.plugin.testkit

import com.histmersion.plugin.{AiHttp, WorkKey}

/** An [[AiHttp]] that sends nothing: it answers each request with the next of `answers` (once they run out, with
  * `Left("no scripted answer")`) and keeps every request, to assert on what the provider sent.
  */
final class ScriptedAiHttp(answers: Either[String, AiHttp.Response]*) extends AiHttp:
  // the answers not yet given and the requests seen; guarded by `this` (a provider may call from several threads)
  private val left = scala.collection.mutable.Queue(answers*)
  private val seen = List.newBuilder[AiHttpRequest]

  def requests: List[AiHttpRequest] = synchronized(seen.result())

  def post(
      url: com.histmersion.plugin.testkit.ScriptedAiHttp.PostUrl,
      headers: Map[com.histmersion.domain.CoreQuota.HeaderName, com.histmersion.plugin.testkit.ScriptedAiHttp.PostHeadersPart2],
      body: com.histmersion.plugin.testkit.ScriptedAiHttp.RequestBody,
      timeoutSeconds: com.histmersion.plugin.testkit.ScriptedAiHttp.TimeoutSeconds
  ): Either[com.histmersion.plugin.testkit.ScriptedAiHttp.Problem, AiHttp.Response] =
    next(AiHttpRequest("POST", url, ScriptedAiHttp.masked(headers), Some(body)))

  def get(url: com.histmersion.plugin.testkit.ScriptedAiHttp.GetUrl, headers: Map[com.histmersion.domain.CoreQuota.HeaderName, com.histmersion.plugin.testkit.ScriptedAiHttp.GetHeadersPart2], timeoutSeconds: com.histmersion.plugin.testkit.ScriptedAiHttp.TimeoutSeconds): Either[com.histmersion.plugin.testkit.ScriptedAiHttp.Problem, AiHttp.Response] =
    next(AiHttpRequest("GET", url, ScriptedAiHttp.masked(headers), None))

  private def next(r: AiHttpRequest): Either[com.histmersion.plugin.testkit.ScriptedAiHttp.Problem, AiHttp.Response] = synchronized {
    seen += r
    if left.isEmpty then Left("no scripted answer") else left.dequeue()
  }

object ScriptedAiHttp:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestBody = RequestBody.T
  object RequestBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PostUrl = _root_.com.histmersion.plugin.testkit.AiHttpRequest.Url
  val PostUrl: _root_.com.histmersion.plugin.testkit.AiHttpRequest.Url.type = _root_.com.histmersion.plugin.testkit.AiHttpRequest.Url
  type PostHeadersPart2 = PostHeadersPart2.T
  object PostHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetUrl = _root_.com.histmersion.plugin.testkit.AiHttpRequest.Url
  val GetUrl: _root_.com.histmersion.plugin.testkit.AiHttpRequest.Url.type = _root_.com.histmersion.plugin.testkit.AiHttpRequest.Url
  type GetHeadersPart2 = GetHeadersPart2.T
  object GetHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaskedResultPart2 = MaskedResultPart2.T
  object MaskedResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaskedHeadersPart2 = MaskedHeadersPart2.T
  object MaskedHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def masked(headers: Map[com.histmersion.domain.CoreQuota.HeaderName, com.histmersion.plugin.testkit.ScriptedAiHttp.MaskedHeadersPart2]): Map[com.histmersion.domain.CoreQuota.HeaderName, com.histmersion.plugin.testkit.ScriptedAiHttp.MaskedResultPart2] =
    headers.map { (k, v) =>
      val n = k.toLowerCase(java.util.Locale.ROOT)
      k -> (if n == "authorization" || n.contains("key") || n.contains("token") || n.contains("secret") then "***"
            else v)
    }

extension (work: WorkKey.type)
  /** The work a DOI names, with its title, authors and year from OpenAlex (else Crossref), as the server looks it up
    * before asking the acquisition chain ([[PluginHarness.workFromDoi]]).
    */
  def fromDoi(doi: String, harness: PluginHarness): Either[String, WorkKey] = harness.workFromDoi(doi)
