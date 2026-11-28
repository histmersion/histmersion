package com.histmersion

trait Newtype[A]:
  opaque type T = A
  def apply(a: A): T = a

trait Display[A]:
  def show(a: A): String

object Display:
  def apply[A](f: A => String): Display[A] = a => f(a)

package util:
  object Text:
    val empty = ""
