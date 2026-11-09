package com.histmersion

import com.histmersion.util.problem.{Problem, Problems}
import hearth.typename.TypeName

/** `raw` as a value of the newtype, or the [[Problem.Invalid.Malformed]] that names the type, the constraint it fails
  * and the input: `Citekey.parse(raw)`. An extension, not a member of [[Newtype]], because the newtype module is below
  * the one that defines `Problem`; it is top-level in `com.histmersion` so every file of the project sees it.
  */
extension [A, C](newtype: Newtype[A, C])
  def parse(raw: A)(using TypeName[newtype.T]): Either[Problems, newtype.T] =
    newtype.checked(raw).left.map(Problem.Invalid.malformed)
