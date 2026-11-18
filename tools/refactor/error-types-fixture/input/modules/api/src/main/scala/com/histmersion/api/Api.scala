package com.histmersion
package api

import util.problem.Problems

/** The shape of `Api.Error`: the problems and a code, built with or without one. */
object Api:
  final case class Error(problems: Problems, code: Option[Error.Code])
  object Error:
    def apply(problems: Problems): Error = Error(problems, None)
    type Code = String
    def Code(text: String): Code = text
