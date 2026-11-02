package com.histmersion.api

import hearth.kindlings.circederivation.Configuration

/** Honour Scala default values when decoding: without this Kindlings requires every non-Option field in the JSON, so
  * `{"tags":{"add":["sea-peoples"]}}` would be refused for a missing `remove`. The domain and config packages import it
  * (`import com.histmersion.api.given`) for the codecs they derive.
  */
given Configuration = Configuration.default.withDefaults

object Write:

  private[api] def blankToNone(v: Option[String]): Option[String] = v.map(_.trim).filter(_.nonEmpty)

  val CodeNotFound = "not_found"
  val CodePrecondition = "precondition_failed"
  val CodeInvalid = "invalid"

  def notFound(citekey: String): ApiError = ApiError(s"No source '$citekey'", Some(CodeNotFound))

  def applyList(current: List[String], p: ListPatch): List[String] =
    val base = p.replace.getOrElse(current)
    val removed = p.remove.toSet
    (base.filterNot(removed) ++ p.add.filterNot(removed)).distinct
