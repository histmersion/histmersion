package fixture

import com.histmersion.Newtype
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

type Title = Title.T
object Title extends RefinedType[String, Pure]:
  extension (title: Title)
    def isBlank: Boolean = title.value.isBlank
    def startsWith(prefix: String): Boolean = title.value.startsWith(prefix)
    def shout: Title = Title(title.value.toUpperCase)

type Name = Name.T
object Name extends RefinedType[String, Pure]

type Year = Year.T
object Year extends Newtype[Int, Pure]
