package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.api.VocabularyCodecs.given
import org.scalajs.dom
import org.scalajs.dom.window

import scala.scalajs.js

/** One piece of UI state, typed. The web UI's SINGLE source of truth: every component reads a field's `signal`/`now()`
  * and commits through [[UiState.set]] — nothing keeps its own copy.
  */
final class Field[A](
    val key: String,
    val hashKey: String,
    initial: A,
    val persist: Persist,
    val role: Role,
    val label: Option[String] = None,
    val chip: Boolean = true
)(using val codec: FieldCodec[A]):
  private val v = Var(initial)
  // `var`: `from`/`to` default to the CONFIGURED span, known only once `/api/config` has answered
  private var dflt: A = initial

  def default: A = dflt
  def now(): A = v.now()
  def signal: StrictSignal[A] = v.signal

  /** Write WITHOUT committing (no hash, no reload) — a live drag; commit with [[UiState.set]]. */
  def set(a: A): Unit = v.set(a)
  def update(f: A => A): Unit = v.update(f)
  def clear(): Unit = v.set(dflt)
  def :=(a: A): UiState.Assign = UiState.Assign(() => v.set(a))

  private[web] def withDefault(a: A): Unit = { dflt = a; v.set(a) }
  private[web] def encoded: List[String] = codec.encode(now())
  private[web] def load(vs: List[String]): Unit = v.set(codec.decode(vs).getOrElse(dflt))

  /** Add hash values: merged into a list field, replacing a scalar one. */
  private[web] def add(vs: List[String]): Unit =
    if codec.multi then load((encoded ++ vs).distinct) else load(vs)

  /** Remove ONE chip value (a list keeps the others; a scalar goes back to its default). */
  private[web] def remove(value: String): Unit = load(encoded.filterNot(_ == value))
