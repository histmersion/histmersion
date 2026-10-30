package com.histmersion.pluginhost

import sttp.client4.*

import scala.concurrent.duration.*

/** A bot-protection CHALLENGE (Cloudflare's "Just a moment…" interstitial and its kin) rather than an answer. Such a
  * host is treated as having no copy: it is neither retried nor worked around.
  */
object Challenge:
  def isChallenge(code: Int, headers: Map[String, String], body: Array[Byte]): Boolean =
    val mitigated = headers.exists((k, v) => k.equalsIgnoreCase("cf-mitigated") && v.toLowerCase.contains("challenge"))
    val page = new String(body.take(4096), "ISO-8859-1").toLowerCase
    mitigated || isAnubis(body) || ((code == 403 || code == 429 || code == 503) &&
      (page.contains("<title>just a moment") || page.contains("challenge-platform") ||
        page.contains("<title>attention required")))

  /** An Anubis page (OAPEN's file server runs one): its proof-of-work challenge or its refusal, whatever the status
    * (200 by default, 401/403 as configured). Its markers are its own — the challenge's `anubis_challenge` script, its
    * assets under `/.within.website/x/` — and a file never starts with them, so any status counts; a PDF is never read
    * as one.
    */
  private def isAnubis(body: Array[Byte]): Boolean =
    val head = new String(body.take(32768), "ISO-8859-1").toLowerCase
    !head.startsWith("%pdf") &&
    (head.contains("id=\"anubis_challenge\"") || (head.contains("/.within.website/x/") && head.contains("anubis")))
