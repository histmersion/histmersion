package com.histmersion
package domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type Citekey = Citekey.T
object Citekey extends Newtype[String, Not[Blank]]

/** A hand ordering that is not the lifted one: sorting by value is not `.sorted` here. */
type Desc = Desc.T
object Desc extends Newtype[String, Pure]:
  given Ordering[T] = Ordering.by[T, String](_.value).reverse

final case class Entry(key: Citekey, desc: Desc)
