package com.histmersion.api.backup
import java.util.Locale
import com.histmersion.api.given

enum BackupState:
  case Running, Done, Failed
  def wire: String = toString.toLowerCase(java.util.Locale.ROOT)

object BackupState:
  def parse(s: String): Option[BackupState] = values.find(_.wire == s)
