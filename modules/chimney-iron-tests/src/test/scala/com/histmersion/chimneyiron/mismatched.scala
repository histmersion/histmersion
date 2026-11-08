package com.histmersion
package chimneyiron.mismatched

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type Citekey = Citekey.T
object Citekey extends Newtype[String, MinLength[3]]

final case class Reference(source: Citekey)
