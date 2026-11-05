package com.histmersion.plugin

import scala.compiletime.deferred

/** One extension point. `add` refuses an id already taken in this registry. */
trait Registry[A]:
  def add(id: com.histmersion.plugin.Registry.AddId, a: A): Either[com.histmersion.plugin.Registry.AddResultPart1, Unit]

object Registry:
  /** A registry that takes nothing, saying why. */
  type RefusingAddId = RefusingAddId.T
  object RefusingAddId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type AddResultPart1 = AddResultPart1.T
  object AddResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddId = AddId.T
  object AddId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefusingWhy = RefusingWhy.T
  object RefusingWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def refusing[A](why: com.histmersion.plugin.Registry.RefusingWhy): Registry[A] = new Registry[A]:
    def add(id: com.histmersion.plugin.Registry.RefusingAddId, a: A) = Left(s"'$id': $why")
