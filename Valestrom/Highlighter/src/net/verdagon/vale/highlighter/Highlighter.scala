package net.verdagon.vale.highlighter

import net.verdagon.vale.parser._
import net.verdagon.vale.{vassert, vfail}

object Highlighter {
  def min(positions: List[Pos]): Pos = {
    positions match {
      case List() => vfail()
      case List(p) => p
      case head :: tail => {
        val minOfTail = min(tail)
        if (head < minOfTail) { head }
        else { minOfTail }
      }
    }
  }

  class CodeIter(code: String) {
    var index = 0
    var line = 1
    var col = 1
    def pos = Pos(line, col)

    private def advance(): Unit = {
      index = index + 1
      col = col + 1
      if (code.charAt(index - 1) == '\n') {
        line = line + 1
        col = 1
      }
    }

    def advanceTo(untilPos: Pos, untilIndex: Int): String = {
      val indexBefore = index
      while (pos < untilPos && index < code.length && index < untilIndex) {
        advance()
      }
      val indexAfter = index
      code.substring(indexBefore, indexAfter)
    }
  }

  class CommentingCodeIter(code: String, var commentRanges: List[(Int, Int)], builder: StringBuilder) {
    val iter = new CodeIter(code)

    // Advances the underlying CodeIter until we get to untilPos, but also
    // stopping at any comments along the way to add them to builder.
    // Will keep resuming until we hit untilPos.
    def advanceTo(untilPos: Pos): Unit = {
      while (iter.index < code.length && iter.pos < untilPos) {
        if (commentRanges.isEmpty) {
          builder.append(escape(iter.advanceTo(untilPos, Int.MaxValue)))
        } else {
          builder.append(escape(iter.advanceTo(untilPos, commentRanges.head._1)))
        }

        // If we're at the beginning of the next comment, consume it.
        while (iter.index < code.length && commentRanges.nonEmpty && iter.index == commentRanges.head._1) {
          builder.append(s"""<span class="${Comment}">""")
          builder.append(escape(iter.advanceTo(Pos(Int.MaxValue, Int.MaxValue), commentRanges.head._2)))
          vassert(iter.index == commentRanges.head._2)
          builder.append(s"""</span>""")
          commentRanges = commentRanges.tail
        }
      }
    }
  }

  def toHTML(builder: StringBuilder, iter: CommentingCodeIter, span: Span): Unit = {
    iter.advanceTo(span.range.begin)
    builder.append(s"""<span class="${span.classs}">""")
    span.children.foreach(child => {
      iter.advanceTo(child.range.begin)
      toHTML(builder, iter, child)
      iter.advanceTo(child.range.end)
    })
    iter.advanceTo(span.range.end)
    builder.append("</span>")
  }

  def escape(s: String): String = {
    s
      .replaceAll("\\{", "&#123;")
      .replaceAll("\\}", "&#125;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll("\\n", "<br />")
  }

  def toHTML(code: String, span: Span, commentRanges: List[(Int, Int)]): String = {
    val builder = new StringBuilder()
    val iter = new CommentingCodeIter(code, commentRanges, builder)
    toHTML(builder, iter, span)
    builder.toString()
  }
}
