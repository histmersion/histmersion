package com.histmersion
package plugin

/** Which end of a period the dataset does not know: a state "dissolved 1769" whose start is unknown has the period
  * `1769..1769` and the open side [[GazetteerOpenSide.Start]]. Time filters then see the known end only.
  */
enum GazetteerOpenSide derives CanEqual:
  /** The start is unknown; the period's start is its end. */
  case Start

  /** The end is unknown; the period's end is its start. */
  case End
