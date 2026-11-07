package com.histmersion.plugin.testkit

import com.histmersion.domain.CoreQuota
import com.histmersion.plugin.{AiHttp, WorkKey}

/** An [[AiHttp]] that sends nothing: it answers each request with the next of `answers` (once they run out, with
  * `Left("no scripted answer")`) and keeps every request, to assert on what the provider sent.
  */
final class ScriptedAiHttp(answers: Either[ScriptedAiHttp.Problem, AiHttp.Response]*) extends AiHttp:
  // the answers not yet given and the requests seen; guarded by `this` (a provider may call from several threads)
  private val left = scala.collection.mutable.Queue(answers*)
  private val seen = List.newBuilder[AiHttpRequest]

  def requests: List[AiHttpRequest] = synchronized(seen.result())

  def post(
      url: AiHttp.PostUrl,
      headers: Map[AiHttp.PostHeadersPart1, AiHttp.PostHeadersPart2],
      body: AiHttp.PostBody,
      timeoutSeconds: AiHttp.PostTimeoutSeconds
  ): Either[AiHttp.PostResultPart1, AiHttp.Response] =
    next(
      AiHttpRequest(
        AiHttpRequest.Method("POST"),
        AiHttpRequest.Url(url.value),
        ScriptedAiHttp.masked(headers.map((k, v) => CoreQuota.HeaderName(k.value) -> CoreQuota.HeaderValue(v.value))),
        Some(AiHttpRequest.Body(body.value))
      )
    ).left.map(p => AiHttp.PostResultPart1(p.value))

  def get(
      url: AiHttp.GetUrl,
      headers: Map[AiHttp.GetHeadersPart1, AiHttp.GetHeadersPart2],
      timeoutSeconds: AiHttp.GetTimeoutSeconds
  ): Either[AiHttp.GetResultPart1, AiHttp.Response] =
    next(
      AiHttpRequest(
        AiHttpRequest.Method("GET"),
        AiHttpRequest.Url(url.value),
        ScriptedAiHttp.masked(headers.map((k, v) => CoreQuota.HeaderName(k.value) -> CoreQuota.HeaderValue(v.value))),
        None
      )
    ).left.map(p => AiHttp.GetResultPart1(p.value))

  private def next(r: AiHttpRequest): Either[ScriptedAiHttp.Problem, AiHttp.Response] = synchronized {
    seen += r
    if left.isEmpty then Left(ScriptedAiHttp.Problem("no scripted answer")) else left.dequeue()
  }

object ScriptedAiHttp:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def masked(headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]): Map[CoreQuota.HeaderName, CoreQuota.HeaderValue] =
    headers.map { (k, v) =>
      val n = k.value.toLowerCase(java.util.Locale.ROOT)
      k -> (if n == "authorization" || n.contains("key") || n.contains("token") || n.contains("secret") then CoreQuota.HeaderValue("***")
            else v)
    }

extension (work: WorkKey.type)
  /** The work a DOI names, with its title, authors and year from OpenAlex (else Crossref), as the server looks it up
    * before asking the acquisition chain ([[PluginHarness.workFromDoi]]).
    */
  def fromDoi(doi: com.histmersion.domain.library.Doi, harness: PluginHarness): Either[PluginHarness.Problem, WorkKey] = harness.workFromDoi(doi)
