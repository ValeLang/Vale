package dev.vale.parsing

import dev.vale.lexing.SymbolLE

object ParseUtils {

  // This method modifies the current iterator to skip it past the next = symbol
  // that's surrounded by spaces. Note that it won't catch an = at the beginning or
  // end of the statement.
  // It returns None if there wasn't one (which leaves self untouched) or a Some
  // containing everything we skipped past (minus the =).
  def trySkipPastEqualsWhile(iter: ScrambleIterator, continueWhile: ScrambleIterator => Boolean): Option[ScrambleIterator] = {
    val scoutingIter = iter.clone()
    while (continueWhile(scoutingIter)) {
      scoutingIter.peek(3) match {
        case Array(Some(prev), Some(SymbolLE(range, '=')), Some(next)) => {
          val surroundedBySpaces =
            prev.range.end < range.begin && range.end < next.range.begin
          if (surroundedBySpaces) {
            // We'll return this iterator for the things that come before the =
            val beforeIter = iter.clone()
            beforeIter.end = scoutingIter.index + 1

            // Now modify self to skip past it.
            iter.skipTo(scoutingIter)
            iter.advance()
            iter.advance()

            return Some(beforeIter)
          }
        }
        case _ =>
      }
      scoutingIter.advance()
    }

    return None
  }

}
