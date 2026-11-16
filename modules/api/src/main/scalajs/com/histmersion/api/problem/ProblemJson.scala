package com.histmersion
package api
package problem

import hearth.kindlings.jsoniterjson.Json

import util.json.RawJson.*
import util.problem.Problem
import json.NewtypeCodecs.given
import Answer.relayedEntry
import Column.stored

/** A problem in a wire record in the browser: only its sentence. The page shows a problem and never tells cases apart,
  * so it reads an entry's `text` as a [[Problem.Relayed]] and links none of the groups' codecs. A bare string is kept
  * as it was ([[Column.read]] reads no frozen sentence here: [[SentenceReaders]]).
  *
  * The page sends no problem to the server (no request body holds one: RequestBodiesSuite); were it to, it writes the
  * relayed entry (`{"group": "Relayed", "case": "Relayed", "data": {}, "text": …}`), which the server reads as the same
  * [[Problem.Relayed]].
  */
private[api] object ProblemJson:

  extension (problem: Problem)
    /** The problem as a record carries it: a kept text bare, any other problem by its sentence alone. */
    def json: Json = problem match
      case unrecognised: Problem.Stored.Unrecognised => Json.fromString(unrecognised.stored)
      case other                                     => other.relayedEntry.asJson

  /** The problem a record carries, as its sentence. */
  def read(json: Json): Either[String, Problem] =
    json.asString.fold(json.as[Answer.Entry].map(entry => Problem.Relayed(entry.text)))(text =>
      Right(Column.read(text))
    )
