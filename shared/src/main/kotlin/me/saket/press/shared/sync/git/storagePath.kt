package me.saket.press.shared.sync.git

actual fun storagePath(): String {
  return java.io.File(".").absolutePath
}
