package edgar

case class IPPool(poolSize: Int)
{
  private val ips =
  {
    var suffixes = Set[String]()
    
    for (i <- 0 until poolSize) {
      var suffix = (for {_ <- 0 until 3} yield ((math.random() * 26).floor.toInt + 97).asInstanceOf[Char]).mkString
      while (suffixes(suffix)) {
        suffix = (for {_ <- 0 until 3} yield ((math.random() * 26).floor.toInt + 97).asInstanceOf[Char]).mkString
      }
      
      suffixes = suffixes + suffix
    }
    
    suffixes.map {
      (for (_ <- 0 until 3) yield (math.random() * 256).floor.toInt).mkString(".") + "." + _
    }.toIndexedSeq
  }
  
  def draw(): String = ips((math.random() * poolSize).floor.toInt)
  
  def size: Int = ips.size
  
  def isEmpty: Boolean = ips.isEmpty
  
  def get(n: Int): String = if (n < 0 || n >= poolSize) null else ips(n)
}
