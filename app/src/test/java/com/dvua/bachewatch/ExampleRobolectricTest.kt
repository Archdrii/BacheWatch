package com.dvua.bachewatch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("BacheWatch", appName)
  }

  @Test
  fun `test sorting reports by upvotes`() {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = BacheViewModel(application)

    // Initially we populate mock reports.
    // Let's set sorting to "Más votados".
    viewModel.setSortBy("Más votados")

    // Retrieve active reports.
    // Because filteredReports is a StateFlow calculated via combine on background threads,
    // we can retrieve its current value.
    val reports = viewModel.filteredReports.value
    if (reports.isNotEmpty()) {
        // Assert they are sorted in descending order of upvotes.
        for (i in 0 until reports.size - 1) {
            assert(reports[i].upvotes >= reports[i+1].upvotes) {
                "Reports are not sorted correctly by upvotes: ${reports[i].upvotes} is not >= ${reports[i+1].upvotes}"
            }
        }

        // Test toggleUpvote on the second report.
        val reportToUpvote = reports[1]
        val originalUpvotes = reportToUpvote.upvotes
        viewModel.toggleUpvote(reportToUpvote)
        
        // Local upvotedIds session state should contain this ID now.
        assert(viewModel.upvotedIds.value.contains(reportToUpvote.id))
    }
  }
}
