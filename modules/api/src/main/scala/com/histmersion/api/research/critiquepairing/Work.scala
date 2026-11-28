package com.histmersion
package api.research.critiquepairing

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure

final case class Work(citekey: Citekey, names: List[Work.Names], year: Option[Year])

object Work:
  type Names = Names.T
  object Names extends Newtype[String, Pure]
