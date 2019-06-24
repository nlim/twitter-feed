import cats.effect.{ExitCode, IO, IOApp}
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

object Test extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val t = Tweet("RT @evi0309: テヨン お団子コレクション #taeyeon #テヨン #snsd #少女時代 #GG4EVA #사계 #FourSeasons #VOICE https://t.co/5WBaiqA06T",Some(Entities(List(HashTag("taeyeon"), HashTag("テヨン"), HashTag("snsd"), HashTag("少女時代"), HashTag("GG4EVA"), HashTag("사계"), HashTag("FourSeasons"), HashTag("VOICE")),List(),Some(List(Media("http://pbs.twimg.com/media/D9oeEBUUcAEw86P.jpg", "photo"))))))
    val t2 = Tweet("RT @evi0309: テヨン お団子コレクション 2013➡️2014➡️2015➡️2019 #bazqux #テヨン #snsd #少女時代 #GG4EVA #사계 #FourSeasons #VOICE https://t.co/5WBaiqA06T",Some(Entities(List(HashTag("bazqux"), HashTag("テヨン"), HashTag("snsd"), HashTag("少女時代"), HashTag("GG4EVA"), HashTag("사계"), HashTag("FourSeasons"), HashTag("VOICE")),List(),Some(List()))))
    val t3 = Tweet("RT @evi0309: テヨン お団子コレクション 2013➡️2014➡️2015➡️2019 #foobar #テヨン #snsd #少女時代 #GG4EVA #사계 #FourSeasons #VOICE https://t.co/5WBaiqA06T",Some(Entities(List(HashTag("foobar"), HashTag("テヨン"), HashTag("snsd"), HashTag("少女時代"), HashTag("GG4EVA"), HashTag("사계"), HashTag("FourSeasons"), HashTag("VOICE")),List(Url("https://t.co/NcK3oXCZIw","https://t1.daumcdn.net/cfile/tistory/996181445D0D93143D")),Some(List()))))
    for {
      either <- Main.getEmojiFile
      x <- either.fold(
        err => Main.printErrLn(err.toString) >> IO.pure(ExitCode.Error),
        emojiDefs => {
          val emojiMap: Map[Char, EmojiDefinition] = Main.makeEmojiMap(emojiDefs)
          val stats: Stats = Tweet.tweetsToStats(emojiMap, List(t, t2, t3))
          Main.printErrLn(stats.toString) >> IO.pure(ExitCode.Success)
        }
      )
    } yield x
  }
}
