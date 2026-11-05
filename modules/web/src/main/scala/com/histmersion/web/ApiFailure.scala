package com.histmersion.web

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import scala.scalajs.js

import org.scalajs.dom

import sttp.client4.*
import com.histmersion.api.Api

/** A request the server answered with an error status: the status and the server's own message. `unauthorized` is a
  * write refused by the API-key gate; `conflict` a stale `ifUpdatedAt`.
  */
final case class ApiFailure(status: ApiFailure.Status, error: Api.Error) extends Exception(error.message):
  def unauthorized: com.histmersion.web.ApiFailure.UnauthorizedResult = status == 401 || status == 403
  def conflict: com.histmersion.web.ApiFailure.ConflictResult = status == 412
  def duplicate: com.histmersion.web.ApiFailure.DuplicateResult = status == 409

object ApiFailure:
  /** Pure: what a failed response means, from its status and whatever body could be decoded. */
  type UnauthorizedResult = UnauthorizedResult.T
  object UnauthorizedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConflictResult = ConflictResult.T
  object ConflictResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DuplicateResult = DuplicateResult.T
  object DuplicateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfStatus = OfStatus.T
  object OfStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Status = Status.T
  object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(status: com.histmersion.web.ApiFailure.OfStatus, decoded: Option[Api.Error]): ApiFailure =
    ApiFailure(
      status,
      decoded.getOrElse(Api.Error(status match
        case 401 | 403 => "the server refused the change: an API key is required"
        case 0         => "the server could not be reached"
        case s         => s"unexpected response from the server (HTTP $s)"))
    )
