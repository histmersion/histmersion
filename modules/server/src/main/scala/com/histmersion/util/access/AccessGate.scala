package com.histmersion
package util.access

import java.net.InetSocketAddress

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.*
import io.netty.util.ReferenceCountUtil

import com.histmersion.config.AuthConfig
import com.histmersion.lint.{LintRule, tolerated}

/** The netty gate every request passes first, whatever its method ([[Admission.decide]] says how a request is let in).
  * A request it lets in carries the admission in [[Admission.Header]] (any value the client sent is removed first); a
  * refused one is answered here ([[Refusal]]) and its body chunks are dropped up to its last, so the connection stays
  * usable.
  *
  * It sits directly after the HTTP codec, where a request is a head followed by its chunks: further on, the streams
  * handler turns every request with a body into a streamed one. The key is read at each request (a reload may set,
  * change or clear it); the networks, names and origins are the policy the listener was bound with. One instance per
  * connection: `discarding` is that connection's state.
  */
final class AccessGate(
    policy: AccessPolicy,
    apiKey: () => AuthConfig.ApiKey,
    liveRunToken: Admission.Bearer => Boolean,
    agent: Admission.AgentPaths
) extends ChannelInboundHandlerAdapter:
  // `var`: this connection is between a refused request's head and its last chunk
  private var discarding = false

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit =
    msg match
      case req: HttpRequest =>
        val headers = req.headers()
        val _ = headers.remove(Admission.Header)
        Admission.decide(request(ctx, req), policy, agent) match
          case Right(admission) =>
            val _ = headers.set(Admission.Header, admission.wire)
            super.channelRead(ctx, msg)
          case Left(problem) =>
            scribe.info(s"refused ${req.method()} ${Admission.Path.ofTarget(req.uri())}: ${problem.productPrefix}")
            discarding = !req.isInstanceOf[LastHttpContent]
            Refusal(problem).answer(ctx, req)
      case c: HttpContent if discarding =>
        if c.isInstanceOf[LastHttpContent] then discarding = false
        val _ = ReferenceCountUtil.release(c)
      case _ => super.channelRead(ctx, msg)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def request(ctx: ChannelHandlerContext, req: HttpRequest): Admission.Request =
    val headers = req.headers()
    def header(name: CharSequence): Option[Admission.HeaderText] =
      Option(headers.get(name)).map(Admission.HeaderText(_))
    val remote = ctx.channel().remoteAddress().asInstanceOf[InetSocketAddress]
    val local = ctx.channel().localAddress().asInstanceOf[InetSocketAddress]
    Admission.Request(
      peer = remote.getAddress,
      port = AccessPolicy.Port(local.getPort),
      method = Admission.Method.of(req.method().name()),
      path = Admission.Path.ofTarget(req.uri()),
      host = header(HttpHeaderNames.HOST),
      origin = header(HttpHeaderNames.ORIGIN),
      referer = header(HttpHeaderNames.REFERER),
      forwarding = Forwarding.of(name => Admission.HeaderText.all(headers.getAll(name))),
      presented = presented(headers.get(HttpHeaderNames.AUTHORIZATION))
    )

  private def presented(authorization: String): Admission.Presented =
    Admission.Bearer.of(authorization) match
      case None                           => Admission.Presented.Absent
      case Some(b) if b.matches(apiKey()) => Admission.Presented.ApiKey
      case Some(b) if liveRunToken(b)     => Admission.Presented.RunToken
      case Some(_)                        => Admission.Presented.Wrong
