package com.histmersion.web.ui

enum SidebarTab(val key: SidebarTab.Key, val label: SidebarTab.Label):
  case Sources extends SidebarTab(com.histmersion.web.ui.SidebarTab.Key("sources"), com.histmersion.web.ui.SidebarTab.Label("Sources"))
  case Articles extends SidebarTab(com.histmersion.web.ui.SidebarTab.Key("articles"), com.histmersion.web.ui.SidebarTab.Label("Articles"))
  case Events extends SidebarTab(com.histmersion.web.ui.SidebarTab.Key("events"), com.histmersion.web.ui.SidebarTab.Label("Events"))
  case Places extends SidebarTab(com.histmersion.web.ui.SidebarTab.Key("places"), com.histmersion.web.ui.SidebarTab.Label("Places"))
  case Pictures extends SidebarTab(com.histmersion.web.ui.SidebarTab.Key("pictures"), com.histmersion.web.ui.SidebarTab.Label("Pictures"))

object SidebarTab:
  /** The tab a kept key names; an old link's `bytime`/`byregion` (tabs no longer shown) is the sources'. */
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def of(key: Option[com.histmersion.web.ui.SidebarTab.Key]): SidebarTab = key.flatMap(k => values.find(_.key == k)).getOrElse(Sources)
