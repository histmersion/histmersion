package com.histmersion.api.catalogue

object Event:
  type Id = Id.T
  object Id extends io.github.iltotore.iron.RefinedType[String, io.github.iltotore.iron.constraint.any.Pure]
