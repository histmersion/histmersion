package com.histmersion.config

/** One node of the hot component graph (docs/research-plugins.md §3.6): its name, the nodes its value is built from,
  * and the config keys it is built from — each a HOCON path prefix (`histmersion.db` covers `histmersion.db.url` and
  * `histmersion.db.pool.max`).
  */
final case class HotNode(name: HotNode.Name, deps: List[HotNode.Deps], keys: List[HotNode.Keys])

object HotNode:
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Deps = Deps.T
  object Deps extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Keys = Keys.T
  object Keys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
