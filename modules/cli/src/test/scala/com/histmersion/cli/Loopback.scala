package com.histmersion
package cli

object Loopback:

  def url(port: Int): Client.ServerUrl = Client.ServerUrl(s"http://127.0.0.1:$port")
