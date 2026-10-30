package com.histmersion.server.acquire

import com.histmersion.domain.HttpPolicy

import com.histmersion.pluginhost.{HttpAnswer, PolicyHttp}
import io.circe.Json

/** The ONE seam between the acquisition toolset and the network.
  *
  * Every upstream client is written against this trait, so a test supplies a fake that answers from a captured fixture
  * and never opens a socket. It is deliberately tiny — three verbs — because the interesting logic is in the pure
  * parsers, not here.
  *
  * Failures are `Left(message)`, never exceptions: an unreachable API is an expected condition for a tool whose whole
  * job is talking to flaky third parties.
  */
trait Http:
  def json(url: String, headers: Map[String, String] = Map.empty): Either[String, Json]
  def text(url: String, headers: Map[String, String] = Map.empty): Either[String, String]
  def bytes(url: String, headers: Map[String, String] = Map.empty): Either[String, Array[Byte]]

  /** One request, not retried, answered as it came — the status and headers too (CORE's budget reads them). A client
    * that only has bodies (a test's) answers 200 with no headers, or its `Left`.
    */
  def once(url: String, headers: Map[String, String] = Map.empty): Either[String, HttpAnswer] =
    bytes(url, headers).map(HttpAnswer(200, Map.empty, _))

object Http:
  /** A caller's own `User-Agent` (the PMC hosts get the project's public identity) replaces the acquisition one; either
    * goes through [[HttpPolicy.userAgent]], so neither can carry an e-mail.
    */
  val UserAgent = PolicyHttp.UserAgent
