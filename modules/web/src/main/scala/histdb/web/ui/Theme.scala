package histdb.web.ui

object Theme:
  val bg: CssColor       = CssColor("#12141a")
  val panel: CssColor    = CssColor("#1b1e27")
  val ink: CssColor      = CssColor("#e6e8ee")
  val muted: CssColor    = CssColor("#9aa3b2")
  val accent: CssColor   = CssColor("#6ea8fe")
  val line: CssColor     = CssColor("#2a2f3a")
  val inputBg: CssColor  = CssColor("#0e1016")

object Layout:
  val sidebarMinWidth: Px  = Px(200)
  val sidebarMaxWidth: Px  = Px(800)
  val sidebarDefault: Px   = Px(360)
  val overviewHeight: Px   = Px(44)
  val overviewBase: Px     = Px(30)
  val detailHeight: Px     = Px(58)
  val detailBase: Px       = Px(40)
  val handleWidth: Double  = 6.0
  val markerRadiusDefault: Int = 5
  val maxBuckets: Long     = 1500L
  val minSpan: Long        = 10L
