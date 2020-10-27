package me.saket.press.shared.sync.git

import pw.binom.ByteBuffer
import pw.binom.ByteBufferPool
import pw.binom.asUTF8String
import pw.binom.copyTo
import pw.binom.io.ByteArrayOutput
import pw.binom.io.file.isExist
import pw.binom.io.file.mkdirs
import pw.binom.io.file.name
import pw.binom.io.file.parent
import pw.binom.io.file.read
import pw.binom.io.file.write
import pw.binom.io.use
import pw.binom.pool.ObjectPool
import pw.binom.toByteBufferUTF8

/**
 * Really hoping that this can use Okio in the future because everything else is quite... bad.
 * [https://publicobject.com/2020/10/06/files/]
 */
internal class BinomBasedFile(val delegate: pw.binom.io.file.File): File {
  constructor(path: String) : this(pw.binom.io.file.File(path))

  constructor(parent: File, name: String) : this(
      pw.binom.io.file.File(
          parent = pw.binom.io.file.File(parent.path),
          name = name
      )
  )

  override val exists: Boolean get() = delegate.isExist
  override val path: String get() = delegate.path
  override val name: String get() = delegate.name
  override val parent: File? get() = delegate.parent?.let(::BinomBasedFile)
  override val isDirectory: Boolean get() = delegate.isDirectory

  companion object {
    val byteBufferPool = ByteBufferPool(10)
  }

  override fun write(input: String) {
    parent?.let { check(it.exists) }

    val data: ByteBuffer = input.toByteBufferUTF8()
    delegate.write().use {
      it.write(data)
      it.flush()
    }
    data.clear()
  }

  override fun read(): String {
    check(exists) { "Can't read non-existent: $path" }

    val buffer = byteBufferPool.borrow()
    try {
      // Bug workaround: Input.copyTo doesn't recycle borrowed buffers from the pool.
      val singleItemPool = object : ObjectPool<ByteBuffer> {
        override fun borrow(init: ((ByteBuffer) -> Unit)?) = buffer
        override fun recycle(value: ByteBuffer) = error("nope")
      }

      ByteArrayOutput().use { out ->
        delegate.read().use {
          it.copyTo(out, singleItemPool)
        }
        out.trimToSize()
        out.data.clear()
        return out.data.asUTF8String()
      }
    } finally {
      byteBufferPool.recycle(buffer)
    }
  }

  override fun makeDirectory(recursively: Boolean) {
    if (recursively) {
      delegate.mkdirs()
    } else {
      delegate.mkdir()
    }
  }

  override fun delete() {
    check(exists) { "$name does not exist: $path" }
    check(delegate.delete()) { "Failed to delete file: $this" }
  }

  override fun sizeInBytes(): Long {
    check(exists)
    check(!isDirectory)
    return delegate.size
  }

  override fun children(): List<File> {
    check(exists)
    check(delegate.isDirectory)

    val children = delegate.list()
    return children.map(::BinomBasedFile)
  }

  /** @return the same [newFile] for convenience. */
  override fun renameTo(newFile: File): File {
    check(this.path != newFile.path) { "Same path: $path vs ${newFile.path}" }
    check(this.exists) { "$path doesn't exist" }
    check(!newFile.exists) { "${newFile.path} already exists!" }

    if (!newFile.parent!!.exists) {
      newFile.parent!!.makeDirectory(recursively = true)
    }

    val renamed = delegate.renameTo(pw.binom.io.file.File(newFile.path))
    check(renamed) { "Couldn't rename ($this) to $newFile" }
    return newFile
  }

  /**
   * Like `content == read()`, but the plan is to avoid reading the whole file
   * into memory in the future when Okio supports native platforms.
   */
  override fun equalsContent(content: String): Boolean {
    return read() == content
  }

  override fun toString(): String {
    return "${delegate.name} (${delegate.path})"
  }
}
