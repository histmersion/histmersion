package com.histmersion
package util.access

enum Forwarding derives CanEqual:
  case Absent
  case Marked(
      forwarded: List[Admission.HeaderText],
      forwardedFor: List[Admission.HeaderText],
      realIp: List[Admission.HeaderText]
  )

object Forwarding:
  val ForwardedHeader = "Forwarded"
  val ForwardedForHeader = "X-Forwarded-For"
  val RealIpHeader = "X-Real-IP"

  val Headers: List[String] =
    List(ForwardedHeader, ForwardedForHeader, "X-Forwarded-Host", "X-Forwarded-Proto", RealIpHeader, "Via")

  def of(valuesOf: String => List[Admission.HeaderText]): Forwarding =
    if Headers.exists(name => valuesOf(name).nonEmpty) then
      Marked(valuesOf(ForwardedHeader), valuesOf(ForwardedForHeader), valuesOf(RealIpHeader))
    else Absent
