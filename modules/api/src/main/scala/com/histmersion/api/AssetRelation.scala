package com.histmersion.api

object AssetRelation:
  val Depicts = "depicts"
  val TakenAt = "taken_at"
  val Illustrates = "illustrates"
  val PageOf = "page_of"
  val all: List[String] = List(Depicts, TakenAt, Illustrates, PageOf)
