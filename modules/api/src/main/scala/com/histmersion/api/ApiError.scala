package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `code` lets one error output carry several HTTP statuses (see `Write.Code*`); absent = 400. */
final case class ApiError(message: String, code: Option[String] = None) derives KindlingsCodecAsObject, KindlingsSchema
object ApiError:
  given Schema[ApiError] = summon[KindlingsSchema[ApiError]].schema

  /** The `oneOfVariantValueMatcher` test for one [[ApiError.code]]. Tapir hands the matcher an `Any`, and under
    * `-source:future` a constructor pattern on `Any` is a warning (it is not `Matchable`) — so the value is checked
    * with a type test instead of being destructured.
    */
  def hasCode(code: String): PartialFunction[Any, Boolean] =
    case e if e.isInstanceOf[ApiError] && e.asInstanceOf[ApiError].code.contains(code) => true
