package other

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

type Note = Note.T
object Note extends RefinedType[String, Pure]
