package com.histmersion.web.ui

enum SidebarTab(val key: SidebarTab.Key, val label: SidebarTab.Label):
  case Sources extends SidebarTab("sources", "Sources")
  case Articles extends SidebarTab("articles", "Articles")
  case Events extends SidebarTab("events", "Events")
  case Places extends SidebarTab("places", "Places")
  case Pictures extends SidebarTab("pictures", "Pictures")

object SidebarTab:
  /** The tab a kept key names; an old link's `bytime`/`byregion` (tabs no longer shown) is the sources'. */
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type OfKey = OfKey.T
  object OfKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(key: Option[com.histmersion.web.ui.SidebarTab.OfKey]): SidebarTab = key.flatMap(k => values.find(_.key == k)).getOrElse(Sources)
