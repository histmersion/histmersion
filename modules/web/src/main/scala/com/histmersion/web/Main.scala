package com.histmersion.web

import org.scalajs.dom

object Main:

  def main(args: Array[String]): Unit =
    if StandalonePage.isStandalone then StandalonePage.init()
    else
      dom.window.location.pathname match
        // the acquisition queue is a window of the app now; the old address opens it there
        case "/acquire" => dom.window.location.replace("/#open=" + WindowKey.Acquire.wire)
        case _          => App.init()
