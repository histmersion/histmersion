package com.histmersion
package domain.library
import com.histmersion.util.problem.Problem
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

type CitekeyRule = Match["(?!.*\\.\\.)[a-z][a-z0-9_.-]{1,120}"]

type Citekey = Citekey.T

object Citekey extends Newtype[String, CitekeyRule]:

  type ValidRaw = String

  def parse(raw: String): Either[Problem, Citekey] =
    val s = Option(raw).map((_).trim).getOrElse("")
    if s.isEmpty then Left(Problem.Invalid.Missing.here(Problem.Field("citekey")))
    else checked(s).left.map(Problem.Invalid.malformed)

  def valid(raw: ValidRaw): Boolean = (parse(raw).isRight)
