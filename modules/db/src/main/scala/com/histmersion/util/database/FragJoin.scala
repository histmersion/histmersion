package com.histmersion
package util.database

import com.augustnagro.magnum.{sql, Frag}

extension (parts: Seq[Frag])
  /** The fragments one after another with `sep` between two (`sql"$a$sep$b"`, folded), each keeping its own parameters
    * in place; `sql""` for none. Magnum 2 composes fragments only by nesting them, so this is the one place a list of
    * them becomes one.
    */
  def joined(sep: Frag): Frag =
    parts.reduceLeftOption((a, b) => sql"$a$sep$b").getOrElse(sql"")
