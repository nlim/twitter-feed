import com.twitter.algebird._
import scala.language.implicitConversions

object Algebird {

  val DELTA = 1E-8
  val EPS = 0.001
  val SEED = 1
  val HEAVY_HITTERS_N = 10

  def string2Bytes(i : String) = i.toCharArray.map(_.toByte)
  val params = SketchMapParams[String](SEED, EPS, DELTA, HEAVY_HITTERS_N)(string2Bytes)

  val stringLongMonoid = SketchMap.monoid[String, Long](params)
}
