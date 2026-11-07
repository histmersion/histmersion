package com.histmersion.domain.library.acquisition

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
  def json(url: com.histmersion.domain.library.acquisition.Http.Url, headers: Map[com.histmersion.domain.library.acquisition.Http.HeaderName, com.histmersion.domain.library.acquisition.Http.JsonHeadersPart2] = Map.empty): Either[com.histmersion.domain.library.acquisition.Http.Problem, Json]
  def text(url: com.histmersion.domain.library.acquisition.Http.Url, headers: Map[com.histmersion.domain.library.acquisition.Http.HeaderName, com.histmersion.domain.library.acquisition.Http.TextHeadersPart2] = Map.empty): Either[com.histmersion.domain.library.acquisition.Http.Problem, com.histmersion.domain.library.acquisition.Http.Text]
  def bytes(url: com.histmersion.domain.library.acquisition.Http.Url, headers: Map[com.histmersion.domain.library.acquisition.Http.HeaderName, com.histmersion.domain.library.acquisition.Http.BytesHeadersPart2] = Map.empty): Either[com.histmersion.domain.library.acquisition.Http.Problem, com.histmersion.domain.library.acquisition.Http.Bytes]

  /** One request, not retried, answered as it came — the status and headers too (CORE's budget reads them). A client
    * that only has bodies (a test's) answers 200 with no headers, or its `Left`.
    */
  def once(url: com.histmersion.domain.library.acquisition.Http.Url, headers: Map[com.histmersion.domain.library.acquisition.Http.HeaderName, com.histmersion.domain.library.acquisition.Http.OnceHeadersPart2] = Map.empty): Either[com.histmersion.domain.library.acquisition.Http.Problem, HttpAnswer] =
    bytes(url, headers).map(HttpAnswer(200, Map.empty, _))

object Http:
  type Bytes = Bytes.T
  object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeaderName = HeaderName.T
  object HeaderName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** A caller's own `User-Agent` (the PMC hosts get the project's public identity) replaces the acquisition one; either
    * goes through [[HttpPolicy.userAgent]], so neither can carry an e-mail.
    */
  type JsonHeadersPart2 = JsonHeadersPart2.T
  object JsonHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextHeadersPart2 = TextHeadersPart2.T
  object TextHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BytesHeadersPart2 = BytesHeadersPart2.T
  object BytesHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OnceHeadersPart2 = OnceHeadersPart2.T
  object OnceHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val UserAgent = PolicyHttp.UserAgent
