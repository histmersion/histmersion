package com.histmersion.web

@main def histmersionWeb(): Unit =
  if StandalonePage.isStandalone then StandalonePage.init() else App.init()
