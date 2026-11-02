package com.histmersion.util.storage

import com.histmersion.ByteCount

/** The disk a store is (or would be) on: its name, its size and the space this process may still use. */
final case class StoreVolume(id: String, total: ByteCount, usable: ByteCount)
