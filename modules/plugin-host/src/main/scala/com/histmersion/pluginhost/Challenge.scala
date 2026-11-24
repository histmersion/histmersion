package com.histmersion
package pluginhost

import sttp.client4.*

import scala.concurrent.duration.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.HttpPolicy
import com.histmersion.domain.CoreQuota
import com.histmersion.lint.{LintRule, tolerated}

/** A bot-protection CHALLENGE (Cloudflare's "Just a moment…" interstitial and its kin) rather than an answer. Such a
  * host is treated as having no copy: it is neither retried nor worked around.
  */
object Challenge:
  type Body = Body.T
  object Body extends Newtype[Array[Byte], Pure]
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def isChallenge(
      code: HttpPolicy.StatusCode,
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue],
      body: Body
  ): Boolean =
    val mitigated =
      headers.exists((k, v) => k.value.equalsIgnoreCase("cf-mitigated") && v.value.toLowerCase.contains("challenge"))
    val page = new String(body.value.take(4096), "ISO-8859-1").toLowerCase
    mitigated || isAnubis(body) || ((code.value == 403 || code.value == 429 || code.value == 503) &&
      (page.contains("<title>just a moment") || page.contains("challenge-platform") ||
        page.contains("<title>attention required")))

  /** An Anubis page (OAPEN's file server runs one): its proof-of-work challenge or its refusal, whatever the status
    * (200 by default, 401/403 as configured). Its markers are its own — the challenge's `anubis_challenge` script, its
    * assets under `/.within.website/x/` — and a file never starts with them, so any status counts; a PDF is never read
    * as one.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def isAnubis(body: Body): Boolean =
    val head = new String(body.value.take(32768), "ISO-8859-1").toLowerCase
    !head.startsWith("%pdf") &&
    (head.contains("id=\"anubis_challenge\"") || (head.contains("/.within.website/x/") && head.contains("anubis")))
