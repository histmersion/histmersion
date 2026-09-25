package com.histmersion.web

import org.scalajs.dom

object Main:

  def main(args: Array[String]): Unit =
    if StandalonePage.isStandalone then StandalonePage.init()
    else
      dom.window.location.pathname match
        case "/acquire" => AcquirePage.init()
        case _          => App.init()
