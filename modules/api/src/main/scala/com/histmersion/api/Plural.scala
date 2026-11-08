package com.histmersion.api

object Plural:
  object Noun:
    type Form = Form.T
    object Form extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Plural = Plural.T
    object Plural extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Singular = Singular.T
    object Singular extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Phrase = Phrase.T
  object Phrase extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type CountN = _root_.com.histmersion.api.Plural.Count
  val CountN: _root_.com.histmersion.api.Plural.Count.type = _root_.com.histmersion.api.Plural.Count
  export com.histmersion.api.Plural.Noun.{Singular as CountSingular, Plural as CountPlural}

  def noun(n: com.histmersion.api.Plural.Count, singular: com.histmersion.api.Plural.Noun.Singular, plural: com.histmersion.api.Plural.Noun.Plural = _root_.com.histmersion.api.Plural.Noun.Plural("")): com.histmersion.api.Plural.Noun.Form =
    if n.value == 1 then _root_.com.histmersion.api.Plural.Noun.Form((singular).value) else if (plural).value.nonEmpty then _root_.com.histmersion.api.Plural.Noun.Form((plural).value) else _root_.com.histmersion.api.Plural.Noun.Form((singular).value + "s")

  def count(n: com.histmersion.api.Plural.CountN, singular: com.histmersion.api.Plural.CountSingular, plural: com.histmersion.api.Plural.CountPlural = _root_.com.histmersion.api.Plural.CountPlural("")): com.histmersion.api.Plural.Phrase = _root_.com.histmersion.api.Plural.Phrase(s"$n ${noun(n, singular, plural)}")
