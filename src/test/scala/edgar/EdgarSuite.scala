package edgar

import org.junit.runner.RunWith
import org.scalacheck.Gen
import org.scalacheck.Prop._
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.Checkers


@RunWith(classOf[JUnitRunner])
class EdgarSuite extends FunSuite with Checkers
{
  val poolSize = 50
  val ipPool = IPPool(poolSize)
  
  test("IP pool works") {
    assert(ipPool.size == 50, s"The IP pool contains ${ipPool.size} elements, not $poolSize")
  }
  
  test("The IPs in the pool are properly formatted") {
    check {
      val ip = ipPool.draw().split("\\.")
      ip.length == 4 && ip.take(3).map(_.toInt).forall(n => n >=0 && n < 256)
    }
  }
  
  test("Different items in the pool represent different IPs") {
    val gen = for {
      n <- Gen.choose(0, poolSize)
      m <- Gen.choose(0, poolSize)
    } yield (m, n)
    
    check {
      forAll(gen) {
        case (m, n) => (ipPool.get(m) equals ipPool.get(n)) == (m == n)
      }
    }
  }
  
  test("Session produces a sequence of different numbers") {
    val gen = for {
      t <- Gen.choose(0L, 100000000L)
      n <- Gen.choose(1, 20)
      m <- Gen.choose(1, 2000)
    } yield (t, m, n)
    
    check(
      forAll(gen) {
        case (t, m, n) =>
          (Session("", t, m, n).end == t) == (n == 1)
      }
    )
  }
  
  test("Session generator produces a sequence of correct length") {
    val gen = for {
      t <- Gen.choose(0L, 100000000L)
      m <- Gen.choose(1, 20)
    } yield (t, m)
    
    check(
      forAll(gen) {
        case (t, n) =>
          val sess = Session("", t, 3, n)
          var count = 0
          
          while (sess.hasNext) {
            count += 1
            sess.next
          }
          
          count == n
      }
    )
  }
}
