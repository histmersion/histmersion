package fixture

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

type Title = Title.T
object Title extends RefinedType[String, Pure]:
  extension (title: Title) def isBlank: Boolean = title.value.isBlank
