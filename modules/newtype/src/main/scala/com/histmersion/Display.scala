package com.histmersion

import scala.Conversion.into

trait Display[A]:
  def text(shown: A): String

object Display:
  def apply[A](form: A => String): Display[A] = form(_)

  final class Shown private[Display] (val text: String)

  object Shown:
    given Conversion[String, Shown] = Shown(_)
    given Conversion[Int, Shown] = number => Shown(s"$number")
    given Conversion[Long, Shown] = number => Shown(s"$number")
    given Conversion[Double, Shown] = number => Shown(s"$number")
    given Conversion[Boolean, Shown] = flag => Shown(s"$flag")
    given Conversion[Char, Shown] = character => Shown(s"$character")
    given [A] => (display: Display[A]) => Conversion[A, Shown] = shown => Shown(display.text(shown))

extension (context: StringContext) def shown(parts: into[Display.Shown]*): String = context.s(parts.map(_.text)*)
