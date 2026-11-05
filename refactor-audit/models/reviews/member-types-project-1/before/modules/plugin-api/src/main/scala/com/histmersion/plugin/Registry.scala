package com.histmersion.plugin

import scala.compiletime.deferred

/** One extension point. `add` refuses an id already taken in this registry. */
trait Registry[A]:
  def add(id: String, a: A): Either[String, Unit]

object Registry:
  /** A registry that takes nothing, saying why. */
  def refusing[A](why: String): Registry[A] = new Registry[A]:
    def add(id: String, a: A) = Left(s"'$id': $why")
