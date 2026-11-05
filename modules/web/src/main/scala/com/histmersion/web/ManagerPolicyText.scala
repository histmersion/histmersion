package com.histmersion.web

import com.histmersion.api.automation.Managers.*
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object ManagerPolicyText:
  type IsDateResult = IsDateResult.T
  object IsDateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsDateS = IsDateS.T
  object IsDateS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Date = "^\\d{4}-\\d{2}-\\d{2}(T.*)?$".r

  /** A date (or an instant) rather than a DOI or a citekey. */
  def isDate(s: com.histmersion.web.ManagerPolicyText.IsDateS): com.histmersion.web.ManagerPolicyText.IsDateResult = Date.matches(s.trim)
