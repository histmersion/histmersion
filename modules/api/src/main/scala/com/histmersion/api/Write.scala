package com.histmersion
package api

import com.histmersion.util.problem.Problem
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Citekey
import hearth.kindlings.jsoniterderivation.JsoniterConfig

import util.json.JsonCodec
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** How a record's schema is derived: a field with a default is optional, as its codec reads it (`JsonCodec`) — a patch
  * like `{"tags":{"add":["sea-peoples"]}}` must not be described as needing `remove`. The domain and config packages
  * import it (`import com.histmersion.api.given`) for the schemas they derive.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@movesTo(FeaturePlace.UtilCommon)
given JsoniterConfig = JsonCodec.schemas

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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def invalid(message: Problem, code: Api.Error.Code = Api.Error.Code(CodeInvalid)): Api.Error =
    Api.Error(message, Some(code))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def notFound(citekey: Citekey): Api.Error =
    Api.Error(
      Problem.NotFound.Entity(Problem.TypeName.of(Citekey), Problem.Raw(s"$citekey")),
      Some(Api.Error.Code(CodeNotFound))
    )

  def applyList(current: List[ListItem], p: ListPatch): List[ListItem] =
    val base = p.replace.getOrElse(current)
    val removed = p.remove.toSet
    (base.filterNot(removed) ++ p.add.filterNot(removed)).distinct
