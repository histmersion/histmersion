package com.histmersion
package api

import io.github.iltotore.iron.constraint.any.Pure

object Plural:
  object Noun:
    type Form = Form.T
    object Form extends Newtype[String, Pure]
    type Plural = Plural.T
    object Plural extends Newtype[String, Pure]
    type Singular = Singular.T
    object Singular extends Newtype[String, Pure]
  type Count = Count.T
  object Count extends Newtype[Long, Pure]
  type Phrase = Phrase.T
  object Phrase extends Newtype[String, Pure]

  type CountN = Count
  val CountN: Count.type = Count
  export Noun.{Singular as CountSingular, Plural as CountPlural}

  def noun(n: Count, singular: Noun.Singular, plural: Noun.Plural = Noun.Plural("")): Noun.Form =
    if n.value == 1 then Noun.Form((singular).value) else if (plural).value.nonEmpty then Noun.Form((plural).value) else Noun.Form((singular).value + "s")

  def count(n: CountN, singular: Plural.CountSingular, plural: Plural.CountPlural = Plural.CountPlural("")): Phrase = Phrase(s"$n ${noun(n, singular, plural)}")
