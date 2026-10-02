package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.JsonUtils
import com.example.util.SensitiveDataMasker
import com.example.util.ValidationUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    assertEquals("DevTraffic Inspector", appName)
  }

  @Test
  fun `validation utils tests`() {
    assertTrue(ValidationUtils.isValidMethod("POST"))
    assertTrue(ValidationUtils.isValidMethod("get"))
    assertFalse(ValidationUtils.isValidMethod("INVALID_METHOD"))

    assertTrue(ValidationUtils.isValidUrl("https://example.com/api/test"))
    assertTrue(ValidationUtils.isValidUrl("http://127.0.0.1:8080/test"))
    assertFalse(ValidationUtils.isValidUrl("ftp://invalid"))
  }

  @Test
  fun `sensitive data masking tests`() {
    val headers = mapOf(
      "Authorization" to "Bearer secret_token_123",
      "Content-Type" to "application/json"
    )
    val masked = SensitiveDataMasker.maskHeaders(headers, enabled = true)
    assertEquals("••••••••", masked["Authorization"])
    assertEquals("application/json", masked["Content-Type"])
  }

  @Test
  fun `json utils format and validation`() {
    val rawJson = "{\"name\":\"test\",\"value\":123}"
    assertTrue(JsonUtils.isValidJson(rawJson))
    assertFalse(JsonUtils.isValidJson("plain text"))
  }
}
