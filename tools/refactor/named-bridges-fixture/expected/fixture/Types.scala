package com.histmersion
package fixture

import io.github.iltotore.iron.constraint.any.Pure

type Year = Year.T
object Year extends Newtype[Long, Pure]

object Calendar:
  type Astronomical = Astronomical.T
  object Astronomical extends Newtype[Long, Pure]:
    extension (a: Astronomical) def asYear: Year = Year(a.value)
  type Label = Label.T
  object Label extends Newtype[String, Pure]

object Uses:
  def plain(a: Calendar.Astronomical): Year = a.asYear
  def assumed(a: Calendar.Astronomical): Year = Year.assume(a.value)
  def computed(a: Long): Year = Calendar.Astronomical(a + 1).asYear
  def nested(as: List[Calendar.Astronomical]): List[Year] = as.map(_.value).map(Year(_))
  def lambda(as: List[Calendar.Astronomical]): List[Year] = as.map(a => a.asYear)
  def other(l: Calendar.Label): Calendar.Label = Calendar.Label(l.value)
