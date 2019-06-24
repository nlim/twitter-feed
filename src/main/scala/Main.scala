import java.io.{BufferedReader, File, FileReader}

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze._
import fs2.Stream
import fs2.text
import java.nio.file.Paths
import java.time.Instant
import org.http4s.circe._
import io.circe.syntax._
import io.circe.generic.auto._

object Main extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    getConfigAndRun { c =>
      getEmojiFileAndRun { emojiDefs =>
        val emojiMap: Map[Char, EmojiDefinition] = makeEmojiMap(emojiDefs)

        for {
          startInstant <- getCurrentInstant
          ref <- Ref.of[IO, StatsRecord](StatsRecord.start(startInstant))
          _ <- IO.race(makeServer(emojiMap, ref), (new TWStream[IO](getCurrentInstant)).run(emojiMap, ref, c))
        } yield ExitCode.Success
      }
    }
  }

  implicit val emojiListDecoder: io.circe.Decoder[List[EmojiDefinition]] = io.circe.Decoder.decodeList[EmojiDefinition](EmojiDefinition.decoder)

  val emojiFileName = "src/main/resources/emoji.json"

  def getEmojiFile: IO[Either[io.circe.Error, List[EmojiDefinition]]] = {
    val s: Stream[IO, String] =  fs2.io.file.readAll[IO](Paths.get(emojiFileName), scala.concurrent.ExecutionContext.global, 4096).through(text.utf8Decode)
    val ioString: IO[String] = s.compile.toVector.map(v => v.mkString(""))

    ioString.map(string =>
      io.circe.parser.decode[List[EmojiDefinition]](string)
    )
  }

  // NOTE (Digits keep showing up in the top Emojis without this exclusion list)
  val charsToDrop = Set('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '#', '?')

  def makeEmojiMap(list: List[EmojiDefinition]): Map[Char, EmojiDefinition] =
    list.foldLeft(Map.empty[Char, EmojiDefinition]) {
      case (m, ed) =>
        UnicodeUtils.getUnicodeString(ed) match {
          case Some(char) if !charsToDrop.contains(char) => m.updated(char, ed)
          case _ => m
        }
    }

  def printErrLn(s: String): IO[Unit] = {
    IO.delay(System.err.println(s))
  }

  val getCurrentInstant: IO[Instant] = {
    IO.delay(Instant.now())
  }

  def getEmojiFileAndRun(f: List[EmojiDefinition] => IO[ExitCode]): IO[ExitCode] = {
      for {
        either <- getEmojiFile
        x <- either.fold(err => printErrLn(err.toString) >> IO.pure(ExitCode.Error), f)
      } yield x
  }

  def getConfigAndRun(f: TwitterFeedConfig => IO[ExitCode]): IO[ExitCode] = {
    TwitterFeedConfig.getConfig.fold(
      configErrors => printErrLn(configErrors.toString) >> IO.pure(ExitCode.Error),
      f
    )
  }

  def statsService(emojiMap: Map[Char, EmojiDefinition], ref: Ref[IO, StatsRecord]): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "stats" =>
      val result = for {
        statsRecord <- ref.get
        statsForDisplay: StatsForDisplay = StatsForDisplay.compute(statsRecord, emojiMap)
        r <- Ok(StatsForDisplay.encoder(statsForDisplay))
      } yield r

      result.handleErrorWith(f => {
        printErrLn(f.getStackTrace().mkString("\n")) >> Ok("error")
      })
  }

  def makeServer(emojiMap: Map[Char, EmojiDefinition], ref: Ref[IO, StatsRecord]): IO[Unit] = {
    BlazeServerBuilder[IO].bindHttp(8080, "localhost")
      .withHttpApp(Router("/" -> statsService(emojiMap, ref)).orNotFound)
      .serve
      .compile
      .drain
  }
}

