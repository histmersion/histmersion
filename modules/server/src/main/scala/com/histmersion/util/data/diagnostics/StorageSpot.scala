package com.histmersion
package util.data.diagnostics

import com.histmersion.util.storage.{Blob, Store}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** One entry of About › Storage before it is measured: the stores it sums (several only for the rest of the data
  * directory), and the one whose place it shows — none when the places go in `detail` instead, so nothing nests under
  * it.
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class StorageSpot(
    key: ShownStore.Key,
    label: ShownStore.Label,
    group: ShownStore.Group,
    stores: List[Store],
    shown: Option[Store],
    detail: Option[StorageSpot.Detail] = None
):
  /** Where the entry is, as About shows it. */
  def path: Option[Blob.Location] = shown.map(_.where())

object StorageSpot:
  type Detail = Detail.T
  object Detail extends Newtype[String, Pure]
