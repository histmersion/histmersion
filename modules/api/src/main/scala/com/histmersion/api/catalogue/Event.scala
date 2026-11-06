package com.histmersion.api.catalogue

object Event:
  type Uid = Uid.T
  object Uid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Id = Id.T
  object Id extends io.github.iltotore.iron.RefinedType[String, io.github.iltotore.iron.constraint.any.Pure]
