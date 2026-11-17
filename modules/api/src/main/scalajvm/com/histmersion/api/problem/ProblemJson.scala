package com.histmersion
package api
package problem

import hearth.kindlings.jsoniterjson.Json

import util.json.RawJson.*
import util.problem.Problem
import json.NewtypeCodecs.given
import Answer.{entry, problem}

/** A problem in a wire record or a stored column on the JVM: the typed entry both ways, so the server, the CLI and the
  * MCP tools keep its case. A bare string is how a master version kept a problem's text: it reads through
  * [[StoredShapes]] (the shapes a master version wrote; any other text as [[Problem.Stored.Unrecognised]], whole), and
  * is written back as that problem's entry.
  */
private[api] object ProblemJson:

  extension (problem: Problem)
    /** The problem as a record carries it. */
    def json: Json = problem.entry.asJson

  /** The problem a record carries: its case, read from the entry's group, case and fields. */
  def read(json: Json): Either[String, Problem] =
    json.asString.fold(json.as[Answer.Entry].flatMap(_.problem))(text => Right(StoredShapes.read(text)))
