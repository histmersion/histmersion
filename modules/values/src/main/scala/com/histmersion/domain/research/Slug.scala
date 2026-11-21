package com.histmersion
package domain.research
import com.histmersion.util.problem.Problem
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

type Slug = Slug.T

object Slug extends Newtype[String, Match["[a-z0-9][a-z0-9_.-]{0,120}"]]:

  def parse(raw: String): Either[Problem, Slug] =
    either((raw).trim.toLowerCase).left.map(_ => Problem.Invalid.malformed(refused(raw)))
