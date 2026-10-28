package com.histmersion.web

object Main:

  def main(args: Array[String]): Unit =
    if StandalonePage.isStandalone then StandalonePage.init() else App.init()
