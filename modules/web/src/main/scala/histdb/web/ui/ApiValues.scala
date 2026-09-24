package histdb.web.ui

enum AggregateBy(val param: String):
  case Time       extends AggregateBy("time")
  case Region     extends AggregateBy("region")
  case TimeRegion extends AggregateBy("time,region")

enum AggregateUnit(val param: Option[String]):
  case Sources  extends AggregateUnit(None)
  case Findings extends AggregateUnit(Some("findings"))
  case Starts   extends AggregateUnit(Some("starts"))

enum SidebarTab(val key: String, val label: String):
  case Sources  extends SidebarTab("sources", "sources")
  case Articles extends SidebarTab("articles", "articles")
  case Events   extends SidebarTab("events", "events")
  case ByTime   extends SidebarTab("bytime", "by time")
  case ByRegion extends SidebarTab("byregion", "by region")
