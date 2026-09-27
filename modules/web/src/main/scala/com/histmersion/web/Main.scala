package com.histmersion.web

import org.scalajs.dom

object Main:

  def main(args: Array[String]): Unit =
    if StandalonePage.isStandalone then StandalonePage.init()
    else
      // the bookkeeping lists (`/acquire`, `/tasks`, `/log`) are windows of the app; their own
      // addresses open them there, so old and hand-typed links keep working
      WindowKey.ofPath(dom.window.location.pathname) match
        case Some(key) => dom.window.location.replace("/#open=" + key.wire)
        case None      => App.init()
