package com.histmersion
package api.jobs

import com.histmersion.api.RegisteredJobKinds
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
object JobActions:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  val all: List[Wire] = List(Wire("pause"), Wire("resume"), Wire("cancel"))

  /** The kinds whose jobs a person may pause and cancel. */
  val Stoppable: Set[JobKinds.Wire] = RegisteredJobKinds.all.stoppableKinds

  val CodeConflict = "conflict"
