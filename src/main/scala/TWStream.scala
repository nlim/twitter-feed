import java.time.Instant
import java.time.temporal.ChronoUnit

import org.http4s._
import org.http4s.client.blaze._
import org.http4s.client.oauth1
import cats.effect._
import cats.implicits._
import fs2.Stream
import fs2.io.stdout
import fs2.text.{lines, utf8Encode}
import io.circe.{Encoder, Json}
import io.circe.syntax._
import io.circe.Decoder.Result
import jawnfs2._
import java.util.concurrent.{ExecutorService, Executors, TimeUnit}

import cats.{Applicative, Monad}
import cats.effect.concurrent.Ref

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.FiniteDuration

class TWStream[F[_]](getCurrentInstant: F[Instant])(implicit F: ConcurrentEffect[F], cs: ContextShift[F], timer: Timer[F]) {
  // Use the Circe Json AST for in order to use parseJsonStream from jawn-fs2
  implicit val f = io.circe.jawn.CirceSupportParser.facade

  // Used code from: https://http4s.org/v0.20/streaming/  for reference
  def sign(consumerKey: String, consumerSecret: String, accessToken: String, accessSecret: String)(req: Request[F]): F[Request[F]] = {
    val consumer = oauth1.Consumer(consumerKey, consumerSecret)
    val token    = oauth1.Token(accessToken, accessSecret)
    oauth1.signRequest(req, consumer, callback = None, verifier = None, token = Some(token))
  }

  implicit val decoder = Tweet.decoder
  implicit val encoder = Tweet.encoder

  def jsonStream(consumerKey: String, consumerSecret: String, accessToken: String, accessSecret: String)(req: Request[F]): Stream[F, Tweet] = {
    for {
      client <- BlazeClientBuilder(global).stream
      sr <- Stream.eval(sign(consumerKey, consumerSecret, accessToken, accessSecret)(req))
      res <- client.stream(sr).flatMap(_.body.chunks.parseJsonStream)
      tweet <- resultToStream(decoder.decodeJson(res))
    } yield tweet
  }

  def resultToStream[A](result: Result[A]): Stream[F, A] = {
    result match {
      case Left(_) =>  Stream.empty
      case Right(a) => Stream(a)
    }
  }

  def twitterStream(c: TwitterFeedConfig): Stream[F, Tweet] = {
    val req = Request[F](Method.GET, Uri.uri("https://stream.twitter.com/1.1/statuses/sample.json"))
    jsonStream(
      c.consumerKey,
      c.consumerSecret,
      c.accessToken,
      c.accessSecret
    )(req)
  }

  def twitterStatsStream(config: TwitterFeedConfig, emojiMap: Map[Char, EmojiDefinition]): Stream[F, Stats] = {
    twitterStream(config).chunkN(60, true).map(c => Stream.eval(Applicative[F].pure(Tweet.tweetsToStats(emojiMap, c.toList)))).parJoin(4)
  }

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

  /** Compile our stream down to an effect to make it runnable */
  def runWithRef(emojiMap: Map[Char, EmojiDefinition], ref: Ref[F, StatsRecord], config: TwitterFeedConfig): F[Unit] = {
    twitterStatsToRef(config, emojiMap, ref)
      .map(s => ChronoUnit.SECONDS.between(s.startTime, s.lastUpdatedTime).toString + ", " + s.stats.totalTweetCount)
      .compile.drain
  }
}
