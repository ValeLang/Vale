package net.verdagon.vale.vivem

import net.verdagon.vale.metal.ShareH
import net.verdagon.vale.vassert

object VivemExterns {
  def panic(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 0)
    throw new PanicException()
  }

  def addIntInt(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aReferend = memory.dereference(args(0))
    val bReferend = memory.dereference(args(1))
    (aReferend, bReferend) match {
      case (IntV(aValue), IntV(bValue)) => {
        memory.addAllocationForReturn(ShareH, IntV(aValue + bValue))
      }
    }
  }

  def addFloatFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aReferend = memory.dereference(args(0))
    val bReferend = memory.dereference(args(1))
    (aReferend, bReferend) match {
      case (FloatV(aValue), FloatV(bValue)) => {
        memory.addAllocationForReturn(ShareH, FloatV(aValue + bValue))
      }
    }
  }

  def multiplyIntInt(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aReferend = memory.dereference(args(0))
    val bReferend = memory.dereference(args(1))
    (aReferend, bReferend) match {
      case (IntV(aValue), IntV(bValue)) => {
        memory.addAllocationForReturn(ShareH, IntV(aValue * bValue))
      }
    }
  }

  def multiplyFloatFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aReferend = memory.dereference(args(0))
    val bReferend = memory.dereference(args(1))
    (aReferend, bReferend) match {
      case (FloatV(aValue), FloatV(bValue)) => {
        memory.addAllocationForReturn(ShareH, FloatV(aValue * bValue))
      }
    }
  }

  def mod(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aReferend = memory.dereference(args(0))
    val bReferend = memory.dereference(args(1))
    (aReferend, bReferend) match {
      case (IntV(aValue), IntV(bValue)) => {
        memory.addAllocationForReturn(ShareH, IntV(aValue % bValue))
      }
    }
  }

  def subtractIntInt(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aReferend = memory.dereference(args(0))
    val bReferend = memory.dereference(args(1))
    (aReferend, bReferend) match {
      case (IntV(aValue), IntV(bValue)) => {
        memory.addAllocationForReturn(ShareH, IntV(aValue - bValue))
      }
    }
  }

  def subtractFloatFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aReferend = memory.dereference(args(0))
    val bReferend = memory.dereference(args(1))
    (aReferend, bReferend) match {
      case (FloatV(aValue), FloatV(bValue)) => {
        memory.addAllocationForReturn(ShareH, FloatV(aValue - bValue))
      }
    }
  }

  def addStrStr(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aReferend = memory.dereference(args(0))
    val bReferend = memory.dereference(args(1))
    (aReferend, bReferend) match {
      case (StrV(aValue), StrV(bValue)) => {
        memory.addAllocationForReturn(ShareH, StrV(aValue + bValue))
      }
    }
  }

  def getch(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.isEmpty)
    val next = memory.stdin()
    val code = if (next.isEmpty) { 0 } else { next.charAt(0).charValue().toInt }
    memory.addAllocationForReturn(ShareH, IntV(code))
  }

  def lessThanInt(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aReferend = memory.dereference(args(0))
    val bReferend = memory.dereference(args(1))
    (aReferend, bReferend) match {
      case (IntV(aValue), IntV(bValue)) => {
        memory.addAllocationForReturn(ShareH, BoolV(aValue < bValue))
      }
    }
  }

  def lessThanFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aReferend = memory.dereference(args(0))
    val bReferend = memory.dereference(args(1))
    (aReferend, bReferend) match {
      case (FloatV(aValue), FloatV(bValue)) => {
        memory.addAllocationForReturn(ShareH, BoolV(aValue < bValue))
      }
    }
  }

  def greaterThanFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aReferend = memory.dereference(args(0))
    val bReferend = memory.dereference(args(1))
    (aReferend, bReferend) match {
      case (FloatV(aValue), FloatV(bValue)) => {
        memory.addAllocationForReturn(ShareH, BoolV(aValue > bValue))
      }
    }
  }

  def lessThanOrEqInt(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aReferend = memory.dereference(args(0))
    val bReferend = memory.dereference(args(1))
    (aReferend, bReferend) match {
      case (IntV(aValue), IntV(bValue)) => {
        memory.addAllocationForReturn(ShareH, BoolV(aValue <= bValue))
      }
    }
  }

  def greaterThanInt(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aReferend = memory.dereference(args(0))
    val bReferend = memory.dereference(args(1))
    (aReferend, bReferend) match {
      case (IntV(aValue), IntV(bValue)) => {
        memory.addAllocationForReturn(ShareH, BoolV(aValue > bValue))
      }
    }
  }

  def greaterThanOrEqInt(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aReferend = memory.dereference(args(0))
    val bReferend = memory.dereference(args(1))
    (aReferend, bReferend) match {
      case (IntV(aValue), IntV(bValue)) => {
        memory.addAllocationForReturn(ShareH, BoolV(aValue >= bValue))
      }
    }
  }

  def eqIntInt(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aReferend = memory.dereference(args(0))
    val bReferend = memory.dereference(args(1))
    (aReferend, bReferend) match {
      case (IntV(aValue), IntV(bValue)) => {
        memory.addAllocationForReturn(ShareH, BoolV(aValue == bValue))
      }
    }
  }

  def eqStrStr(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aReferend = memory.dereference(args(0))
    val bReferend = memory.dereference(args(1))
    (aReferend, bReferend) match {
      case (StrV(aValue), StrV(bValue)) => {
        memory.addAllocationForReturn(ShareH, BoolV(aValue == bValue))
      }
    }
  }

  def eqBoolBool(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aReferend = memory.dereference(args(0))
    val bReferend = memory.dereference(args(1))
    (aReferend, bReferend) match {
      case (BoolV(aValue), BoolV(bValue)) => {
        memory.addAllocationForReturn(ShareH, BoolV(aValue == bValue))
      }
    }
  }

  def and(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 2)
    val aReferend = memory.dereference(args(0))
    val bReferend = memory.dereference(args(1))
    (aReferend, bReferend) match {
      case (BoolV(aValue), BoolV(bValue)) => {
        memory.addAllocationForReturn(ShareH, BoolV(aValue && bValue))
      }
    }
  }

  def not(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val BoolV(value) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, BoolV(!value))
  }

  def sqrt(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val FloatV(value) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, FloatV(Math.sqrt(value).toFloat))
  }

  def castIntStr(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val IntV(value) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, StrV(value.toString))
  }

  def castFloatStr(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val FloatV(value) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, StrV(value.toString))
  }

  def castIntFloat(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    val IntV(value) = memory.dereference(args(0))
    memory.addAllocationForReturn(ShareH, FloatV(value.toFloat))
  }

  def print(memory: AdapterForExterns, args: Vector[ReferenceV]): ReferenceV = {
    vassert(args.size == 1)
    memory.dereference(args(0)) match {
      case StrV(value) => {
        memory.stdout(value)
      }
    }
    memory.makeVoid()
  }
}
