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

class Interner {
  val map = new BigHashMap[MemberWiseEqualsWrapper, Any](100000)
  def intern[X <: IInterning](x: X): X = {
    map.get(MemberWiseEqualsWrapper(x)) match {
      case Some(original) => return original.asInstanceOf[X]
      case None =>
    }
    // If we get here, we need to insert it.
    x.uniqueId = new InterningId(map.size + 1)
    map.put(MemberWiseEqualsWrapper(x), x)
    x
  }
}


// "String Interned"
case class StrI(str: String) extends IInterning