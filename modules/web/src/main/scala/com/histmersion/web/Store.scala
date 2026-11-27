package com.histmersion
package web

import com.raquo.laminar.api.L.*
import com.histmersion.api.VocabularyCodecs.given
import org.scalajs.dom
import org.scalajs.dom.window

import scala.scalajs.js
import scala.util.Try
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** Key-value persistence for [[Persist.Local]] fields: the browser's `localStorage` in the app, an in-memory map in
  * tests (Node has none). Every access is guarded — private browsing and a full quota must degrade to "not remembered",
  * never to a broken page.
  */
@atBoundary(BoundaryKind.Dom, "reads and writes the browser's localStorage")
trait Store:
  def get(key: String): Option[String]
  def put(key: String, value: String): Unit
  def remove(key: String): Unit

object Store:

  object Browser extends Store:

    def get(key: String): Option[String] =
      Try(Option(window.localStorage.getItem(key))).toOption.flatten
    def put(key: String, value: String): Unit = { val _ = Try(window.localStorage.setItem(key, value)) }
    def remove(key: String): Unit = { val _ = Try(window.localStorage.removeItem(key)) }

  final class Memory extends Store:
    private val m = scala.collection.mutable.Map.empty[String, String]
    def get(key: String): Option[String] = m.get(key)
    def put(key: String, value: String): Unit = m(key) = value
    def remove(key: String): Unit = { val _ = m.remove(key) }
