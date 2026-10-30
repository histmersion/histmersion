package com.histmersion.plugin

/** One HTTP answer. */
final case class AiHttpResponse(status: Int, body: String, headers: Map[String, String] = Map.empty)
