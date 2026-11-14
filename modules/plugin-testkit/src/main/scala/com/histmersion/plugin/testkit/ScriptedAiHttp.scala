package com.histmersion
package plugin.testkit
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.plugin.Header

import com.histmersion.domain.CoreQuota
import com.histmersion.plugin.{AiHttp, Plugin, WorkKey}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Doi

/** An [[AiHttp]] that sends nothing: it answers each request with the next of `answers` (once they run out, with a
  * refused `Left`, "no scripted answer") and keeps every request, to assert on what the provider sent.
  */
final class ScriptedAiHttp(answers: Either[Problem, AiHttp.Response]*) extends AiHttp:
  // the answers not yet given and the requests seen; guarded by `this` (a provider may call from several threads)
  private val left = scala.collection.mutable.Queue(answers*)
  private val seen = List.newBuilder[AiHttpRequest]

  def requests: List[AiHttpRequest] = synchronized(seen.result())

  def post(
      url: Plugin.Http.Url,
      headers: Map[Header.Name, Header.Value],
      body: AiHttp.PostBody,
      timeoutSeconds: AiHttp.TimeoutSeconds
  ): Either[Problem, AiHttp.Response] =
    next(
      AiHttpRequest(
        AiHttpRequest.Method("POST"),
        AiHttpRequest.Url(url.value),
        ScriptedAiHttp.masked(headers.map((k, v) => CoreQuota.HeaderName(k.value) -> CoreQuota.HeaderValue(v.value))),
        Some(AiHttpRequest.Body(body.value))
      )
    )

  def get(
      url: Plugin.Http.Url,
      headers: Map[Header.Name, Header.Value],
      timeoutSeconds: AiHttp.TimeoutSeconds
  ): Either[Problem, AiHttp.Response] =
    next(
      AiHttpRequest(
        AiHttpRequest.Method("GET"),
        AiHttpRequest.Url(url.value),
        ScriptedAiHttp.masked(headers.map((k, v) => CoreQuota.HeaderName(k.value) -> CoreQuota.HeaderValue(v.value))),
        None
      )
    )

  private def next(r: AiHttpRequest): Either[Problem, AiHttp.Response] = synchronized {
    seen += r
    if left.isEmpty then Left(Problem.Unclassified(Legacy.Text("no scripted answer")))
    else left.dequeue()
  }

object ScriptedAiHttp:

  private def masked(
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]
  ): Map[CoreQuota.HeaderName, CoreQuota.HeaderValue] =
    headers.map { (k, v) =>
      val n = k.value.toLowerCase(java.util.Locale.ROOT)
      k -> (if n == "authorization" || n.contains("key") || n.contains("token") || n.contains("secret") then
              CoreQuota.HeaderValue("***")
            else v)
    }

extension (work: WorkKey.type)
  /** The work a DOI names, with its title, authors and year from OpenAlex (else Crossref), as the server looks it up
    * before asking the acquisition chain ([[PluginHarness.workFromDoi]]).
    */
  def fromDoi(doi: plugin.Doi, harness: PluginHarness): Either[Problem, WorkKey] = harness.workFromDoi(doi)
