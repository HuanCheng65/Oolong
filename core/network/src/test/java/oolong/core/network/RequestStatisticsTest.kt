package oolong.core.network

import kotlinx.coroutines.test.runTest
import oolong.core.network.message.MessageRemoteDataSource
import oolong.core.network.testing.FormServer
import oolong.core.network.testing.assertSigned
import oolong.core.network.testing.form
import oolong.core.network.testing.value
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RequestStatisticsTest {
    private val server = FormServer()
    private val dataSource = MessageRemoteDataSource(server.network, server.commonParams, FakeDevice)

    @After
    fun tearDown() = server.close()

    @Test
    fun `a request reports the one before it, inside the signature`() = runTest {
        val answer = """{"error_code":"0","message":{"replyme":"0"}}"""
        server.enqueue(answer)
        server.enqueue(answer)

        dataSource.reminder()
        dataSource.reminder()

        val first = server.takeRequest().form()
        assertEquals("0", first.value("stErrorNums"))
        assertNull(first.value("stTime"))
        val second = server.takeRequest().form()
        assertSigned(second, withSig = false)
        assertEquals(answer.length.toString(), second.value("stSize"))
        assertEquals("1", second.value("stTimesNum"))
        assertEquals("1", second.value("stMethod"))
        // FakeDevice is on a 5G network, which reports as mode 1
        assertEquals("1", second.value("stMode"))
        assertEquals("1", second.value("stErrorNums"))
    }

    @Test
    fun `failures are counted until a request reports them`() {
        val statistics = RequestStatistics(clock = { 0L })
        statistics.failed()
        statistics.failed()
        statistics.record(timeMillis = 120, size = 800, networkType = 2, post = true)

        val reported = statistics.next().toMap()

        assertEquals("2", reported["stErrorNums"])
        assertEquals("2", reported["stMode"])
        assertEquals("120", reported["stTime"])
        assertEquals("0", statistics.next().toMap()["stErrorNums"])
    }
}
