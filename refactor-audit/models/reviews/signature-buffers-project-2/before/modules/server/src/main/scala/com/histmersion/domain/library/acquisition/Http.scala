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
  def json(url: com.histmersion.domain.library.acquisition.Http.JsonUrl, headers: Map[com.histmersion.domain.library.acquisition.Http.JsonHeadersPart1, com.histmersion.domain.library.acquisition.Http.JsonHeadersPart2] = Map.empty): Either[com.histmersion.domain.library.acquisition.Http.JsonResultPart1, Json]
  def text(url: com.histmersion.domain.library.acquisition.Http.TextUrl, headers: Map[com.histmersion.domain.library.acquisition.Http.TextHeadersPart1, com.histmersion.domain.library.acquisition.Http.TextHeadersPart2] = Map.empty): Either[com.histmersion.domain.library.acquisition.Http.TextResultPart1, com.histmersion.domain.library.acquisition.Http.TextResultPart2]
  def bytes(url: com.histmersion.domain.library.acquisition.Http.BytesUrl, headers: Map[com.histmersion.domain.library.acquisition.Http.BytesHeadersPart1, com.histmersion.domain.library.acquisition.Http.BytesHeadersPart2] = Map.empty): Either[com.histmersion.domain.library.acquisition.Http.BytesResultPart1, Array[com.histmersion.domain.library.acquisition.Http.BytesResultPart2]]

  /** One request, not retried, answered as it came — the status and headers too (CORE's budget reads them). A client
    * that only has bodies (a test's) answers 200 with no headers, or its `Left`.
    */
  def once(url: com.histmersion.domain.library.acquisition.Http.OnceUrl, headers: Map[com.histmersion.domain.library.acquisition.Http.OnceHeadersPart1, com.histmersion.domain.library.acquisition.Http.OnceHeadersPart2] = Map.empty): Either[com.histmersion.domain.library.acquisition.Http.OnceResultPart1, HttpAnswer] =
    bytes(url, headers).map(HttpAnswer(200, Map.empty, _))

object Http:
  /** A caller's own `User-Agent` (the PMC hosts get the project's public identity) replaces the acquisition one; either
    * goes through [[HttpPolicy.userAgent]], so neither can carry an e-mail.
    */
  type JsonResultPart1 = JsonResultPart1.T
  object JsonResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JsonUrl = JsonUrl.T
  object JsonUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JsonHeadersPart1 = JsonHeadersPart1.T
  object JsonHeadersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JsonHeadersPart2 = JsonHeadersPart2.T
  object JsonHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextResultPart1 = TextResultPart1.T
  object TextResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextResultPart2 = TextResultPart2.T
  object TextResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextUrl = TextUrl.T
  object TextUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextHeadersPart1 = TextHeadersPart1.T
  object TextHeadersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextHeadersPart2 = TextHeadersPart2.T
  object TextHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BytesResultPart1 = BytesResultPart1.T
  object BytesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BytesResultPart2 = BytesResultPart2.T
  object BytesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BytesUrl = BytesUrl.T
  object BytesUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BytesHeadersPart1 = BytesHeadersPart1.T
  object BytesHeadersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BytesHeadersPart2 = BytesHeadersPart2.T
  object BytesHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OnceResultPart1 = OnceResultPart1.T
  object OnceResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OnceUrl = OnceUrl.T
  object OnceUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OnceHeadersPart1 = OnceHeadersPart1.T
  object OnceHeadersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OnceHeadersPart2 = OnceHeadersPart2.T
  object OnceHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val UserAgent = PolicyHttp.UserAgent
