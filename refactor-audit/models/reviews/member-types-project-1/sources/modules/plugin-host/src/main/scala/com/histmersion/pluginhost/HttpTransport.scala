package com.histmersion.pluginhost

import scala.jdk.CollectionConverters.*

/** One GET on the wire: the seam between the host's HTTP policies ([[Polite]], [[PolicyHttp]]) and the network. A test
  * supplies one that answers from fixtures or recordings and never opens a socket. Failures to talk at all are `Left`;
  * any HTTP answer, a 429 included, is a `Right` — the policy decides what it means.
  */
trait HttpTransport:
  def get(url: com.histmersion.pluginhost.HttpTransport.GetUrl, headers: Map[com.histmersion.pluginhost.HttpTransport.GetHeadersPart1, com.histmersion.pluginhost.HttpTransport.GetHeadersPart2]): Either[com.histmersion.pluginhost.HttpTransport.GetResultPart1, HttpAnswer]

object HttpTransport:
  type GetResultPart1 = GetResultPart1.T
  object GetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetUrl = GetUrl.T
  object GetUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetHeadersPart1 = GetHeadersPart1.T
  object GetHeadersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetHeadersPart2 = GetHeadersPart2.T
  object GetHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
