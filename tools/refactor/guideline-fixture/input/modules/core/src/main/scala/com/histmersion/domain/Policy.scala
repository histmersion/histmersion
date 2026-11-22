package com.histmersion
package domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.constraint.string.Match

type Citekey = Citekey.T
object Citekey extends RefinedType[String, Pure]:
  given Ordering[Citekey] = Ordering.by(_.value)
  extension (c: Citekey) def normalised: String = c.value.trim
  def parse(s: String): Option[Citekey] = Citekey.option(s) // assert: GuidelineInventory.silentPure

type Slug = Slug.T
object Slug extends RefinedType[String, Match["[a-z-]+"]]

type Title = Title.T
object Title extends RefinedSubtype[String, Pure]

enum Status(val wire: Status.Wire):
  case Active extends Status(Status.Wire("active"))
  case Retired extends Status(Status.Wire("retired"))
object Status:
  type Wire = Wire.T
  object Wire extends RefinedType[String, Pure]
  def find(s: String): Option[Status] = Status.values.find(_.wire.value.equalsIgnoreCase(s)) // assert: GuidelineInventory
