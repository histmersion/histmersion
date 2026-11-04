package com.histmersion.domain.detectorfixtures

import java.nio.file.{Files as Disk, Path}
import java.net.http.HttpClient

object ImportedAccess:
  def file(p: Path) = Disk.readString(p) // expect: filesystem
  def http = HttpClient.newHttpClient() // expect: http
