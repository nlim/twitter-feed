import com.typesafe.config.ConfigFactory
import pureconfig.generic.auto._

case class TwitterFeedConfig(consumerKey: String, consumerSecret: String, accessToken: String, accessSecret: String)

object TwitterFeedConfig {
  def getConfig = {
    pureconfig.loadConfig[TwitterFeedConfig]
  }
}
