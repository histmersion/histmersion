package com.histmersion.util.data.diagnostics

import com.histmersion.util.storage.Store

/** One entry of About › Storage before it is measured: the stores it sums (several only for the rest of the data
  * directory), and the one whose place it shows — none when the places go in `detail` instead, so nothing nests under
  * it.
  */
final case class StorageSpot(
    key: String,
    label: String,
    group: String,
    stores: List[Store],
    shown: Option[Store],
    detail: Option[String] = None
):
  /** Where the entry is, as About shows it. */
  def path: Option[String] = shown.map(_.where())
