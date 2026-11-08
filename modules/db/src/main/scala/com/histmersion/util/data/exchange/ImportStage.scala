package com.histmersion
package util.data.exchange

/** The stages of an import, in the order they run. */
enum ImportStage derives CanEqual:
  /** The rows of the lines, before the blobs are read: they are written with the bundle's own clocks. */
  case Rows

  /** What hangs off the blobs, once they are in. */
  case AfterBlobs

  /** The state derived from what was written (search chunks, links): this collection's own consequences, written with
    * its clocks.
    */
  case Derived
