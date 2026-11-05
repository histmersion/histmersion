//> using dep io.github.iltotore::iron:3.4.1
package conceptfixture

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

type Label = Label.T
object Label extends RefinedType[String, Pure]
type Title = Title.T
object Title extends RefinedType[String, Pure]
type Doi = Doi.T
object Doi extends RefinedType[String, Pure]
type AuthorName = AuthorName.T
object AuthorName extends RefinedType[String, Pure]
type ByteCount = ByteCount.T
object ByteCount extends RefinedType[Long, Pure]
type Latitude = Latitude.T
object Latitude extends RefinedType[Double, Pure]
type Year = Year.T
object Year extends RefinedType[Int, Pure]
