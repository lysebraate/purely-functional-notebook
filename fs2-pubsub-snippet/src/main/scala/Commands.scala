import scala.util.matching.Regex

object Commands {

  val joinCommand: Regex = raw"\/join (\S*)".r
  val leaveCommand: Regex = raw"\/leave (\S*)".r
  val sendCommand: Regex = raw"\/send (\S*) (.*)".r

}
