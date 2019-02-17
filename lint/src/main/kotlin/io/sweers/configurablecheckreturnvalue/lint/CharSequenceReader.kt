/*
 * Copyright (C) 2013 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.sweers.configurablecheckreturnvalue.lint

import com.google.common.base.Preconditions.checkPositionIndexes
import java.io.IOException
import java.io.Reader
import java.nio.CharBuffer

/** Creates a new reader wrapping the given [this] character sequence. */
internal fun CharSequence.reader(): Reader = CharSequenceReader(this)

/**
 * A [Reader] that reads the characters in a [CharSequence]. Like `StringReader`,
 * but works with any [CharSequence].
 *
 * @author Colin Decker
 */
private class CharSequenceReader(seq: CharSequence) : Reader() {

  private var _seq: CharSequence? = null
  private var pos: Int = 0
  private var mark: Int = 0

  init {
    this._seq = seq
  }

  private inline fun <T> guardedRead(body: (seq: CharSequence) -> T): T {
    return _seq?.let {
      body(it)
    } ?: throw IOException("reader closed")
  }

  private fun hasRemaining(): Boolean {
    return remaining() > 0
  }

  private fun remaining(): Int {
    return guardedRead { it.length - pos }
  }

  @Synchronized
  @Throws(IOException::class)
  override fun read(target: CharBuffer): Int {
    checkNotNull(target)
    return guardedRead { seq ->
      if (!hasRemaining()) {
        return@guardedRead -1
      }
      val charsToRead = Math.min(target.remaining(), remaining())
      for (i in 0 until charsToRead) {
        target.put(seq[pos++])
      }
      charsToRead
    }
  }

  @Synchronized
  @Throws(IOException::class)
  override fun read(): Int {
    return guardedRead { if (hasRemaining()) it[pos++].toInt() else -1 }
  }

  @Synchronized
  @Throws(IOException::class)
  override fun read(cbuf: CharArray, off: Int, len: Int): Int {
    checkPositionIndexes(off, off + len, cbuf.size)
    return guardedRead { seq ->
      if (!hasRemaining()) {
        return@guardedRead -1
      }
      val charsToRead = Math.min(len, remaining())
      for (i in 0 until charsToRead) {
        cbuf[off + i] = seq[pos++]
      }
      charsToRead
    }
  }

  @Synchronized
  @Throws(IOException::class)
  override fun skip(n: Long): Long {
    check(n >= 0) {
      "n ($n) may not be negative"
    }
    return guardedRead {
      val charsToSkip = Math.min(remaining().toLong(),
          n).toInt() // safe because remaining is an int
      pos += charsToSkip
      return@guardedRead charsToSkip.toLong()
    }
  }

  @Synchronized
  @Throws(IOException::class)
  override fun ready(): Boolean {
    return guardedRead { true }
  }

  override fun markSupported(): Boolean {
    return true
  }

  @Synchronized
  @Throws(IOException::class)
  override fun mark(readAheadLimit: Int) {
    check(readAheadLimit >= 0) {
      "readAheadLimit ($readAheadLimit) may not be negative"
    }
    guardedRead {
      mark = pos
    }
  }

  @Synchronized
  @Throws(IOException::class)
  override fun reset() {
    guardedRead {
      pos = mark
    }
  }

  @Synchronized
  @Throws(IOException::class)
  override fun close() {
    _seq = null
  }
}
