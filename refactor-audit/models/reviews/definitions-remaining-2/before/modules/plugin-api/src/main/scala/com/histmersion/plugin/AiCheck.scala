package com.histmersion.plugin

/** One line of `agent doctor`: `level` is `ok`, `warn` or `fail`. */
final case class AiCheck(name: String, level: String, detail: String)
