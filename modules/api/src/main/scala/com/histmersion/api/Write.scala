package com.histmersion
package api

import com.histmersion.util.problem.{Legacy, Problem}
import hearth.kindlings.circederivation.Configuration
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Citekey

/** Honour Scala default values when decoding: without this Kindlings requires every non-Option field in the JSON, so
  * `{"tags":{"add":["sea-peoples"]}}` would be refused for a missing `remove`. The domain and config packages import it
  * (`import com.histmersion.api.given`) for the codecs they derive.
  */
given Configuration = Configuration.default.withDefaults

object Write:
  type ListItem = ListItem.T
  object ListItem extends Newtype[String, Pure]

  private[api] def blankToNone(v: Option[String]): Option[String] =
    (v.map((_).trim).filter(_.nonEmpty)).map(refactorValue => (refactorValue))

  val CodeNotFound = "not_found"
  val CodePrecondition = "precondition_failed"
  val CodeInvalid = "invalid"

  /** A request the server refuses for a value it does not accept, with the reason (and the code when the value is one a
    * client places: the citekey).
    */
  def invalid(message: Problem, code: Api.Error.Code = Api.Error.Code(CodeInvalid)): Api.Error =
    Api.Error(message, Some(code))

  def notFound(citekey: Citekey): Api.Error =
    Api.Error(Problem.Unclassified(Legacy.Text(s"No source '$citekey'")), Some(Api.Error.Code(CodeNotFound)))

  def applyList(current: List[ListItem], p: ListPatch): List[ListItem] =
    val base = p.replace.getOrElse(current)
    val removed = p.remove.toSet
    (base.filterNot(removed) ++ p.add.filterNot(removed)).distinct
