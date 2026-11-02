package com.histmersion.domain.library.ingest

import com.histmersion.util.storage.StoreKey

/** A copy an acquisition fetched and spooled (`<citekey>/paper.<ext>` in the acquisition spool), and where it is. */
final case class FetchedCopy(key: StoreKey, where: String)
