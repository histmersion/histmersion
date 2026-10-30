package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class ApiError(message: String, code: Option[String] = None) derives KindlingsCodecAsObject, KindlingsSchema
object ApiError:
  given Schema[ApiError] = summon[KindlingsSchema[ApiError]].schema

  /** Tapir hands the matcher an `Any`, and under `-source:future` a constructor pattern on `Any` warns (it is not
    * `Matchable`), so the value is checked with a type test.
    */
  def hasCode(code: String): PartialFunction[Any, Boolean] =
    case e if e.isInstanceOf[ApiError] && e.asInstanceOf[ApiError].code.contains(code) => true
