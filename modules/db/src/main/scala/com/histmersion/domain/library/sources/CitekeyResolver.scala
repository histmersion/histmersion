package com.histmersion
package domain.library.sources

import com.augustnagro.magnum.DbCon
import com.histmersion.domain.library.Citekey

/** How another domain reads the sources' citekeys inside its own transaction: the citekey each id names now (an old
  * citekey answers with the one it was renamed to).
  */
trait CitekeyResolver:

  /** The citekey each of `ids` names now; an id that names nothing is absent. */
  def canonical(ids: List[Citekey])(using DbCon): Map[Citekey, Citekey]

object CitekeyResolver:

  /** The stored sources and their old citekeys. */

  val stored: CitekeyResolver = new CitekeyResolver:
    def canonical(ids: List[Citekey])(using DbCon): Map[Citekey, Citekey] = SourceRepo.canonicalAll(ids)
