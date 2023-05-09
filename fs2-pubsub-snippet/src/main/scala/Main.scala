import cats.effect.std.Queue
import fs2.{Pipe, Stream}
import cats.effect.{ExitCode, IO, IOApp, Ref, Resource, Temporal}
import cats.syntax.all.*
import com.comcast.ip4s.{ipv4, port}
import fs2.concurrent.{SignallingRef, Topic}
import org.http4s.{HttpRoutes, Response}
import org.http4s.Method.GET
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import io.circe.generic.auto.*
//import io.circe.jawn
import io.circe.syntax.*
import org.http4s.websocket.WebSocketFrame.Text
import cats.effect.syntax.all.*
import cats.syntax.all.*

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.*
import java.util.UUID
import scala.util.matching.Regex
import Commands.*

object Main extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    for {
      service <- SubscriptionService.create
      server = Stream.resource(serverResource(service) >> Resource.never)
      _ <- server
        .concurrently(service.handleSubscriptions)
        .concurrently(service.subscribers)
        .compile
        .drain
    } yield ExitCode.Success
  }

  private def serverResource(service: SubscriptionService): Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8181")
      .withIdleTimeout(2.days) // WS times out without phone home
      .withHttpWebSocketApp(ws => routes(ws, service).orNotFound)
      .build

  private def routes(
      ws: WebSocketBuilder2[IO],
      service: Subscription
  )(using t: Temporal[IO]): HttpRoutes[IO] =
    HttpRoutes.of[IO] { case GET -> Root / "ws" / handle =>
      val receive: Pipe[IO, WebSocketFrame, Unit] =
        in =>
          in.evalMap {
            case Text(joinCommand(txt), _)              => service.subscribe(txt, handle)
            case Text(sendCommand(channel, message), _) => service.publish(channel, handle, message)
            case Text(leaveCommand(channel), _)         => service.unsubscribe(channel, handle)
            case _                                      => IO.unit
          }

      val send = service.mystream(handle).map(e => WebSocketFrame.Text(s"$e"))

      ws.build(send, receive)
    }
}
