import java.net.URI
import java.time.Instant
import java.time.temporal.ChronoUnit

import cats.Monoid
import com.twitter.algebird.SketchMap
import io.circe._
import io.circe.generic.auto._

import scala.util.Try

final case class HashTag(text: String)
final case class Url(url: String, expanded_url: String)
final case class Media(media_url: String, `type`: String)
final case class Entities(hashtags: List[HashTag], urls: List[Url], media: Option[List[Media]])
final case class Tweet(text: String, entities: Option[Entities])

final case class EmojiDefinition(name: Option[String], unified: String, short_name: String)

// Average tweets per hour/minute/second

// Total number of tweets received
// Top emojis in tweets
// Percent of tweets that contains emojis
// Top hashtags
// Percent of tweets that contain a url
// Percent of tweets that contain a photo url (pic.twitter.com, pbs.twimg.com, or instagram)
// Top domains of urls in tweets

final case class StatsForDisplay(
                                totalTweets: Long,
                                averageTweetsPerHour: Long,
                                averageTweetsPerMinute: Long,
                                averageTweetsPerSecond: Long,
                                percentTweetsWithEmojis: Long,
                                percentTweetsWithUrl: Long,
                                percentTweetsWithPhoto: Long,
                                topHashTags: List[String],
                                topEmojis: List[EmojiDefinition],
                                topDomains: List[String]
                                )


object StatsForDisplay {
  import StatsRecord._

  val encoder: Encoder[StatsForDisplay] = implicitly[Encoder[StatsForDisplay]]

  def compute(record: StatsRecord, emojiMap: Map[Char, EmojiDefinition]): StatsForDisplay = {
    val secondsPassed = ChronoUnit.SECONDS.between(record.startTime, record.lastUpdatedTime)

    if (secondsPassed == 0L) {
      StatsForDisplay(0, 0, 0, 0, 0, 0, 0, List.empty, List.empty, List.empty)
    } else {
      StatsForDisplay(
        totalTweets = record.stats.totalTweetCount,
        averageTweetsPerHour = (60 * 60 * (record.stats.totalTweetCount.doubleValue() / secondsPassed)).round,
        averageTweetsPerMinute = (60 * (record.stats.totalTweetCount.doubleValue() / secondsPassed)).round,
        averageTweetsPerSecond = (record.stats.totalTweetCount.doubleValue() / secondsPassed).round,
        percentTweetsWithEmojis = (100.0 * (record.stats.tweetsWithEmojiCount.doubleValue() / record.stats.totalTweetCount.doubleValue())).round,
        percentTweetsWithUrl = (100.0 * (record.stats.tweetsWithUrlCount.doubleValue() / record.stats.totalTweetCount.doubleValue())).round,
        percentTweetsWithPhoto = (100.0 * (record.stats.tweetsWithImageCount.doubleValue() / record.stats.totalTweetCount.doubleValue())).round,
        topHashTags = record.stats.hashTagHistogram.heavyHitterKeys,
        topEmojis = topNKeys(10, record.stats.emojiHistoGram, 0L).flatMap(c => emojiMap.get(c)),
        topDomains = record.stats.domainHistogram.heavyHitterKeys
      )
    }
  }

  def topNKeys[K, V: Ordering](n: Int, m: Map[K, V], default: V): List[K] = {
    m.keySet.toList.sortBy(k => m.getOrElse(k, default))(implicitly[Ordering[V]].reverse).take(n)
  }
}

final case class StatsRecord(startTime: Instant, lastUpdatedTime: Instant, stats: Stats)
final case class Stats(
                        totalTweetCount: Long,
                        tweetsWithEmojiCount: Long,
                        tweetsWithUrlCount: Long,
                        tweetsWithImageCount: Long,
                        hashTagHistogram: SketchMap[String, Long],
                        domainHistogram: SketchMap[String, Long],
                        emojiHistoGram: Map[Char, Long]
                      )

object StatsRecord {
  implicit val encoderInstant = new Encoder[Instant] {
    override def apply(a: Instant): Json = Json.fromLong(a.getEpochSecond)
  }

