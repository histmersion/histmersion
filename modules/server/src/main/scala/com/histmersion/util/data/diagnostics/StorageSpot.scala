package com.histmersion.util.data.diagnostics

import com.histmersion.util.storage.Store

/** One entry of About › Storage before it is measured: the stores it sums (several only for the rest of the data
  * directory), and the one whose place it shows — none when the places go in `detail` instead, so nothing nests under
  * it.
  */
final case class StorageSpot(
    key: StorageSpot.Key,
    label: StorageSpot.Label,
    group: StorageSpot.Group,
    stores: List[Store],
    shown: Option[Store],
    detail: Option[StorageSpot.Detail] = None
):
  /** Where the entry is, as About shows it. */
  def path: Option[com.histmersion.util.data.diagnostics.StorageSpot.Path] = shown.map(_.where())

object StorageSpot:
  type Path = Path.T
  object Path extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Group = Group.T
  object Group extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Detail = Detail.T
  object Detail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
