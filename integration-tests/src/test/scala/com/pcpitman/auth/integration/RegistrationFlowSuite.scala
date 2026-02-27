package com.pcpitman.auth.integration

import com.microsoft.playwright.options.AriaRole

class RegistrationFlowSuite extends IntegrationTestBase {

  test("full registration, email validation, mobile verification, logout, and login flow") {
    val page = newPage()
    val testEmail = s"test-${System.currentTimeMillis()}@example.com"
    val testPassword = "TestPass1!xx"
    val testFirstName = "Integration"
    val testLastName = "Test"

    // 1. Navigate to root → redirected to /login
    page.navigate(s"$frontendBaseUrl/")
    page.waitForURL("**/login")
    assert(page.url().contains("/login"), s"Expected /login but got ${page.url()}")

    // 2. Click Register → /register
    page.getByRole(AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Register")).click()
    page.waitForURL("**/register")

    // 3. Fill registration form and submit
    page.getByPlaceholder("First Name").fill(testFirstName)
    page.getByPlaceholder("Last Name").fill(testLastName)
    page.locator("input[type=date]").fill("1990-01-15")
    page.getByPlaceholder("Email").fill(testEmail)
    page.getByPlaceholder("Password", new com.microsoft.playwright.Page.GetByPlaceholderOptions().setExact(true)).fill(testPassword)
    page.getByPlaceholder("Confirm Password").fill(testPassword)
    page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Register")).click()

    // 4. See "Check Your Email" message
    val offsetBeforeRegister = backend.currentLineOffset
    page.waitForSelector("text=Check Your Email")

    // 5. Extract validation token from backend logs (mock SES logs the full request including the URL)
    val token = backend.findPattern("""validate-email\?token=([a-f0-9]{64})""", startOffset = 0)

    // 6. Navigate to validation URL → redirected to /register showing "Add Mobile Number"
    page.navigate(s"$frontendBaseUrl/validate-email?token=$token")
    page.waitForURL("**/register")
    page.waitForSelector("text=Add Mobile Number")

    // 7. Enter phone number, submit → "Verify Mobile" code entry
    val offsetBeforeMobile = backend.currentLineOffset
    page.getByRole(AriaRole.TEXTBOX).fill("5551234567")
    page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Send Verification Code")).click()
    page.waitForSelector("text=Verify Mobile")

    // 8. Extract OTP from backend logs (mock SNS logs the message)
    val otp = backend.findPattern("""verification code is: (\d{6})""", startOffset = offsetBeforeMobile)

    // 9. Enter OTP, submit → redirected to /session showing user info
    page.getByPlaceholder("Verification Code").fill(otp)
    page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Verify")).click()
    page.waitForURL("**/session")
    page.waitForSelector("text=Session")
    assert(page.content().contains(testFirstName), "Session page should show first name")
    assert(page.content().contains(testLastName), "Session page should show last name")
    assert(page.content().contains("1990-01-15"), "Session page should show birth date")
    assert(page.content().contains(testEmail), "Session page should show email")
    assert(page.content().contains("AUTHENTICATED"), "Session page should show AUTHENTICATED status")

    // 10. Logout → /login
    page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Logout")).click()
    page.waitForURL("**/login")

    // 11. Login with same credentials → /session (AUTHENTICATED)
    page.getByPlaceholder("Email").fill(testEmail)
    page.getByPlaceholder("Password").fill(testPassword)
    page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Sign In")).click()
    page.waitForURL("**/session")
    page.waitForSelector("text=AUTHENTICATED")

    page.close()
  }

  test("registration with email change, old token invalid, and mobile change attempt with recovery") {
    val page = newPage()
    val testPassword = "TestPass1!xx"
    val ts = System.currentTimeMillis()
    val email1 = s"change1-$ts@example.com"
    val email2 = s"change2-$ts@example.com"
    val phone1 = "5559876543"
    val phone2 = "5551112222"

    // 1. Register with email1
    page.navigate(s"$frontendBaseUrl/register")
    page.waitForURL("**/register")
    page.getByPlaceholder("First Name").fill("Change")
    page.getByPlaceholder("Last Name").fill("Test")
    page.locator("input[type=date]").fill("1990-01-15")
    page.getByPlaceholder("Email").fill(email1)
    page.getByPlaceholder("Password", new com.microsoft.playwright.Page.GetByPlaceholderOptions().setExact(true)).fill(testPassword)
    page.getByPlaceholder("Confirm Password").fill(testPassword)
    page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Register")).click()

    // 2. See "Check Your Email", extract token1 for email1
    page.waitForSelector("text=Check Your Email")
    val token1 = backend.findPattern("""validate-email\?token=([a-f0-9]{64})""", startOffset = 0)
    val offsetAfterEmail1 = backend.currentLineOffset

    // 3. Click "Back to Update Info" and change email to email2
    page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Back to Update Info")).click()
    page.waitForSelector("text=Update Info")
    page.getByPlaceholder("Email").fill(email2)
    page.getByPlaceholder("Password", new com.microsoft.playwright.Page.GetByPlaceholderOptions().setExact(true)).fill(testPassword)
    page.getByPlaceholder("Confirm Password").fill(testPassword)
    page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Update")).click()

    // 4. See "Check Your Email" again, extract token2 for email2
    page.waitForSelector("text=Check Your Email")
    val token2 = backend.findPattern("""validate-email\?token=([a-f0-9]{64})""", startOffset = offsetAfterEmail1)

    // 5. Validate with old token1 → fails, redirects to /login (token was deleted by updateProfile)
    page.navigate(s"$frontendBaseUrl/validate-email?token=$token1")
    page.waitForURL("**/login")

    // 6. Validate with new token2 → succeeds, redirects to /register with "Add Mobile Number"
    page.navigate(s"$frontendBaseUrl/validate-email?token=$token2")
    page.waitForURL("**/register")
    page.waitForSelector("text=Add Mobile Number")

    // 7. Add phone1 → "Verify Mobile", extract OTP
    val offsetBeforeMobile = backend.currentLineOffset
    page.getByRole(AriaRole.TEXTBOX).fill(phone1)
    page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Send Verification Code")).click()
    page.waitForSelector("text=Verify Mobile")
    val otp1 = backend.findPattern("""verification code is: (\d{6})""", startOffset = offsetBeforeMobile)

    // 8. Click "Change Phone Number", try phone2 → error (addMobile requires EMAIL_VALIDATED but status is MOBILE_PENDING)
    page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Change Phone Number")).click()
    page.waitForSelector("text=Add Mobile Number")
    page.getByRole(AriaRole.TEXTBOX).fill(phone2)
    page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Send Verification Code")).click()
    page.waitForSelector(".error")

    // 9. Navigate back to /register → MOBILE_PENDING restores "Verify Mobile" screen
    page.navigate(s"$frontendBaseUrl/register")
    page.waitForURL("**/register")
    page.waitForSelector("text=Verify Mobile")

    // 10. Enter wrong code → error
    page.getByPlaceholder("Verification Code").fill("000000")
    page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Verify")).click()
    page.waitForSelector(".error")

    // 11. Enter original OTP1 → succeeds (phone change failed so original code is still valid)
    page.getByPlaceholder("Verification Code").fill(otp1)
    page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Verify")).click()
    page.waitForURL("**/session")
    page.waitForSelector("text=AUTHENTICATED")

    page.close()
  }
}
