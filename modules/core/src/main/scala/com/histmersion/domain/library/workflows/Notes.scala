package com.histmersion
package domain.library.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.Content
import com.histmersion.util.problem.Problem.Where
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.research.Slug
import com.histmersion.domain.AgentVerify.*
import com.histmersion.domain.EnrichSource.Description

@tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
final case class Notes(notes: Notes.Value) derives JsonCodec, KindlingsSchema, com.histmersion.api.json.Schema

object Notes:
  type Value = Value.T
  object Value extends Newtype[String, Pure]

  extension (n: Notes)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def checkNotes(isCitekey: Citekey => Boolean, isSlug: Slug => Boolean): Either[List[Problem], Description] =
      val text = Option(n.notes.value).map(_.trim).getOrElse("")
      if text.isEmpty then Left(List(Content.Answer(Content.Requirement.NotesGiven)))
      else
        linkProblems(LinkProblemsText(text), Problem.Where("notes"), isCitekey, isSlug) match
          case Nil => Right(Description(text))
          case ps  => Left(ps)
