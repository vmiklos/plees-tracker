package hu.vmiklos.plees_tracker

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for functions in GraphActivity. */
class GraphsActivityUnitTest {
    private val data = listOf(
        0 to 0f,
        1 to 1f,
        2 to 2f,
        3 to 3f,
        4 to 4f
    )

    @Test
    fun `test cumulativeAverage()`() {
        val expected = listOf(
            0 to 0f,
            1 to 0.5f,
            2 to 1f,
            3 to 1.5f,
            4 to 2f
        )
        assertEquals(expected, data.cumulativeAverage())
    }

    @Test
    fun `test cumulativeSum()`() {
        val expected = listOf(
            0 to 0f,
            1 to 1f,
            2 to 3f,
            3 to 6f,
            4 to 10f
        )
        assertEquals(expected, data.cumulativeSum())
    }

    @Test
    fun `test stripTime()`() {
        val dateWithDate = Calendar.getInstance().apply {
            set(2034, 7, 7, 7, 7, 7)
            set(Calendar.MILLISECOND, 0)
        }.time
        val dateWithoutTime = Calendar.getInstance().apply {
            set(2034, 7, 7, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        assertEquals(dateWithoutTime, dateWithDate.stripTime())
        assertEquals(dateWithoutTime.time, dateWithDate.time.stripTime())
    }
}
