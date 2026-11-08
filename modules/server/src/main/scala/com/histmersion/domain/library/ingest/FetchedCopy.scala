package com.histmersion
package domain.library.ingest

/** A copy an acquisition fetched and spooled (`<citekey>/paper.<ext>` in the acquisition spool), and where it is. */
import com.histmersion.util.storage.Store
import io.github.iltotore.iron.constraint.any.Pure
final case class FetchedCopy(key: Store.Key, where: FetchedCopy.Where)

object FetchedCopy:
  type Where = Where.T
  object Where extends Newtype[String, Pure]
