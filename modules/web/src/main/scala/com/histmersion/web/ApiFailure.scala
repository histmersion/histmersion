package com.histmersion
package web
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import scala.scalajs.js

import org.scalajs.dom

import sttp.client4.*
import com.histmersion.api.Api
import io.github.iltotore.iron.constraint.any.Pure

/** A request the server answered with an error status: the status and the server's own message. `unauthorized` is a
  * request the server let in only with its API key, sent without it or with a wrong one (401; a 403 is a refusal the
  * key does not lift); `conflict` a stale `ifUpdatedAt`.
  */
final case class ApiFailure(status: ApiFailure.Status, error: Api.Error)
    extends Exception(error.problems.sentences.mkString("; ")):
  def unauthorized: Boolean = status.value == 401
  def conflict: Boolean = status.value == 412
  def duplicate: Boolean = status.value == 409

object ApiFailure:
  type Status = Status.T
  object Status extends Newtype[Int, Pure]

  /** Pure: what a failed response means, from its status and whatever body could be decoded. */
  def of(status: Status, decoded: Option[Api.Error]): ApiFailure =
    ApiFailure(
      status,
      decoded.getOrElse(Api.Error(Problem.Unclassified(Legacy.Text(status.value match
        case 401 => "the server needs its API key for this request"
        case 403 => "the server refused this request"
        case 0   => "the server could not be reached"
        case s   => s"unexpected response from the server (HTTP $s)"))))
    )
