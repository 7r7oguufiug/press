package me.saket.press.shared.sync.git

import com.soywiz.korio.async.runBlockingNoSuspensions
import com.soywiz.korio.file.VfsFile
import com.soywiz.korio.file.baseName
import com.soywiz.korio.file.std.LocalVfs
import pw.binom.io.file.name

expect fun storagePath(): String

class KorioBasedFile(path: String) : File {
  private val fs: VfsFile = LocalVfs[storagePath()]
  private val file = fs[path]

  override val exists: Boolean
    get() {
      return runBlockingNoSuspensions {
        file.exists()
      }
    }
  override val path: String
    get() = file.path
  override val name: String
    get() = file.baseName
  override val parent: File?
    get() = file.parent.path.let(::KorioBasedFile)
  override val isDirectory: Boolean
    get() = runBlockingNoSuspensions { file.isDirectory() }

  override fun write(input: String) {
    runBlockingNoSuspensions {
      file.writeString(input)
    }
  }

  override fun read(): String {
    return runBlockingNoSuspensions {
      file.readString()
    }
  }

  override fun makeDirectory(recursively: Boolean) {
    runBlockingNoSuspensions {
      file.mkdir()
    }
  }

  override fun delete() {
    runBlockingNoSuspensions {
      file.delete()
    }
  }

  override fun sizeInBytes(): Long {
    return runBlockingNoSuspensions {
      file.size()
    }
  }

  override fun children(): List<File> {
    return runBlockingNoSuspensions {
      file.listSimple().map { KorioBasedFile(it.path) }
    }
  }

  override fun renameTo(newFile: File): File {
    return runBlockingNoSuspensions {
      val target = fs[newFile.path]
      target.ensureParents()

      val renamed = target.renameTo(target.path)
      check(renamed) { "Couldn't rename ($this) to $target" }
      KorioBasedFile(target.path)
    }
  }

  override fun equalsContent(content: String): Boolean {
    return runBlockingNoSuspensions {
      file.readString() == content
    }
  }

  override fun toString(): String {
    return "$name ($path)"
  }
}
