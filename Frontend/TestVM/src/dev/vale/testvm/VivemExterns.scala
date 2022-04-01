package dev.vale.testvm

import dev.vale.finalast.{InlineH, ShareH, YonderH}
import dev.vale.{vassert, vfail}

import java.lang.ArithmeticException
import dev.vale.finalast.YonderH
import dev.vale.vfail

object VivemExterns {
  def panic(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 0)
    throw new PanicException()
  }

  def addFloatFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (FloatV(aValue), FloatV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, FloatV(aValue + bValue))
      }
    }
  }

  def multiplyFloatFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (FloatV(aValue), FloatV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, FloatV(aValue * bValue))
      }
    }
  }

  def divideFloatFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (FloatV(aValue), FloatV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, FloatV(aValue / bValue))
      }
    }
  }

  def subtractFloatFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (FloatV(aValue), FloatV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, FloatV(aValue - bValue))
      }
    }
  }

  def addStrStr(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 6)
    val StrV(aStr) = memory.dereference(args(0))
    val IntV(aBegin, 32) = memory.dereference(args(1))
    val IntV(aLength, 32) = memory.dereference(args(2))
    val StrV(bStr) = memory.dereference(args(3))
    val IntV(bBegin, 32) = memory.dereference(args(4))
    val IntV(bLength, 32) = memory.dereference(args(5))
    memory.addAllocationForReturn(ShareH, YonderH, StrV(aStr.substring(aBegin.toInt, aBegin.toInt + aLength.toInt) + bStr.substring(bBegin.toInt, bBegin.toInt + bLength.toInt)))
  }

  def getch(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.isEmpty)
    val next = memory.stdin()
    val code = if (next.isEmpty) { 0 } else { next.charAt(0).charValue().toInt }
    memory.addAllocationForReturn(ShareH, InlineH, IntV(code, 32))
  }

  def lessThanFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (FloatV(aValue), FloatV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, BoolV(aValue < bValue))
      }
    }
  }

  def greaterThanFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (FloatV(aValue), FloatV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, BoolV(aValue > bValue))
      }
    }
  }

  def eqFloatFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (FloatV(aValue), FloatV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, BoolV(aValue == bValue))
      }
    }
  }

  def eqStrStr(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 6)
    val StrV(leftStr) = memory.dereference(args(0))
    val IntV(leftStrStart, 32) = memory.dereference(args(1))
    val IntV(leftStrLen, 32) = memory.dereference(args(2))
    val StrV(rightStr) = memory.dereference(args(3))
    val IntV(rightStrStart, 32) = memory.dereference(args(4))
    val IntV(rightStrLen, 32) = memory.dereference(args(5))
    val result = BoolV(leftStr.slice(leftStrStart.toInt, leftStrLen.toInt) == rightStr.slice(rightStrStart.toInt, rightStrLen.toInt))
    memory.addAllocationForReturn(ShareH, InlineH, result)
  }

  def eqBoolBool(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (BoolV(aValue), BoolV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, BoolV(aValue == bValue))
      }
    }
  }

  def and(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (BoolV(aValue), BoolV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, BoolV(aValue && bValue))
      }
    }
  }

  def or(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (BoolV(aValue), BoolV(bValue)) => {
        memory.addAllocationForReturn(ShareH, InlineH, BoolV(aValue || bValue))
      }
    }
  }

  def not(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val BoolV(value) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, InlineH, BoolV(!value))
  }

  def sqrt(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val FloatV(value) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, InlineH, FloatV(Math.sqrt(value).toFloat))
  }

  def strLength(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val StrV(value) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, InlineH, IntV(value.length, 32))
  }

  def castFloatStr(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val FloatV(value) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, YonderH, StrV(value.toString))
  }

  def negateFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val FloatV(value) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, InlineH, FloatV(-value))
  }

  def print(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 3)
    val StrV(aStr) = memory.dereference(args(0))
    val IntV(aBegin, 32) = memory.dereference(args(1))
    val IntV(aLength, 32) = memory.dereference(args(2))
    memory.stdout(aStr.substring(aBegin.toInt, aBegin.toInt + aLength.toInt))
    memory.makeVoid()
  }

  def addI32(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue, 32), IntV(bValue, 32)) => {
        memory.addAllocationForReturn(ShareH, InlineH, IntV(aValue.toInt + bValue.toInt, 32))
      }
    }
  }

  def multiplyI32(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue, 32), IntV(bValue, 32)) => {
        memory.addAllocationForReturn(ShareH, InlineH, IntV(aValue.toInt * bValue.toInt, 32))
      }
    }
  }

  def divideI32(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue, 32), IntV(bValue, 32)) => {
        memory.addAllocationForReturn(ShareH, InlineH, IntV(aValue.toInt / bValue.toInt, 32))
      }
    }
  }

  def modI32(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue, 32), IntV(bValue, 32)) => {
        try {
          memory.addAllocationForReturn(ShareH, InlineH, IntV(aValue.toInt % bValue.toInt, 32))
        } catch {
          case _ : ArithmeticException => vfail()
        }
      }
    }
  }

  def subtractI32(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue, 32), IntV(bValue, 32)) => {
        memory.addAllocationForReturn(ShareH, InlineH, IntV(aValue.toInt - bValue.toInt, 32))
      }
    }
  }

  def lessThanI32(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue, 32), IntV(bValue, 32)) => {
        memory.addAllocationForReturn(ShareH, InlineH, BoolV(aValue < bValue))
      }
    }
  }

  def lessThanOrEqI32(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue, 32), IntV(bValue, 32)) => {
        memory.addAllocationForReturn(ShareH, InlineH, BoolV(aValue <= bValue))
      }
    }
  }

  def greaterThanI32(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue, 32), IntV(bValue, 32)) => {
        memory.addAllocationForReturn(ShareH, InlineH, BoolV(aValue > bValue))
      }
    }
  }

  def greaterThanOrEqI32(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue, 32), IntV(bValue, 32)) => {
        memory.addAllocationForReturn(ShareH, InlineH, BoolV(aValue >= bValue))
      }
    }
  }

  def eqI32(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue, 32), IntV(bValue, 32)) => {
        memory.addAllocationForReturn(ShareH, InlineH, BoolV(aValue == bValue))
      }
    }
  }

  def castI32Str(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val IntV(value, 32) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, YonderH, StrV(value.toString))
  }

  def castFloatI32(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val FloatV(value) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, InlineH, IntV(value.toInt, 32))
  }

  def castI32Float(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val IntV(value, 32) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, InlineH, FloatV(value.toFloat))
  }

  def addI64(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue, 64), IntV(bValue, 64)) => {
        memory.addAllocationForReturn(ShareH, InlineH, IntV(aValue + bValue, 64))
      }
    }
  }

  def multiplyI64(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue, 64), IntV(bValue, 64)) => {
        memory.addAllocationForReturn(ShareH, InlineH, IntV(aValue * bValue, 64))
      }
    }
  }

  def divideI64(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue, 64), IntV(bValue, 64)) => {
        memory.addAllocationForReturn(ShareH, InlineH, IntV(aValue / bValue, 64))
      }
    }
  }

  def truncateI64ToI32(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val IntV(value, 64) = memory.dereference(args(0))
    val result = value & 0xFFFFFFFFL
    memory.addAllocationForReturn(ShareH, InlineH, IntV(result, 32))
  }

  def modI64(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue, 64), IntV(bValue, 64)) => {
        try {
          memory.addAllocationForReturn(ShareH, InlineH, IntV(aValue % bValue, 64))
        } catch {
          case _ : ArithmeticException => vfail()
        }
      }
    }
  }

  def subtractI64(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue, 64), IntV(bValue, 64)) => {
        memory.addAllocationForReturn(ShareH, InlineH, IntV(aValue - bValue, 64))
      }
    }
  }

  def lessThanI64(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue, 64), IntV(bValue, 64)) => {
        memory.addAllocationForReturn(ShareH, InlineH, BoolV(aValue < bValue))
      }
    }
  }

  def lessThanOrEqI64(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue, 64), IntV(bValue, 64)) => {
        memory.addAllocationForReturn(ShareH, InlineH, BoolV(aValue <= bValue))
      }
    }
  }

  def greaterThanI64(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue, 64), IntV(bValue, 64)) => {
        memory.addAllocationForReturn(ShareH, InlineH, BoolV(aValue > bValue))
      }
    }
  }

  def greaterThanOrEqI64(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue, 64), IntV(bValue, 64)) => {
        memory.addAllocationForReturn(ShareH, InlineH, BoolV(aValue >= bValue))
      }
    }
  }

  def eqI64(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aKind = memory.dereference(args(0))
    val bKind = memory.dereference(args(1))
    (aKind, bKind) match {
      case (IntV(aValue, 64), IntV(bValue, 64)) => {
        memory.addAllocationForReturn(ShareH, InlineH, BoolV(aValue == bValue))
      }
    }
  }

  def castI64Str(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val IntV(value, 64) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, YonderH, StrV(value.toString))
  }

  def castFloatI64(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val FloatV(value) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, InlineH, IntV(value.toInt, 64))
  }

  def castI64Float(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val IntV(value, 64) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, InlineH, FloatV(value.toFloat))
  }
}
