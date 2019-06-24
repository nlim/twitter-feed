import com.twitter.algebird._
import scala.language.implicitConversions

object Algebird {

  // Delta and epsilon and uncertainty measures
  // Sketches are within epsilon of the true value
  // with probability 1 - delta
  //
  // Allows us to store histograms in sub-linear space.
  //
  val DELTA = 1E-4
  val EPS = 0.01
  val SEED = 1
  val HEAVY_HITTERS_N = 10

  def string2Bytes(i : String) = i.toCharArray.map(_.toByte)
  val params = SketchMapParams[String](SEED, EPS, DELTA, HEAVY_HITTERS_N)(string2Bytes)

  val stringLongMonoid = SketchMap.monoid[String, Long](params)
}
