package com.histmersion
package api
package problem

import hearth.kindlings.jsoniterjson.Json

import util.json.RawJson.*
import util.problem.Problem
import json.NewtypeCodecs.given
import Answer.{entry, problem}
import Column.stored

/** A problem in a wire record on the JVM: the typed entry both ways, so the server, the CLI and the MCP tools keep its
  * case. A bare string reads as the case whose frozen column sentence it is ([[Column.read]]), any other text as
  * [[Problem.Stored.Unrecognised]], which is written back as the same bare string.
  */
private[api] object ProblemJson:

  extension (problem: Problem)
    /** The problem as a record carries it. */
    def json: Json = problem match
      case unrecognised: Problem.Stored.Unrecognised => Json.fromString(unrecognised.stored)
      case typed                                     => typed.entry.asJson

  /** The problem a record carries: its case, read from the entry's group, case and fields. */
  def read(json: Json): Either[String, Problem] =
    json.asString.fold(json.as[Answer.Entry].flatMap(_.problem))(text => Right(Column.read(text)))
