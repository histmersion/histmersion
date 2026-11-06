package com.histmersion.api

import hearth.kindlings.circederivation.Configuration

/** Honour Scala default values when decoding: without this Kindlings requires every non-Option field in the JSON, so
  * `{"tags":{"add":["sea-peoples"]}}` would be refused for a missing `remove`. The domain and config packages import it
  * (`import com.histmersion.api.given`) for the codecs they derive.
  */
given Configuration = Configuration.default.withDefaults

object Write:
  type ListItem = ListItem.T
  object ListItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type InvalidMessage = _root_.com.histmersion.api.Api.Error.Message
  val InvalidMessage: _root_.com.histmersion.api.Api.Error.Message.type = _root_.com.histmersion.api.Api.Error.Message

  private[api] def blankToNone(v: Option[String]): Option[String] = (v.map((_).value.trim).filter(_.nonEmpty)).map(refactorValue => (refactorValue))

  val CodeNotFound = "not_found"
  val CodePrecondition = "precondition_failed"
  val CodeInvalid = "invalid"

  /** A request the server refuses for a value it does not accept, with the reason. */
  def invalid(message: com.histmersion.api.Write.InvalidMessage): Api.Error = Api.Error(message, Some(_root_.com.histmersion.api.Api.Error.Code(CodeInvalid)))

  def notFound(citekey: com.histmersion.api.library.Citekey): Api.Error = Api.Error(_root_.com.histmersion.api.Api.Error.Message(s"No source '$citekey'"), Some(_root_.com.histmersion.api.Api.Error.Code(CodeNotFound)))

  def applyList(current: List[com.histmersion.api.Write.ListItem], p: ListPatch): List[com.histmersion.api.Write.ListItem] =
    val base = p.replace.getOrElse(current)
    val removed = p.remove.toSet
    (base.filterNot(removed) ++ p.add.filterNot(removed)).distinct
