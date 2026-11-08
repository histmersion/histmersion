package com.histmersion
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

// S0 UNCHANGED: already `package com.histmersion`, no qualified names
type Citekey = Citekey.T

object Citekey extends RefinedType[String, Pure]

type Year = Year.T

object Year extends RefinedType[Int, Pure]
