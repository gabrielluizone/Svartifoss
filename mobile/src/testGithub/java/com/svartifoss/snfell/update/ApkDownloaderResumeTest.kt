package com.svartifoss.snfell.update

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.MessageDigest

/**
 * The downloader against a real HTTP server on localhost, with the behaviours that matter for a
 * 25 to 36 MB release asset: a range honoured, a range ignored, a range refused, a body cut short,
 * an error. Runs on the JDK's HttpURLConnection rather than Android's, which is close enough for
 * the protocol logic and is not a claim about Android's redirect handling (that is what the
 * `Connection: close` header and the short-body retry are for).
 *
 * The server is [LocalHttpServer], a few lines over a `ServerSocket`, and not the JDK's own
 * `com.sun.net.httpserver`: an Android module's unit tests are compiled against `android.jar`,
 * which has no such package, so a test written with it never compiled - and took every other test
 * of the `github` flavor down with it, since one source set is one compilation.
 */
class ApkDownloaderResumeTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var server: LocalHttpServer
    private val content = ByteArray(300_000) { (it * 31 + 7).toByte() }

    /** Range headers of each request that reached the server, null when it had none. */
    private val requests = mutableListOf<String?>()

    // Behaviour switches, read by the handler per request.
    private var honourRange = true
    private var rangeStatus: Int? = null
    private var failWith: Int? = null
    private var cutFirstBodyAt: Int? = null
    private var lyingContentRange = false

    @Before
    fun startServer() {
        server = LocalHttpServer(::handle)
    }

    @After
    fun stopServer() {
        server.close()
    }

    private val url get() = "http://127.0.0.1:${server.port}/release.apk"

    private fun handle(headers: Map<String, String>): LocalHttpServer.Response {
        val range = headers["range"]
        val requestNumber = synchronized(requests) {
            requests += range
            requests.size
        }

        failWith?.let { return LocalHttpServer.Response(it) }
        val start = range?.removePrefix("bytes=")?.substringBefore('-')?.toIntOrNull()
        if (start != null && rangeStatus != null) return LocalHttpServer.Response(rangeStatus!!)

        val ranged = start != null && honourRange
        val from = if (ranged) start!! else 0
        val body = content.copyOfRange(from, content.size)
        val shownStart = if (lyingContentRange) from + 1 else from
        return LocalHttpServer.Response(
                status = if (ranged) 206 else 200,
                body = body,
                contentRange = if (ranged) "bytes $shownStart-${content.size - 1}/${content.size}" else null,
                // Declares the whole body, sends part of it and hangs up.
                sendOnly = cutFirstBodyAt?.takeIf { requestNumber == 1 }?.coerceAtMost(body.size))
    }

    private fun target() = File(folder.root, "updates/wear-9.9.apk")

    private fun download(
            target: File = target(),
            expectedSize: Long = content.size.toLong(),
            sha256: String? = null,
            isUsable: (File) -> Boolean = { true },
            percents: MutableList<Int> = mutableListOf()
    ): ApkDownloader.Outcome = runBlocking {
        ApkDownloader.download(url, expectedSize, target, sha256, isUsable) { percents += it }
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }

    // ---- the ordinary download -----------------------------------------------------------------

    @Test
    fun `a fresh download lands whole and leaves no partial behind`() {
        val outcome = download()
        assertEquals(ApkDownloader.Outcome.DOWNLOADED, outcome)
        assertArrayEquals(content, target().readBytes())
        assertFalse(ApkDownloader.partialFor(target()).exists())
        assertEquals(listOf<String?>(null), requests)
    }

    @Test
    fun `progress reaches 100 and never goes backwards`() {
        val percents = mutableListOf<Int>()
        download(percents = percents)
        assertEquals(100, percents.last())
        assertEquals(percents.sorted(), percents)
    }

    // ---- resuming ------------------------------------------------------------------------------

    @Test
    fun `an unfinished download asks for the rest and does not start over`() {
        val partial = ApkDownloader.partialFor(target()).also { it.parentFile!!.mkdirs() }
        partial.writeBytes(content.copyOfRange(0, 120_000))

        val percents = mutableListOf<Int>()
        val outcome = download(percents = percents)

        assertEquals(ApkDownloader.Outcome.DOWNLOADED, outcome)
        assertArrayEquals(content, target().readBytes())
        assertEquals(listOf<String?>("bytes=120000-"), requests)
        // The first reported figure is the resumed one, not zero.
        assertTrue("first percent was ${percents.first()}", percents.first() >= 40)
    }

    @Test
    fun `a server that ignores the range gets the file from the start`() {
        honourRange = false
        ApkDownloader.partialFor(target()).also { it.parentFile!!.mkdirs() }
                .writeBytes(content.copyOfRange(0, 50_000))

        download()

        assertArrayEquals(content, target().readBytes())
        assertEquals(listOf<String?>("bytes=50000-"), requests)
    }

    @Test
    fun `a refused range restarts from zero in the same call`() {
        rangeStatus = 416
        ApkDownloader.partialFor(target()).also { it.parentFile!!.mkdirs() }
                .writeBytes(content.copyOfRange(0, 50_000))

        download()

        assertArrayEquals(content, target().readBytes())
        assertEquals(listOf<String?>("bytes=50000-", null), requests)
    }

    @Test
    fun `a content range that starts elsewhere is never appended`() {
        lyingContentRange = true
        ApkDownloader.partialFor(target()).also { it.parentFile!!.mkdirs() }
                .writeBytes(content.copyOfRange(0, 50_000))

        download()

        // Appending that body would have produced a corrupt file; the whole thing was refetched.
        assertArrayEquals(content, target().readBytes())
        assertEquals(listOf<String?>("bytes=50000-", null), requests)
    }

    @Test
    fun `a partial longer than the release is discarded`() {
        ApkDownloader.partialFor(target()).also { it.parentFile!!.mkdirs() }
                .writeBytes(ByteArray(content.size + 10))

        download()

        assertArrayEquals(content, target().readBytes())
        assertEquals(listOf<String?>(null), requests)
    }

    @Test
    fun `a body cut short is finished from what arrived`() {
        cutFirstBodyAt = 100_000

        download()

        assertArrayEquals(content, target().readBytes())
        assertEquals(2, requests.size)
        assertNull(requests[0])
        assertTrue("second request was ${requests[1]}", requests[1]!!.startsWith("bytes="))
    }

    // ---- reuse ---------------------------------------------------------------------------------

    @Test
    fun `a complete file already there is used with no request at all`() {
        target().also { it.parentFile!!.mkdirs() }.writeBytes(content)

        val percents = mutableListOf<Int>()
        val outcome = download(percents = percents, sha256 = "sha256:" + sha256(content))

        assertEquals(ApkDownloader.Outcome.REUSED, outcome)
        assertTrue(requests.isEmpty())
        assertEquals(listOf(100), percents)
    }

    @Test
    fun `a file of the wrong size is replaced`() {
        target().also { it.parentFile!!.mkdirs() }.writeBytes(content.copyOf(1_000))

        assertEquals(ApkDownloader.Outcome.DOWNLOADED, download())
        assertArrayEquals(content, target().readBytes())
    }

    @Test
    fun `a file of the right size with the wrong digest is replaced`() {
        target().also { it.parentFile!!.mkdirs() }.writeBytes(ByteArray(content.size) { 1 })

        val outcome = download(sha256 = sha256(content))

        assertEquals(ApkDownloader.Outcome.DOWNLOADED, outcome)
        assertArrayEquals(content, target().readBytes())
    }

    @Test
    fun `a file the caller does not accept is not reused`() {
        target().also { it.parentFile!!.mkdirs() }.writeBytes(content)
        var accept = false

        val outcome = download(isUsable = { accept.also { accept = true } })

        assertEquals(ApkDownloader.Outcome.DOWNLOADED, outcome)
        assertEquals(1, requests.size)
    }

    // ---- failures keep what is worth keeping ---------------------------------------------------

    @Test
    fun `an http error keeps the partial for the next attempt`() {
        val partial = ApkDownloader.partialFor(target()).also { it.parentFile!!.mkdirs() }
        partial.writeBytes(content.copyOfRange(0, 80_000))
        failWith = 503

        try {
            download()
            fail("expected an IOException")
        } catch (expected: IOException) {
            // The point of the test is what is left behind.
        }

        assertTrue(partial.isFile)
        assertEquals(80_000L, partial.length())
        assertFalse(target().exists())
        // One request: no progress was made, so it did not spin.
        assertEquals(1, requests.size)
    }

    @Test
    fun `a digest that does not match deletes the partial and the target`() {
        try {
            download(sha256 = sha256(ByteArray(3)))
            fail("expected an IOException")
        } catch (expected: IOException) {
        }

        assertFalse(target().exists())
        assertFalse(ApkDownloader.partialFor(target()).exists())
    }

    @Test
    fun `a file the caller rejects after downloading is deleted`() {
        try {
            download(isUsable = { false })
            fail("expected an IOException")
        } catch (expected: IOException) {
        }

        assertFalse(target().exists())
    }

    // ---- parsing -------------------------------------------------------------------------------

    @Test
    fun `the start of a content range is read from the header`() {
        assertEquals(100L, ApkDownloader.contentRangeStart("bytes 100-199/1000"))
        assertEquals(0L, ApkDownloader.contentRangeStart("bytes 0-9/*"))
        assertEquals(5L, ApkDownloader.contentRangeStart("Bytes 5-9/10"))
        assertNull(ApkDownloader.contentRangeStart("bytes */1000"))
        assertNull(ApkDownloader.contentRangeStart("items 1-2/3"))
        assertNull(ApkDownloader.contentRangeStart(null))
    }

    @Test
    fun `a digest is read the way github writes it`() {
        val hex = "b45ca657d8437241ee50fcbb10bdb39ec0c25c77ea631b1333fcec2a9f8794f7"
        assertEquals(hex, ApkDownloader.normalizeSha256("sha256:$hex"))
        assertEquals(hex, ApkDownloader.normalizeSha256(hex.uppercase()))
        assertNull(ApkDownloader.normalizeSha256("sha1:abcdef"))
        assertNull(ApkDownloader.normalizeSha256("sha256:xyz"))
        assertNull(ApkDownloader.normalizeSha256(null))
        assertNull(ApkDownloader.normalizeSha256(""))
    }
}

