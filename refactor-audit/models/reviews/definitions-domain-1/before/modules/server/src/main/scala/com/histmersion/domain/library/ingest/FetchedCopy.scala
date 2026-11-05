package com.histmersion.domain.library.ingest

/** A copy an acquisition fetched and spooled (`<citekey>/paper.<ext>` in the acquisition spool), and where it is. */
import com.histmersion.util.storage.Store
final case class FetchedCopy(key: Store.Key, where: String)
