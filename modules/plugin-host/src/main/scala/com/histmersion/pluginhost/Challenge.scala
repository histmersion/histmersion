package com.histmersion.pluginhost

import sttp.client4.*

import scala.concurrent.duration.*

/** A bot-protection CHALLENGE (Cloudflare's "Just a moment…" interstitial and its kin) rather than an answer. Such a
  * host is treated as having no copy: it is neither retried nor worked around.
  */
object Challenge:
  type Body = Body.T
  object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  def isChallenge(code: com.histmersion.domain.HttpPolicy.StatusCode, headers: Map[com.histmersion.domain.CoreQuota.HeaderName, com.histmersion.domain.CoreQuota.HeaderValue], body: com.histmersion.pluginhost.Challenge.Body): Boolean =
    val mitigated = headers.exists((k, v) => k.value.equalsIgnoreCase("cf-mitigated") && v.value.toLowerCase.contains("challenge"))
    val page = new String(body.value.take(4096), "ISO-8859-1").toLowerCase
    mitigated || isAnubis(body) || ((code.value == 403 || code.value == 429 || code.value == 503) &&
      (page.contains("<title>just a moment") || page.contains("challenge-platform") ||
        page.contains("<title>attention required")))

  /** An Anubis page (OAPEN's file server runs one): its proof-of-work challenge or its refusal, whatever the status
    * (200 by default, 401/403 as configured). Its markers are its own — the challenge's `anubis_challenge` script, its
    * assets under `/.within.website/x/` — and a file never starts with them, so any status counts; a PDF is never read
    * as one.
    */
  private def isAnubis(body: com.histmersion.pluginhost.Challenge.Body): Boolean =
    val head = new String(body.value.take(32768), "ISO-8859-1").toLowerCase
    !head.startsWith("%pdf") &&
    (head.contains("id=\"anubis_challenge\"") || (head.contains("/.within.website/x/") && head.contains("anubis")))
