import cats.effect.std.Queue
import cats.effect.{IO, Ref, Temporal}
import fs2.{Pure, Stream}
import fs2.concurrent.SignallingRef
import fs2.concurrent.Topic
import cats.syntax.all.*
import scala.collection.immutable.Map
import scala.concurrent.duration.*
import fs2.concurrent.Channel
import scala.collection.immutable

val General = "*"

object SubscriptionService {
  def create = {
    for {
      signallingRef <- SignallingRef.of[IO, Map[String, List[String]]](Map("*" -> List.empty[String]))
      global <- Topic[IO, String]
      t <- Ref.of[IO, Map[String, Topic[IO, String]]](Map(General -> global))
      a <- Ref.of[IO, Map[String, Queue[IO, String]]](Map.empty)
    } yield SubscriptionService(signallingRef, t, a)
  }
}

case class SubscriptionService(
    signallingRef: SignallingRef[IO, Map[String, List[String]]],
    topics: Ref[IO, Map[String, Topic[IO, String]]],
    queues: Ref[IO, Map[String, Queue[IO, String]]]
) extends Subscription {

  def mystream(handle: String)(using t: Temporal[IO]): Stream[IO, String] = {
    Stream
      .eval(for {
        queue <- Queue.unbounded[IO, String]
        _ <- queues.updateAndGet(e => e.updated(handle, queue))
        _ <- signallingRef.updateAndGet(channels =>
          channels
            .updated(General, channels.get(General).map(subscribers => subscribers :+ handle).getOrElse(List.empty))
        )
      } yield Stream.fromQueueUnterminated(queue))
      .flatten
  }

  def subscribe(channel: String, handle: String): IO[Unit] = {
    for {
      newTopic <- Topic[IO, String]
      _ <- topics.getAndUpdate(t => {
        if (!t.contains(channel)) t.updated(channel, newTopic)
        else t
      })
      _ <- signallingRef.update(channels => channels.updated(channel, channels.getOrElse(channel, List()) :+ handle))
    } yield ()
  }

  def unsubscribe(channel: String, handle: String): IO[Unit] = {
    for {
      _ <- signallingRef.update(channels => {
        val fetchedChannel = channels.get(channel).toList
        val updatedChannel = fetchedChannel.flatMap(subs => subs.filterNot(s => s == handle))

        if (updatedChannel.isEmpty) channels.removed(channel)
        else channels.updated(channel, updatedChannel)
      })
      _ <- for {
        top <- signallingRef.get.map(e => e.get(channel))
        _ <- topics.getAndUpdate(f => if (top.isEmpty) f.removed(channel) else f)
      } yield ()
    } yield ()
  }

  def publish(channel: String, handle: String, message: String): IO[Unit] = {
    for {
      allTopics <- topics.get
      channel <- IO.fromOption(allTopics.get(channel))(throw new RuntimeException(s"Could not find channel ${channel}"))
      _ <- channel.publish1(message)
    } yield ()
  }

  def handleSubscriptions(using t: Temporal[IO]): Stream[IO, Unit] = {
    val a = signallingRef.discrete
      .filter(c => c.getOrElse(General, List()).nonEmpty)
      .evalTap(s => IO.println(s))
      .switchMap(e => {
        val consumerQueues: IO[Seq[(Topic[IO, String], List[Queue[IO, String]])]] =
          for {
            allQueues <- queues.get
            topics <- topics.get
            aa: Seq[(String, List[Queue[IO, String]])] = e.map { case (k, v) =>
              (k, v.flatMap(h => allQueues.get(h).toList))
            }.toList
            b: Seq[(Topic[IO, String], List[Queue[IO, String]])] = aa.flatMap { case (k, v) =>
              topics.get(k).map(t => (t -> v)).toList
            }
          } yield b

        for {
          qq <- Stream.repeatEval(consumerQueues)
          a: Seq[Stream[IO, String]] = qq.map { case (k, v) =>
            k.subscribeUnbounded.evalTap(m => v.traverse(o => o.offer(m)))
          }
          b: Stream[IO, Stream[IO, String]] = Stream.emits[IO, Stream[IO, String]](a)
          _ <- b.parJoinUnbounded
        } yield ()
      })
      .as(())
    a
  }

  def subscribers: Stream[IO, Unit] = {
    val bb: Stream[IO, Unit] = Stream
      .eval(topics.get)
      .flatMap(e => {
        val aa: Seq[Stream[IO, Unit]] =
          e.map(v => v._2.subscribers.evalTap(s => IO.println(s"${v._1}: Number of Subscribers ${s}")).as(())).toList

        val cc = Stream.emits[IO, Stream[IO, Unit]](aa).flatten
        cc
      })
    bb
  }

}
