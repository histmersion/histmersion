package com.histmersion
package api

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

@movesTo(FeaturePlace.UtilCommon)
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
  object Phrase extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type CountN = Count
  val CountN: Count.type = Count
  export Noun.{Plural as CountPlural, Singular as CountSingular}

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def noun(n: Count, singular: Noun.Singular, plural: Noun.Plural = Noun.Plural("")): Noun.Form =
    if n.value == 1 then Noun.Form((singular).value)
    else if (plural).value.nonEmpty then Noun.Form((plural).value)
    else Noun.Form((singular).value + "s")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def count(n: CountN, singular: Plural.CountSingular, plural: Plural.CountPlural = Plural.CountPlural("")): Phrase =
    Phrase(s"$n ${noun(n, singular, plural)}")
