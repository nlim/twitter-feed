import java.time.Instant

import org.http4s._
import org.http4s.client.blaze._
import org.http4s.client.oauth1
import cats.effect._
import cats.implicits._
import fs2.Stream
import io.circe.syntax._
import io.circe.Decoder.Result
import jawnfs2._

import cats.{Applicative}
import cats.effect.concurrent.Ref

import scala.concurrent.ExecutionContext.global

// Took inspiration for this class from: https://http4s.org/v0.20/streaming/
// Lets you program to a generic F type constructor, IO is the only one used in this project right now.
class TwitterStream[F[_]](getCurrentInstant: F[Instant])(implicit F: ConcurrentEffect[F], cs: ContextShift[F], timer: Timer[F]) {
  // Use the Circe Json AST for in order to use parseJsonStream from jawn-fs2
  implicit val f = io.circe.jawn.CirceSupportParser.facade
  implicit val decoder = Tweet.decoder
  implicit val encoder = Tweet.encoder

  // Used code from: https://http4s.org/v0.20/streaming/ to to the Oauth signing, and obtaining the Json stream in `jsonStream` function
  def signRequest(consumerKey: String, consumerSecret: String, accessToken: String, accessSecret: String)(req: Request[F]): F[Request[F]] = {
    val consumer = oauth1.Consumer(consumerKey, consumerSecret)
    val token    = oauth1.Token(accessToken, accessSecret)
    oauth1.signRequest(req, consumer, callback = None, verifier = None, token = Some(token))
  }


  def jsonStream(consumerKey: String, consumerSecret: String, accessToken: String, accessSecret: String)(req: Request[F]): Stream[F, Tweet] = {
    for {
      client <- BlazeClientBuilder(global).stream
      sr <- Stream.eval(signRequest(consumerKey, consumerSecret, accessToken, accessSecret)(req))
      res <- client.stream(sr).flatMap(_.body.chunks.parseJsonStream)
      // NOTE in the Json stream, there are some "delete" objects, using resultToStream will only keep objects that can be parsed
      // as a Tweet case class
      tweet <- resultToStream(decoder.decodeJson(res))
    } yield tweet
  }

  def twitterStream(c: TwitterFeedConfig): Stream[F, Tweet] = {
    val req = Request[F](Method.GET, Uri.uri("https://stream.twitter.com/1.1/statuses/sample.json"))
    jsonStream(c.consumerKey, c.consumerSecret, c.accessToken, c.accessSecret)(req)
  }

  def resultToStream[A](result: Result[A]): Stream[F, A] = {
    result match {
      case Left(_) =>  Stream.empty
      case Right(a) => Stream(a)
    }
  }

  def twitterStatsStream(config: TwitterFeedConfig, emojiMap: Map[Char, EmojiDefinition]): Stream[F, Stats] = {
    twitterStream(config).chunkN(10, true)
      // Mapping to compute Stream[F, Stream[F, Stats]] for the Stream[F, Chunk[Tweet]]
      .map(c => Stream.eval(Applicative[F].pure(Tweet.tweetsToStats(emojiMap, c.toList))))
      // Using parJoin in introduce some concurrency, so we compute Chunk[Tweet] => Stats in concurrently for each chunk
      .parJoin(4)
  }

  // Cats Ref[F, T] modify function requires that you return a 2-tuple
  def modifyFunction(instant: Instant, stats: Stats)(statsRecord: StatsRecord): (StatsRecord, StatsRecord) = {
    val result = StatsRecord.update(statsRecord, instant, stats)
    (result, result)
  }

  def twitterStatsToRef(config: TwitterFeedConfig, emojiMap: Map[Char, EmojiDefinition], ref: Ref[F, StatsRecord]): Stream[F, StatsRecord] = {
    twitterStatsStream(config, emojiMap)
      .evalMap(stats =>
        for {
          instant <- getCurrentInstant
          r <- ref.modify(modifyFunction(instant, stats))
        } yield r
      )
  }

  def run(emojiMap: Map[Char, EmojiDefinition], ref: Ref[F, StatsRecord], config: TwitterFeedConfig): F[Unit] = {
    twitterStatsToRef(config, emojiMap, ref).compile.drain
  }
}
