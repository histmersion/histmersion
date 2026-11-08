package com.histmersion
package config

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** One node of the hot component graph (docs/research-plugins.md §3.6): its name, the nodes its value is built from,
  * and the config keys it is built from — each a HOCON path prefix (`histmersion.db` covers `histmersion.db.url` and
  * `histmersion.db.pool.max`).
  */
final case class HotNode(name: HotNode.Name, deps: List[HotNode.Deps], keys: List[HotNode.Keys])

object HotNode:
  type Name = Name.T
  object Name extends RefinedType[String, Pure]
  type Deps = Deps.T
  object Deps extends RefinedType[String, Pure]
  type Keys = Keys.T
  object Keys extends RefinedType[String, Pure]
