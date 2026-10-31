package com.histmersion.domain.library.collections

/** A source's original as the server stores it: its bytes and the `Content-Disposition` it is served with (which
  * names its format).
  */
final case class StoredOriginal(bytes: Array[Byte], disposition: String)
