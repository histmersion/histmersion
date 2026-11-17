package com.histmersion
package util.access

import com.github.plokhotnyuk.jsoniter_scala.core.writeToArray
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import io.netty.util.ReferenceCountUtil

import com.histmersion.api.Api
import com.histmersion.util.problem.Problem

/** A request a netty gate refuses before any endpoint sees it, answered as every endpoint answers a failure: the
  * problem's status and the `{"problems": [...]}` body of [[Api.Error]] (a 401 also names the scheme it wants).
  */
final case class Refusal(problem: Problem):

  /** Answers `request` on `ctx` and releases it. Whether its body is still to come (and must be dropped) is the
    * caller's: it is when `request` is not its own last chunk.
    */
  def answer(ctx: ChannelHandlerContext, request: HttpRequest): Unit =
    val error = Api.Error(problem)
    val status = error.status
    val body = writeToArray(error)
    val keepAlive = HttpUtil.isKeepAlive(request)
    val _ = ReferenceCountUtil.release(request)
    val response = DefaultFullHttpResponse(
      HttpVersion.HTTP_1_1,
      HttpResponseStatus.valueOf(status.code),
      Unpooled.wrappedBuffer(body)
    )
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json")
    response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, body.length)
    if status.code == HttpResponseStatus.UNAUTHORIZED.code then
      response.headers().set(HttpHeaderNames.WWW_AUTHENTICATE, "Bearer")
    HttpUtil.setKeepAlive(response, keepAlive)
    val _ = ctx.writeAndFlush(response)
