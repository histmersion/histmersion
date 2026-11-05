package com.histmersion.domain.library.ingest

/** A copy an acquisition fetched and spooled (`<citekey>/paper.<ext>` in the acquisition spool), and where it is. */
import com.histmersion.util.storage.Store
final case class FetchedCopy(key: Store.Key, where: FetchedCopy.Where)

object FetchedCopy:
  type Where = Where.T
  object Where extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
