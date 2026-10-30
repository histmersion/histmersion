package com.histmersion.server.hot

import hearth.kindlings.optics.syntax.*
import ox.{supervised, Ox}

import scala.concurrent.duration.*

/** A stable handle on a component a config reload may rebuild (docs/research-plugins.md §3.6). Its holder never
  * reloads: [[use]] gives the value of the generation the calling thread is pinned to — or pins the current generation
  * for the duration of the call — so a value is never closed under the code using it.
  */
trait Hot[+A]:
  def name: String

  /** The value for this call, pinned while `f` runs. */
  def use[B](f: A => B): B

  /** The value the calling thread sees now (its pin's, else the current one), NOT pinned: for reading something that is
    * not closed with its generation (a report, a registry snapshot).
    */
  def peek: A

  /** The latest published value, whatever the calling thread is pinned to; not pinned. */
  def latest: A