  def start(startTime: Instant) = new StatsRecord(startTime, startTime, Stats.monoid.empty)

  def update(statsRecord: StatsRecord, lastUpdatedTime: Instant, stats: Stats) = statsRecord.copy(
    lastUpdatedTime = lastUpdatedTime,
    stats = Stats.monoid.combine(statsRecord.stats, stats)
  )
}

object Stats {
  val monoid = new Monoid[Stats] {
    override def empty: Stats = Stats(0, 0, 0, 0, Algebird.stringLongMonoid.zero, Algebird.stringLongMonoid.zero, Map.empty)

    override def combine(x: Stats, y: Stats): Stats = Stats(
      x.totalTweetCount + y.totalTweetCount,
      x.tweetsWithEmojiCount + y.tweetsWithEmojiCount,
      x.tweetsWithUrlCount + y.tweetsWithUrlCount,
      x.tweetsWithImageCount + y.tweetsWithImageCount,
      Algebird.stringLongMonoid.plus(x.hashTagHistogram, y.hashTagHistogram),
      Algebird.stringLongMonoid.plus(x.domainHistogram, y.domainHistogram),
      combineHistograms(x.emojiHistoGram, y.emojiHistoGram)
    )
  }

  def combineHistograms[A](x: Map[A, Long], y: Map[A, Long]): Map[A, Long] = {
    val empty: Map[A, Long] = Map.empty

    val (biggerMap, smallerMap) = if (x.size > y.size) (x, y) else (y, x)

    smallerMap.keySet.foldLeft(biggerMap) {
      case (m, k) => m.updated(k, biggerMap.get(k).getOrElse(0L) + smallerMap.get(k).getOrElse(0L))
    }
  }
}

object Tweet {
  val decoder: Decoder[Tweet] = implicitly[Decoder[Tweet]]
  val encoder: Encoder[Tweet] = implicitly[Encoder[Tweet]]

  def tweetsToStats(emojiMap: Map[Char, EmojiDefinition], tweets: Traversable[Tweet]): Stats = {
    Stats.monoid.combineAll(tweets.toList.map(t => tweetToStats(emojiMap, t)))
  }

  def tweetToStats(emojiMap: Map[Char, EmojiDefinition], tweet: Tweet): Stats = {
    val hashtagHisto : Map[String, Long] = tweet.entities.map(e => e.hashtags.map(ht => (ht.text, 1L)).toMap).getOrElse(Map.empty)
    val domainHisto: Map[String, Long] = tweet.entities.map(e => e.urls.flatMap(url => toDomain(url).map((_, 1L))).toMap).getOrElse(Map.empty)

    val emojiHisto : Map[Char, Long] = tweet.text.toCharArray.foldLeft(Map.empty[Char, Long]) {
      case (m, char) => emojiMap.get(char) match {
        case Some(_) => m.get(char) match {
          case Some(v) => m.updated(char, v+1)
          case None    => m.updated(char, 1L)
        }
        case None    => m
      }
    }

    val hasPhoto  = tweet.entities.flatMap(e => e.media).map(list => list.exists(m => m.`type`.equals("photo"))).getOrElse(false)
    val hasUrl    = !domainHisto.isEmpty
    val hasEmojis = !emojiHisto.isEmpty

    Stats(
      1L,
      if (hasEmojis) 1L else 0L,
      if (hasUrl) 1L else 0L,
      if (hasPhoto) 1L else 0L,
      Algebird.stringLongMonoid.create(hashtagHisto.toList),
      Algebird.stringLongMonoid.create(domainHisto.toList),
      emojiHisto
    )
  }

  // NOTE: Dealing with an exception throwing API, Lets just use Option to track failure for now.
  def toDomain(url: Url): Option[String] = {
    import scala.language.postfixOps
    Try {
      val uri = URI.create(url.expanded_url)
      uri.getHost()
    } toOption
  }
}

object EmojiDefinition{
  val decoder: Decoder[EmojiDefinition] = implicitly[Decoder[EmojiDefinition]]
  val encoder: Encoder[EmojiDefinition] = implicitly[Encoder[EmojiDefinition]]
}
