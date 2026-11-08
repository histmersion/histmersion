package com.histmersion
package web.ui

import io.github.iltotore.iron.constraint.any.Pure

enum SidebarTab(val key: SidebarTab.Key, val label: SidebarTab.Label):
  case Sources extends SidebarTab(SidebarTab.Key("sources"), SidebarTab.Label("Sources"))
  case Articles extends SidebarTab(SidebarTab.Key("articles"), SidebarTab.Label("Articles"))
  case Events extends SidebarTab(SidebarTab.Key("events"), SidebarTab.Label("Events"))
  case Places extends SidebarTab(SidebarTab.Key("places"), SidebarTab.Label("Places"))
  case Pictures extends SidebarTab(SidebarTab.Key("pictures"), SidebarTab.Label("Pictures"))

object SidebarTab:
  /** The tab a kept key names; an old link's `bytime`/`byregion` (tabs no longer shown) is the sources'. */
  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]


  def of(key: Option[Key]): SidebarTab = key.flatMap(k => values.find(_.key == k)).getOrElse(Sources)
