package com.histmersion.api

final case class Validity(from: Option[com.histmersion.api.catalogue.Year], to: Option[com.histmersion.api.catalogue.Year], startUnknown: Validity.StartUnknown)

object Validity:
  type StartUnknown = StartUnknown.T
  object StartUnknown extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
