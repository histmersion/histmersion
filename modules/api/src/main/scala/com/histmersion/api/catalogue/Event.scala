package com.histmersion
package api.catalogue

import io.github.iltotore.iron.constraint.any.Pure

object Event:
  type Uid = Uid.T
  object Uid extends Newtype[String, Pure]
  type Id = Id.T
  object Id extends Newtype[String, io.github.iltotore.iron.constraint.any.Pure]
