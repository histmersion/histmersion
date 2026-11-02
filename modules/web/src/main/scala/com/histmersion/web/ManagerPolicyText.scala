package com.histmersion.web

import com.histmersion.api.automation.Managers.*
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object ManagerPolicyText:
  private val Date = "^\\d{4}-\\d{2}-\\d{2}(T.*)?$".r

  /** A date (or an instant) rather than a DOI or a citekey. */
  def isDate(s: String): Boolean = Date.matches(s.trim)
