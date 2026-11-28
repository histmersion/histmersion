package com.histmersion
package api.automation.agents

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.Field
import com.histmersion.util.problem.Problem.Invalid
import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class ProbeAnswer(citekey: Citekey, title: api.library.Title, year: Option[Year])
    derives JsonCodec,
      KindlingsSchema:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def problems(recordYear: Option[ProbeAnswer.RecordYear]): List[Problem] = List(
    Option.when(Option(citekey).forall(_.value.trim.isEmpty))(
      Invalid.Missing.here(Field("citekey"))
    ),
    Option.when(Option(title).forall(_.value.trim.isEmpty))(
      Problem.Content.Proposal(Problem.Content.Requirement.ExactTitle, Problem.Where("title"))
    ),
    Option.when(year.isEmpty && recordYear.isDefined)(
      Invalid.Missing.here(Field("year"))
    )
  ).flatten

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object ProbeAnswer:
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  given Schema[ProbeAnswer] = api.json.Schema.derived[ProbeAnswer].modify(_.year)(_.format("int32"))
  type RecordYear = RecordYear.T
  object RecordYear extends Newtype[Int, Pure]
