package other

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Display

type Note = Note.T
object Note extends RefinedType[String, Pure]:
  given Display[T] = Display(_.value)
