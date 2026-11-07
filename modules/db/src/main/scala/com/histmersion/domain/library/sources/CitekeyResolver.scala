package com.histmersion.domain.library.sources

import com.augustnagro.magnum.DbCon

/** How another domain reads the sources' citekeys inside its own transaction: the citekey each id names now (an old
  * citekey answers with the one it was renamed to).
  */
trait CitekeyResolver:

  /** The citekey each of `ids` names now; an id that names nothing is absent. */
  def canonical(ids: List[com.histmersion.domain.library.Citekey])(using DbCon): Map[com.histmersion.domain.library.Citekey, com.histmersion.domain.library.Citekey]

object CitekeyResolver:

  /** The stored sources and their old citekeys. */

  val stored: CitekeyResolver = new CitekeyResolver:
    def canonical(ids: List[com.histmersion.domain.library.Citekey])(using DbCon): Map[com.histmersion.domain.library.Citekey, com.histmersion.domain.library.Citekey] = SourceRepo.canonicalAll(ids)
