package com.histmersion.api

object Plural:

  type NounResult = NounResult.T
  object NounResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NounN = NounN.T
  object NounN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NounSingular = NounSingular.T
  object NounSingular extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NounPlural = NounPlural.T
  object NounPlural extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountResult = CountResult.T
  object CountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountN = CountN.T
  object CountN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountSingular = CountSingular.T
  object CountSingular extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountPlural = CountPlural.T
  object CountPlural extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def noun(n: com.histmersion.api.Plural.NounN, singular: com.histmersion.api.Plural.NounSingular, plural: com.histmersion.api.Plural.NounPlural = _root_.com.histmersion.api.Plural.NounPlural("")): com.histmersion.api.Plural.NounResult =
    if n == 1 then singular else if plural.nonEmpty then plural else singular + "s"

  def count(n: com.histmersion.api.Plural.CountN, singular: com.histmersion.api.Plural.CountSingular, plural: com.histmersion.api.Plural.CountPlural = _root_.com.histmersion.api.Plural.CountPlural("")): com.histmersion.api.Plural.CountResult = s"$n ${noun(n, singular, plural)}"
