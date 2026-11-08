package com.histmersion
package domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** A record's key as the person and the web page name it: a citekey, a slug, an event's or a place's id, a picture's
  * uid. It may change (a rename); the record's uid does not.
  */
type ItemKey = ItemKey.T

object ItemKey extends RefinedType[String, Pure]
