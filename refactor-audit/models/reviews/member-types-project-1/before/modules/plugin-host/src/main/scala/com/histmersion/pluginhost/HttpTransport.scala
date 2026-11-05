package com.histmersion.pluginhost

import scala.jdk.CollectionConverters.*

/** One GET on the wire: the seam between the host's HTTP policies ([[Polite]], [[PolicyHttp]]) and the network. A test
  * supplies one that answers from fixtures or recordings and never opens a socket. Failures to talk at all are `Left`;
  * any HTTP answer, a 429 included, is a `Right` — the policy decides what it means.
  */
trait HttpTransport:
  def get(url: String, headers: Map[String, String]): Either[String, HttpAnswer]
