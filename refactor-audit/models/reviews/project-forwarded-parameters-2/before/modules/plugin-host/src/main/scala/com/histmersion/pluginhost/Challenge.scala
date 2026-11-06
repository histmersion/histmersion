package com.histmersion.pluginhost

import sttp.client4.*

import scala.concurrent.duration.*

/** A bot-protection CHALLENGE (Cloudflare's "Just a moment…" interstitial and its kin) rather than an answer. Such a
  * host is treated as having no copy: it is neither retried nor worked around.
  */
object Challenge:
  type IsChallengeBody = IsChallengeBody.T
  object IsChallengeBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsAnubisBody = IsAnubisBody.T
  object IsAnubisBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type IsChallengeResult = IsChallengeResult.T
  object IsChallengeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsChallengeCode = IsChallengeCode.T
  object IsChallengeCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsChallengeHeadersPart1 = IsChallengeHeadersPart1.T
  object IsChallengeHeadersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsChallengeHeadersPart2 = IsChallengeHeadersPart2.T
  object IsChallengeHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsAnubisResult = IsAnubisResult.T
  object IsAnubisResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def isChallenge(code: com.histmersion.pluginhost.Challenge.IsChallengeCode, headers: Map[com.histmersion.pluginhost.Challenge.IsChallengeHeadersPart1, com.histmersion.pluginhost.Challenge.IsChallengeHeadersPart2], body: com.histmersion.pluginhost.Challenge.IsChallengeBody): com.histmersion.pluginhost.Challenge.IsChallengeResult =
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
  private def isAnubis(body: com.histmersion.pluginhost.Challenge.IsAnubisBody): com.histmersion.pluginhost.Challenge.IsAnubisResult =
    val head = new String(body.take(32768), "ISO-8859-1").toLowerCase
    !head.startsWith("%pdf") &&
    (head.contains("id=\"anubis_challenge\"") || (head.contains("/.within.website/x/") && head.contains("anubis")))
