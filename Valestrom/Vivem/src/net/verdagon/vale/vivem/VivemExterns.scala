package net.verdagon.vale.vivem

import java.lang.ArithmeticException
import net.verdagon.vale.metal.{InlineH, ReadonlyH, ShareH, YonderH}
import net.verdagon.vale.{vassert, vfail}

object VivemExterns {
  def panic(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 0)
    throw new PanicException()
  }

  def addIntInt(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue), IntV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, IntV(aValue + bValue))
      }
    }
  }

  def addFloatFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (FloatV(aValue), FloatV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, FloatV(aValue + bValue))
      }
    }
  }

  def multiplyIntInt(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue), IntV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, IntV(aValue * bValue))
      }
    }
  }

  def divideIntInt(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue), IntV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, IntV(aValue / bValue))
      }
    }
  }

  def multiplyFloatFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (FloatV(aValue), FloatV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, FloatV(aValue * bValue))
      }
    }
  }

  def divideFloatFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (FloatV(aValue), FloatV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, FloatV(aValue / bValue))
      }
    }
  }

  def mod(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue), IntV(bValue)) => {
        try {
          memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, IntV(aValue % bValue))
        } catch {
          case _ : ArithmeticException => vfail()
        }
      }
    }
  }

  def subtractIntInt(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue), IntV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, IntV(aValue - bValue))
      }
    }
  }

  def subtractFloatFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (FloatV(aValue), FloatV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, FloatV(aValue - bValue))
      }
    }
  }

  def addStrStr(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 6)
    val StrV(aStr) = memory.dereference(args(0))
    val IntV(aBegin) = memory.dereference(args(1))
    val IntV(aLength) = memory.dereference(args(2))
    val StrV(bStr) = memory.dereference(args(3))
    val IntV(bBegin) = memory.dereference(args(4))
    val IntV(bLength) = memory.dereference(args(5))
    memory.addAllocationForReturn(ShareH, YonderH, ReadonlyH, StrV(aStr.substring(aBegin, aBegin + aLength) + bStr.substring(bBegin, bBegin + bLength)))
  }

  def getch(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.isEmpty)
    val next = memory.stdin()
    val code = if (next.isEmpty) { 0 } else { next.charAt(0).charValue().toInt }
    memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, IntV(code))
  }

  def lessThanInt(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue), IntV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, BoolV(aValue < bValue))
      }
    }
  }

  def lessThanFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (FloatV(aValue), FloatV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, BoolV(aValue < bValue))
      }
    }
  }

  def greaterThanFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (FloatV(aValue), FloatV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, BoolV(aValue > bValue))
      }
    }
  }

  def lessThanOrEqInt(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue), IntV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, BoolV(aValue <= bValue))
      }
    }
  }

  def greaterThanInt(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue), IntV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, BoolV(aValue > bValue))
      }
    }
  }

  def greaterThanOrEqInt(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue), IntV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, BoolV(aValue >= bValue))
      }
    }
  }

  def eqIntInt(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue), IntV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, BoolV(aValue == bValue))
      }
    }
  }

  def eqFloatFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (FloatV(aValue), FloatV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, BoolV(aValue == bValue))
      }
    }
  }

  def eqStrStr(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 6)
    val StrV(leftStr) = memory.dereference(args(0))
    val IntV(leftStrStart) = memory.dereference(args(1))
    val IntV(leftStrLen) = memory.dereference(args(2))
    val StrV(rightStr) = memory.dereference(args(3))
    val IntV(rightStrStart) = memory.dereference(args(4))
    val IntV(rightStrLen) = memory.dereference(args(5))
    val result = BoolV(leftStr.slice(leftStrStart, leftStrLen) == rightStr.slice(rightStrStart, rightStrLen))
    memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, result)
  }

  def eqBoolBool(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (BoolV(aValue), BoolV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, BoolV(aValue == bValue))
      }
    }
  }

  def and(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (BoolV(aValue), BoolV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, BoolV(aValue && bValue))
      }
    }
  }

  def or(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (BoolV(aValue), BoolV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, BoolV(aValue || bValue))
      }
    }
  }

  def not(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val BoolV(value) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, BoolV(!value))
  }

  def sqrt(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val FloatV(value) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, FloatV(Math.sqrt(value).toFloat))
  }

  def castIntStr(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val IntV(value) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, YonderH, ReadonlyH, StrV(value.toString))
  }

  def castFloatInt(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val FloatV(value) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, IntV(value.toInt))
  }

  def strLength(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val StrV(value) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, IntV(value.length))
  }

  def castFloatStr(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val FloatV(value) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, YonderH, ReadonlyH, StrV(value.toString))
  }

  def negateFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val FloatV(value) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, FloatV(-value))
  }

  def castIntFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val IntV(value) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, InlineH, ReadonlyH, FloatV(value.toFloat))
  }

  def print(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 3)
    val StrV(aStr) = memory.dereference(args(0))
    val IntV(aBegin) = memory.dereference(args(1))
    val IntV(aLength) = memory.dereference(args(2))
    memory.stdout(aStr.substring(aBegin, aBegin + aLength))
    memory.makeVoid()
  }
}