/**
 * The least HTTP server a download can be pointed at: one `GET` per connection, answered from
 * [handler] and then hung up on, which is also what `Connection: close` asks of a real one.
 *
 * It exists so the test above needs nothing beyond `java.net` - see its comment. Request header
 * names reach [handler] in lower case.
 */
private class LocalHttpServer(
        private val handler: (headers: Map<String, String>) -> Response
) : AutoCloseable {

    /**
     * @param sendOnly when set, the response declares the whole [body] and sends only this many
     *   bytes of it before closing: a connection that dies part-way.
     */
    class Response(
            val status: Int,
            val body: ByteArray = ByteArray(0),
            val contentRange: String? = null,
            val sendOnly: Int? = null
    )

    private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = socket.localPort

    private val thread = Thread({
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: SocketException) {
                break // closed by [close]
            }
            try {
                client.use(::serve)
            } catch (e: IOException) {
                // The client went away mid-answer. The test that caused it asserts what matters.
            }
        }
    }, "local-http-server").apply {
        isDaemon = true
        start()
    }

    private fun serve(client: Socket) {
        val headers = readHeaders(client.getInputStream()) ?: return
        val response = handler(headers)
        val head = buildString {
            append("HTTP/1.1 ${response.status} ${reason(response.status)}\r\n")
            append("Content-Length: ${response.body.size}\r\n")
            response.contentRange?.let { append("Content-Range: $it\r\n") }
            append("Connection: close\r\n\r\n")
        }
        val out = client.getOutputStream()
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.write(response.body, 0, response.sendOnly ?: response.body.size)
        out.flush()
    }

    /** The request's headers by lower-case name, or null when the client sent no request at all. */
    private fun readHeaders(input: InputStream): Map<String, String>? {
        val lines = mutableListOf<String>()
        val line = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte < 0) return null
            if (byte == '\n'.code) {
                val text = line.toString("US-ASCII").trimEnd('\r')
                line.reset()
                if (text.isEmpty()) break
                lines += text
            } else {
                line.write(byte)
            }
        }
        // The first line is the request line; the rest are `Name: value`.
        return lines.drop(1).mapNotNull { header ->
            val colon = header.indexOf(':')
            if (colon <= 0) null
            else header.substring(0, colon).trim().lowercase() to header.substring(colon + 1).trim()
        }.toMap()
    }

    private fun reason(status: Int) = when (status) {
        200 -> "OK"
        206 -> "Partial Content"
        416 -> "Range Not Satisfiable"
        503 -> "Service Unavailable"
        else -> "Status"
    }

    override fun close() {
        socket.close()
        thread.join(2_000)
    }
}
