package okhttp3.internal.http1

import okio.BufferedSink
import okio.BufferedSource

interface Streams {
  val source: BufferedSource
  val sink: BufferedSink

  fun cancel()
}
