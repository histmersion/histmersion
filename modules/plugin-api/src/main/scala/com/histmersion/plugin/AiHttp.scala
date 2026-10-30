package com.histmersion.plugin

/** The host's client for model calls: HTTP/1.1, the host's User-Agent, a timeout per call, nothing it sends or receives
  * ever logged (a key travels in a header). Failures to connect are `Left`; an HTTP error status is a response (the
  * provider reads its body).
  */
trait AiHttp:
  def post(url: String, headers: Map[String, String], body: String, timeoutSeconds: Int): Either[String, AiHttpResponse]
  def get(url: String, headers: Map[String, String], timeoutSeconds: Int): Either[String, AiHttpResponse]

object AiHttp:
  /** A client that sends nothing (a test, `--self-check`). */
  val Offline: AiHttp = new AiHttp:
    def post(url: String, headers: Map[String, String], body: String, timeoutSeconds: Int) = Left("offline")
    def get(url: String, headers: Map[String, String], timeoutSeconds: Int) = Left("offline")
