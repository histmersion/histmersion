package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.api.VocabularyCodecs.given
import org.scalajs.dom
import org.scalajs.dom.window

import scala.scalajs.js
import scala.util.Try

/** Key-value persistence for [[Persist.Local]] fields: the browser's `localStorage` in the app, an in-memory map in
  * tests (Node has none). Every access is guarded — private browsing and a full quota must degrade to "not remembered",
  * never to a broken page.
  */
trait Store:
  def get(key: com.histmersion.web.Store.GetKey): Option[com.histmersion.web.Store.GetResult]
  def put(key: com.histmersion.web.Store.PutKey, value: com.histmersion.web.Store.PutValue): Unit
  def remove(key: com.histmersion.web.Store.RemoveKey): Unit

object Store:
  type GetResult = GetResult.T
  object GetResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetKey = GetKey.T
  object GetKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutKey = PutKey.T
  object PutKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutValue = PutValue.T
  object PutValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveKey = RemoveKey.T
  object RemoveKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Browser extends Store:
    type GetResult = GetResult.T
    object GetResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GetKey = GetKey.T
    object GetKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PutKey = PutKey.T
    object PutKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PutValue = PutValue.T
    object PutValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RemoveKey = RemoveKey.T
    object RemoveKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def get(key: com.histmersion.web.Store.Browser.GetKey): Option[com.histmersion.web.Store.Browser.GetResult] =
      Try(Option(window.localStorage.getItem(key))).toOption.flatten
    def put(key: com.histmersion.web.Store.Browser.PutKey, value: com.histmersion.web.Store.Browser.PutValue): Unit = { val _ = Try(window.localStorage.setItem(key, value)) }
    def remove(key: com.histmersion.web.Store.Browser.RemoveKey): Unit = { val _ = Try(window.localStorage.removeItem(key)) }

  final class Memory extends Store:
    private val m = scala.collection.mutable.Map.empty[String, String]
    def get(key: com.histmersion.web.Store.Memory.GetKey): Option[com.histmersion.web.Store.Memory.GetResult] = m.get(key)
    def put(key: com.histmersion.web.Store.Memory.PutKey, value: com.histmersion.web.Store.Memory.PutValue): Unit = m(key) = value
    def remove(key: com.histmersion.web.Store.Memory.RemoveKey): Unit = { val _ = m.remove(key) }

  object Memory:
    type GetResult = GetResult.T
    object GetResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GetKey = GetKey.T
    object GetKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PutKey = PutKey.T
    object PutKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PutValue = PutValue.T
    object PutValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RemoveKey = RemoveKey.T
    object RemoveKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
