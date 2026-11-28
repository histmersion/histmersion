package com.histmersion
package domain.library.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.library.workflows.StatedYear
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problems
import com.histmersion.util.problem.Problem.Field
import com.histmersion.util.problem.Problem.Invalid
import com.histmersion.util.problem.Problem.Raw
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.AgentVerify.*
import com.histmersion.domain.EnrichSource.MaxAbsYear

@tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
final case class StatedPeriod(start: StatedYear, end: StatedYear, name: Option[StatedPeriod.Name] = None)
    derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object StatedPeriod:
  type Name = Name.T
  object Name extends Newtype[String, Pure]

  extension (p: StatedPeriod)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def convert(maxAbsYear: MaxAbsYear, within: Option[Period]): Either[Problems, Period] =
      def unit(stated: YearUnit.Wire): Either[Problem, YearUnit] =
        YearUnit
          .parse(stated)
          .toRight(
            Invalid.Unknown
              .here(Problem.TypeName.named[YearUnit], Raw.of(stated), YearUnit.values.toVector.map(u => Raw.of(u.wire)))
              .within(Field("period"))
          )
      for
        su <- unit(p.start.unit)
        eu <- unit(p.end.unit)
        s <- toSignedYear(p.start.value, su)
        e <- toSignedYear(p.end.value, eu)
        _ <- Problems
          .from(periodProblems(PeriodStart(s.value), PeriodEnd(e.value), maxAbsYear, within).toVector)
          .toLeft(())
      yield Period(Year(s.value), Year(e.value))
