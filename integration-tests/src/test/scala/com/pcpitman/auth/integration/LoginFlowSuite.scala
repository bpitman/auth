package com.pcpitman.auth.integration

import com.microsoft.playwright.options.AriaRole

class LoginFlowSuite extends IntegrationTestBase {

  test("login with invalid credentials shows error message") {
    val page = newPage()

    page.navigate(s"$frontendBaseUrl/login")
    page.waitForURL("**/login")

    page.getByPlaceholder("Email").fill("nonexistent@example.com")
    page.getByPlaceholder("Password").fill("WrongPass1!xx")
    page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Sign In")).click()

    page.waitForSelector(".error")
    val errorText = page.locator(".error").textContent()
    assert(errorText.nonEmpty, "Error message should be displayed for invalid credentials")

    page.close()
  }

  test("register with mismatched passwords shows error message") {
    val page = newPage()

    page.navigate(s"$frontendBaseUrl/register")
    page.waitForURL("**/register")

    page.getByPlaceholder("First Name").fill("Test")
    page.getByPlaceholder("Last Name").fill("User")
    page.getByPlaceholder("Email").fill("mismatch@example.com")
    page.getByPlaceholder("Password", new com.microsoft.playwright.Page.GetByPlaceholderOptions().setExact(true)).fill("TestPass1!xx")
    page.getByPlaceholder("Confirm Password").fill("DifferentPass1!xx")
    page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Register")).click()

    page.waitForSelector(".error")
    val errorText = page.locator(".error").textContent()
    assert(errorText.contains("passwords do not match"), s"Expected password mismatch error but got: $errorText")

    page.close()
  }
}
