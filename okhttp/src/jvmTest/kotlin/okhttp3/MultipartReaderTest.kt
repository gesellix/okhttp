/*
 * Copyright (C) 2020 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okhttp3

import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import java.io.EOFException
import java.net.ProtocolException
import kotlin.test.assertFailsWith
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSink
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("Slowish")
class MultipartReaderTest {
  @Test fun `parse multipart`() {
    val multipart =
      """
      |--simple boundary
      |Content-Type: text/plain; charset=utf-8
      |Content-ID: abc
      |
      |abcd
      |efgh
      |--simple boundary
      |Content-Type: text/plain; charset=utf-8
      |Content-ID: ijk
      |
      |ijkl
      |mnop
      |
      |--simple boundary--
      """.trimMargin()
        .replace("\n", "\r\n")

    val parts =
      MultipartReader(
        boundary = "simple boundary",
        source = Buffer().writeUtf8(multipart),
      )
    assertThat(parts.boundary).isEqualTo("simple boundary")

    val partAbc = parts.nextPart()!!
    assertThat(partAbc.headers).isEqualTo(
      headersOf(
        "Content-Type",
        "text/plain; charset=utf-8",
        "Content-ID",
        "abc",
      ),
    )
    assertThat(partAbc.body.readUtf8()).isEqualTo("abcd\r\nefgh")

    val partIjk = parts.nextPart()!!
    assertThat(partIjk.headers).isEqualTo(
      headersOf(
        "Content-Type",
        "text/plain; charset=utf-8",
        "Content-ID",
        "ijk",
      ),
    )
    assertThat(partIjk.body.readUtf8()).isEqualTo("ijkl\r\nmnop\r\n")

    assertThat(parts.nextPart()).isNull()
  }

  @Test fun `parse from response body`() {
    val multipart =
      """
      |--simple boundary
      |
      |abcd
      |--simple boundary--
      """.trimMargin()
        .replace("\n", "\r\n")

    val responseBody =
      multipart.toResponseBody(
        "application/multipart; boundary=\"simple boundary\"".toMediaType(),
      )

    val parts = MultipartReader(responseBody)
    assertThat(parts.boundary).isEqualTo("simple boundary")

    val part = parts.nextPart()!!
    assertThat(part.body.readUtf8()).isEqualTo("abcd")

    assertThat(parts.nextPart()).isNull()
  }

  @Test fun `truncated multipart`() {
    val multipart =
      """
      |--simple boundary
      |
      |abcd
      |efgh
      |
      """.trimMargin()
        .replace("\n", "\r\n")

    val parts =
      MultipartReader(
        boundary = "simple boundary",
        source = Buffer().writeUtf8(multipart),
      )

    val part = parts.nextPart()!!
    assertFailsWith<EOFException> {
      part.body.readUtf8()
    }

    assertFailsWith<EOFException> {
      assertThat(parts.nextPart()).isNull()
    }
  }

  @Test fun `malformed headers`() {
    val multipart =
      """
      |--simple boundary
      |abcd
      |
      """.trimMargin()
        .replace("\n", "\r\n")

    val parts =
      MultipartReader(
        boundary = "simple boundary",
        source = Buffer().writeUtf8(multipart),
      )

    assertFailsWith<EOFException> {
      parts.nextPart()
    }
  }

  @Test fun `lf instead of crlf boundary is not honored`() {
    val multipart =
      """
      |--simple boundary
      |
      |abcd
      |--simple boundary
      |
      |efgh
      |--simple boundary--
      """.trimMargin()
        .replace("\n", "\r\n")
        .replace(Regex("(?m)abcd\r\n"), "abcd\n")

    val parts =
      MultipartReader(
        boundary = "simple boundary",
        source = Buffer().writeUtf8(multipart),
      )

    val part = parts.nextPart()!!
    assertThat(part.body.readUtf8()).isEqualTo("abcd\n--simple boundary\r\n\r\nefgh")

    assertThat(parts.nextPart()).isNull()
  }

  @Test fun `partial boundary is not honored`() {
    val multipart =
      """
      |--simple boundary
      |
      |abcd
      |--simple boundar
      |
      |efgh
      |--simple boundary--
      """.trimMargin()
        .replace("\n", "\r\n")

    val parts =
      MultipartReader(
        boundary = "simple boundary",
        source = Buffer().writeUtf8(multipart),
      )

    val part = parts.nextPart()!!
    assertThat(part.body.readUtf8()).isEqualTo("abcd\r\n--simple boundar\r\n\r\nefgh")

    assertThat(parts.nextPart()).isNull()
  }

  @Test fun `do not need to read entire part`() {
    val multipart =
      """
      |--simple boundary
      |
      |abcd
      |efgh
      |ijkl
      |--simple boundary
      |
      |mnop
      |--simple boundary--
      """.trimMargin()
        .replace("\n", "\r\n")

    val parts =
      MultipartReader(
        boundary = "simple boundary",
        source = Buffer().writeUtf8(multipart),
      )

    parts.nextPart()!!
    val partMno = parts.nextPart()!!
    assertThat(partMno.body.readUtf8()).isEqualTo("mnop")

    assertThat(parts.nextPart()).isNull()
  }

  @Test fun `cannot read part after calling nextPart`() {
    val multipart =
      """
      |--simple boundary
      |
      |abcd
      |efgh
      |ijkl
      |--simple boundary
      |
      |mnop
      |--simple boundary--
      """.trimMargin()
        .replace("\n", "\r\n")

    val parts =
      MultipartReader(
        boundary = "simple boundary",
        source = Buffer().writeUtf8(multipart),
      )

    val partAbc = parts.nextPart()!!
    val partMno = parts.nextPart()!!

    assertFailsWith<IllegalStateException> {
      partAbc.body.request(20)
    }.also { expected ->
      assertThat(expected).hasMessage("closed")
    }

    assertThat(partMno.body.readUtf8()).isEqualTo("mnop")
    assertThat(parts.nextPart()).isNull()
  }

  @Test fun `cannot read part after calling close`() {
    val multipart =
      """
      |--simple boundary
      |
      |abcd
      |--simple boundary--
      """.trimMargin()
        .replace("\n", "\r\n")

    val parts =
      MultipartReader(
        boundary = "simple boundary",
        source = Buffer().writeUtf8(multipart),
      )

    val part = parts.nextPart()!!
    parts.close()

    assertFailsWith<IllegalStateException> {
      part.body.request(10)
    }.also { expected ->
      assertThat(expected).hasMessage("closed")
    }
  }

  @Test fun `cannot call nextPart after calling close`() {
    val parts =
      MultipartReader(
        boundary = "simple boundary",
        source = Buffer(),
      )

    parts.close()

    assertFailsWith<IllegalStateException> {
      parts.nextPart()
    }.also { expected ->
      assertThat(expected).hasMessage("closed")
    }
  }

  @Test fun `zero parts`() {
    val multipart =
      """
      |--simple boundary--
      """.trimMargin()
        .replace("\n", "\r\n")

    val parts =
      MultipartReader(
        boundary = "simple boundary",
        source = Buffer().writeUtf8(multipart),
      )

    assertFailsWith<ProtocolException> {
      parts.nextPart()
    }.also { expected ->
      assertThat(expected).hasMessage("expected at least 1 part")
    }
  }

  @Test fun `skip preamble`() {
    val multipart =
      """
      |this is the preamble! it is invisible to application code
      |
      |--simple boundary
      |
      |abcd
      |--simple boundary--
      """.trimMargin()
        .replace("\n", "\r\n")

    val parts =
      MultipartReader(
        boundary = "simple boundary",
        source = Buffer().writeUtf8(multipart),
      )

    val part = parts.nextPart()!!
    assertThat(part.headers).isEqualTo(headersOf())
    assertThat(part.body.readUtf8()).isEqualTo("abcd")

    assertThat(parts.nextPart()).isNull()
  }

  @Test fun `skip epilogue`() {
    val multipart =
      """
      |--simple boundary
      |
      |abcd
      |--simple boundary--
      |this is the epilogue! it is also invisible to application code
      |
      """.trimMargin()
        .replace("\n", "\r\n")

    val parts =
      MultipartReader(
        boundary = "simple boundary",
        source = Buffer().writeUtf8(multipart),
      )

    val part = parts.nextPart()!!
    assertThat(part.headers).isEqualTo(headersOf())
    assertThat(part.body.readUtf8()).isEqualTo("abcd")

    assertThat(parts.nextPart()).isNull()
  }

  @Test fun `skip whitespace after boundary`() {
    val multipart =
      """
      |--simple boundary
      |
      |abcd
      |--simple boundary--
      """.trimMargin()
        .replace(Regex("(?m)simple boundary$"), "simple boundary \t \t")
        .replace("\n", "\r\n")

    val parts =
      MultipartReader(
        boundary = "simple boundary",
        source = Buffer().writeUtf8(multipart),
      )

    val part = parts.nextPart()!!
    assertThat(part.headers).isEqualTo(headersOf())
    assertThat(part.body.readUtf8()).isEqualTo("abcd")

    assertThat(parts.nextPart()).isNull()
  }

  @Test fun `skip whitespace after close delimiter`() {
    val multipart =
      """
      |--simple boundary
      |
      |abcd
      |--simple boundary--
      """.trimMargin()
        .replace(Regex("(?m)simple boundary--$"), "simple boundary-- \t \t")
        .replace("\n", "\r\n")

    val parts =
      MultipartReader(
        boundary = "simple boundary",
        source = Buffer().writeUtf8(multipart),
      )

    val part = parts.nextPart()!!
    assertThat(part.headers).isEqualTo(headersOf())
    assertThat(part.body.readUtf8()).isEqualTo("abcd")

    assertThat(parts.nextPart()).isNull()
  }

  @Test fun `other characters after boundary`() {
    val multipart =
      """
      |--simple boundary hi
      """.trimMargin()
        .replace(Regex("(?m)simple boundary$"), "simple boundary ")
        .replace("\n", "\r\n")

    val parts =
      MultipartReader(
        boundary = "simple boundary",
        source = Buffer().writeUtf8(multipart),
      )

    assertFailsWith<ProtocolException> {
      parts.nextPart()
    }.also { expected ->
      assertThat(expected).hasMessage("unexpected characters after boundary")
    }
  }

  @Test fun `whitespace before close delimiter`() {
    val multipart =
      """
      |--simple boundary
      |
      |abcd
      |--simple boundary  --
      """.trimMargin()
        .replace("\n", "\r\n")

    val parts =
      MultipartReader(
        boundary = "simple boundary",
        source = Buffer().writeUtf8(multipart),
      )

    parts.nextPart()
    assertFailsWith<ProtocolException> {
      parts.nextPart()
    }.also { expected ->
      assertThat(expected).hasMessage("unexpected characters after boundary")
    }
  }

  /** The documentation advises that '-' is the simplest boundary possible. */
  @Test fun `dash boundary`() {
    val multipart =
      """
      |---
      |Content-ID: abc
      |
      |abcd
      |---
      |Content-ID: efg
      |
      |efgh
      |-----
      """.trimMargin()
        .replace("\n", "\r\n")

    val parts =
      MultipartReader(
        boundary = "-",
        source = Buffer().writeUtf8(multipart),
      )

    val partAbc = parts.nextPart()!!
    assertThat(partAbc.headers).isEqualTo(headersOf("Content-ID", "abc"))
    assertThat(partAbc.body.readUtf8()).isEqualTo("abcd")

    val partEfg = parts.nextPart()!!
    assertThat(partEfg.headers).isEqualTo(headersOf("Content-ID", "efg"))
    assertThat(partEfg.body.readUtf8()).isEqualTo("efgh")

    assertThat(parts.nextPart()).isNull()
  }

  @Test fun `no more parts is idempotent`() {
    val multipart =
      """
      |--simple boundary
      |
      |abcd
      |--simple boundary--
      |
      |efgh
      |--simple boundary--
      """.trimMargin()
        .replace("\n", "\r\n")

    val parts =
      MultipartReader(
        boundary = "simple boundary",
        source = Buffer().writeUtf8(multipart),
      )

    assertThat(parts.nextPart()).isNotNull()
    assertThat(parts.nextPart()).isNull()
    assertThat(parts.nextPart()).isNull()
  }

  @Test fun `empty source`() {
    val parts =
      MultipartReader(
        boundary = "simple boundary",
        source = Buffer(),
      )

    assertFailsWith<EOFException> {
      parts.nextPart()
    }
  }

  /** Confirm that [MultipartBody] and [MultipartReader] can work together. */
  @Test fun `multipart round trip`() {
    val body =
      MultipartBody
        .Builder("boundary")
        .setType(MultipartBody.PARALLEL)
        .addPart("Quick".toRequestBody("text/plain".toMediaType()))
        .addFormDataPart("color", "Brown")
        .addFormDataPart("animal", "fox.txt", "Fox".toRequestBody())
        .build()

    val bodyContent = Buffer()
    body.writeTo(bodyContent)

    val reader = MultipartReader(bodyContent, "boundary")

    val quickPart = reader.nextPart()!!
    assertThat(quickPart.headers).isEqualTo(
      headersOf(
        "Content-Type",
        "text/plain; charset=utf-8",
      ),
    )
    assertThat(quickPart.body.readUtf8()).isEqualTo("Quick")

    val brownPart = reader.nextPart()!!
    assertThat(brownPart.headers).isEqualTo(
      headersOf(
        "Content-Disposition",
        "form-data; name=\"color\"",
      ),
    )
    assertThat(brownPart.body.readUtf8()).isEqualTo("Brown")

    val foxPart = reader.nextPart()!!
    assertThat(foxPart.headers).isEqualTo(
      headersOf(
        "Content-Disposition",
        "form-data; name=\"animal\"; filename=\"fox.txt\"",
      ),
    )
    assertThat(foxPart.body.readUtf8()).isEqualTo("Fox")

    assertThat(reader.nextPart()).isNull()
  }

  /**
   * Read 100 MiB of 'a' chars. This was really slow due to a performance bug in [MultipartReader],
   * and will be really slow if we regress the fix for that.
   */
  @Test
  fun `reading a large part with small byteCount`() {
    val multipartBody =
      MultipartBody
        .Builder("foo")
        .addPart(
          headersOf("header-name", "header-value"),
          object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaTypeOrNull()

            override fun contentLength() = 1024L * 1024L * 100L

            override fun writeTo(sink: BufferedSink) {
              val a1024x1024 = "a".repeat(1024 * 1024)
              repeat(100) {
                sink.writeUtf8(a1024x1024)
              }
            }
          },
        ).build()
    val buffer = Buffer()
    multipartBody.writeTo(buffer)

    val multipartReader = MultipartReader(buffer, "foo")
    val onlyPart = multipartReader.nextPart()!!
    assertThat(onlyPart.headers).isEqualTo(
      headersOf(
        "header-name",
        "header-value",
        "Content-Type",
        "application/octet-stream",
      ),
    )
    val readBuff = Buffer()
    var byteCount = 0L
    while (true) {
      val readByteCount = onlyPart.body.read(readBuff, 1024L)
      if (readByteCount == -1L) break
      byteCount += readByteCount
      assertThat(readBuff.readUtf8()).isEqualTo("a".repeat(readByteCount.toInt()))
    }
    assertThat(byteCount).isEqualTo(1024L * 1024L * 100L)
    assertThat(multipartReader.nextPart()).isNull()
  }
}
