package com.histmersion.web.ui

enum SidebarTab(val key: String, val label: String):
  case Sources extends SidebarTab("sources", "Sources")
  case Articles extends SidebarTab("articles", "Articles")
  case Events extends SidebarTab("events", "Events")
  case Places extends SidebarTab("places", "Places")
  case Pictures extends SidebarTab("pictures", "Pictures")

object SidebarTab:
  /** The tab a kept key names; an old link's `bytime`/`byregion` (tabs no longer shown) is the sources'. */
  def of(key: Option[String]): SidebarTab = key.flatMap(k => values.find(_.key == k)).getOrElse(Sources)
