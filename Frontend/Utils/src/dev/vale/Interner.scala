package dev.vale

import java.lang
import java.lang.Object
import scala.collection.mutable

class InterningId(val uniqueId: Long) {
  override def equals(obj: Any): Boolean = {
    true
  }
  override def hashCode(): Int = vwat()
}

trait IInterning extends Product {
  val hash = runtime.ScalaRunTime._hashCode(this);
  override def hashCode(): Int = hash

  var uniqueId = new InterningId(0L) // 0 means not interned

  def uid: Long = uniqueId.uniqueId

  override def equals(obj: Any): Boolean = {
    obj match {
      case that : IInterning => {
        if (uniqueId.uniqueId == 0) {
          vfail("Forgot to intern: " + this)
        }
        if (that.uniqueId.uniqueId == 0) {
          vfail("Forgot to intern: " + that)
        }
        uniqueId.uniqueId == that.uniqueId.uniqueId
      }
      case _ => false
    }
  }

  // This should only be used while interning, because we don't
  // have a unique ID yet.
  def longEquals(thatObj: Any): Boolean = {
    // Since uniqueId is an InterningId, which overrides .equals(),
    // it doesnt matter that thatObj.uniqueId is nonzero and this.uniqueId is zero.
    // InterningId.equals will always return true.
    getClass == thatObj.getClass &&
      productIterator.sameElements(thatObj.asInstanceOf[Product].productIterator)
  }

  // Can be overridden, such as by StrI.
  //
  def shortcutNewInterningId(): Long = {
    0
  }
}

// Normally, an IInterning subclass will just use the uniqueId to perform
// its .equals() check.
// However, that is very inconvenient when we actually *are* trying to
// do a real member-wise equals comparison, such as when we're seeing
// if a new instance has an equivalent instance already interned.
// So, we use this wrapper when we want to bypass the uniqueId shortcut.
case class MemberWiseEqualsWrapper(x: IInterning) {
  override def hashCode(): Int = {
    x.hashCode()
  }
  override def equals(obj: Any): Boolean = {
    obj match {
      case MemberWiseEqualsWrapper(that) => {
        x.longEquals(that)
      }
      case _ => vwat()
    }
  }
}

class BigHashMap[A, B](initSize : Int) extends mutable.HashMap[A, B] {
  override def initialSize: Int = initSize // 16 - by default
}

object Interner {
  // Anything under this is reserved for short strings, strings of max length 7.
  // See {StrI.makeInterningId} for why the +1.
  val MIN_INTERNING_ID: Long = (1L << (7 * 8)) + 1L
}
class Interner {
  val map = new BigHashMap[MemberWiseEqualsWrapper, Any](10000)
  def intern[X <: IInterning](x: X): X = {
    val shortcutInterningID = x.shortcutNewInterningId()
    if (shortcutInterningID != 0) {
      x.uniqueId = new InterningId(shortcutInterningID)
      return x
    }
    map.get(MemberWiseEqualsWrapper(x)) match {
      case Some(original) => return original.asInstanceOf[X]
      case None =>
    }
    // If we get here, we need to insert it.
    x.uniqueId = new InterningId(map.size + Interner.MIN_INTERNING_ID)
    map.put(MemberWiseEqualsWrapper(x), x)
    x
  }
}
// "String Interned"
case class StrI(str: String) extends IInterning {
  override def toString: String = str
  override def shortcutNewInterningId(): Long = {
    if (str.length > 8) {
      return 0
    }
    val buffer = new Array[Char](8)
    str.getChars(0, str.length, buffer, 0)
    val char0: Long = buffer(0)
    val char1: Long = buffer(1)
    val char2: Long = buffer(2)
    val char3: Long = buffer(3)
    val char4: Long = buffer(4)
    val char5: Long = buffer(5)
    val char6: Long = buffer(6)
    val char7: Long = buffer(7)
    if (char0 >= 128 ||
      char1 >= 128 ||
      char2 >= 128 ||
      char3 >= 128 ||
      char4 >= 128 ||
      char5 >= 128 ||
      char6 >= 128 ||
      char7 >= 128) {
      return 0
    }
    // We do +1 so that we don't collide with 0, which already means not interned.
    val total: Long =
      1L + ((char0 << (7 * 7)) |
        (char1 << (7L * 6L)) |
        (char2 << (7L * 5L)) |
        (char3 << (7L * 4L)) |
        (char4 << (7L * 3L)) |
        (char5 << (7L * 2L)) |
        (char6 << (7L * 1L)) |
        (char7 << (7L * 0L)));
    vassert(total < Interner.MIN_INTERNING_ID)
    total
  }
}

//// These can be used to test out how fast the compiler is without interning.
//// When just measuring the parser, it's 3% slower without interning.
//trait IInterning extends Product { }
//class Interner {
//  def intern[X <: IInterning](x: X): X = {
//    x
//  }
//}
//case class StrI(str: String) extends IInterning {
//}