package com.histmersion.config

/** One node of the hot component graph (docs/research-plugins.md §3.6): its name, the nodes its value is built from,
  * and the config keys it is built from — each a HOCON path prefix (`histmersion.db` covers `histmersion.db.url` and
  * `histmersion.db.pool.max`).
  */
final case class HotNode(name: String, deps: List[String], keys: List[String])
