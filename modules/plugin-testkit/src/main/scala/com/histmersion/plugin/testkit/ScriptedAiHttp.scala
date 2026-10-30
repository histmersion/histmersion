package com.histmersion.plugin.testkit

import com.histmersion.plugin.{AiHttp, AiHttpResponse, WorkKey}

/** An [[AiHttp]] that sends nothing: it answers each request with the next of `answers` (once they run out, with
  * `Left("no scripted answer")`) and keeps every request, to assert on what the provider sent.
  */
final class ScriptedAiHttp(answers: Either[String, AiHttpResponse]*) extends AiHttp:
  // the answers not yet given and the requests seen; guarded by `this` (a provider may call from several threads)
  private val left = scala.collection.mutable.Queue(answers*)
  private val seen = List.newBuilder[AiHttpRequest]

  def requests: List[AiHttpRequest] = synchronized(seen.result())

  def post(
      url: String,
      headers: Map[String, String],
      body: String,
      timeoutSeconds: Int
  ): Either[String, AiHttpResponse] =
    next(AiHttpRequest("POST", url, ScriptedAiHttp.masked(headers), Some(body)))

  def get(url: String, headers: Map[String, String], timeoutSeconds: Int): Either[String, AiHttpResponse] =
    next(AiHttpRequest("GET", url, ScriptedAiHttp.masked(headers), None))

  private def next(r: AiHttpRequest): Either[String, AiHttpResponse] = synchronized {
    seen += r
    if left.isEmpty then Left("no scripted answer") else left.dequeue()
  }

object ScriptedAiHttp:
  private def masked(headers: Map[String, String]): Map[String, String] =
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
