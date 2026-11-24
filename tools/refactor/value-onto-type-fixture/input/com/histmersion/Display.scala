package com.histmersion

trait Display[A]:
  def text(a: A): String

object Display:
  def apply[A](form: A => String): Display[A] = form(_)
