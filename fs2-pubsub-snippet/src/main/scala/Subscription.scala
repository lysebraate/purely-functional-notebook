import cats.effect.{IO, Temporal}
import fs2.Stream

trait Subscription {

  def mystream(handle: String)(using t: Temporal[IO]): Stream[IO, String]

  def subscribe(channel: String, handle: String): IO[Unit]

  def unsubscribe(channel: String, handle: String): IO[Unit]

  def publish(channel: String, handle: String, message: String): IO[Unit]
}
