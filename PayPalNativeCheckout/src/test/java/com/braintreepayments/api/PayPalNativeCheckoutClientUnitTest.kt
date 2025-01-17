package com.braintreepayments.api

import android.app.Application
import androidx.fragment.app.FragmentActivity
import com.braintreepayments.api.Configuration.Companion.fromJson
import com.paypal.checkout.PayPalCheckout
import com.paypal.checkout.approve.Approval
import com.paypal.checkout.approve.ApprovalData
import com.paypal.checkout.approve.OnApprove
import com.paypal.checkout.cancel.OnCancel
import com.paypal.checkout.config.CheckoutConfig
import com.paypal.checkout.config.Environment
import com.paypal.checkout.config.SettingsConfig
import com.paypal.checkout.config.UIConfig
import com.paypal.checkout.error.ErrorInfo
import com.paypal.checkout.error.OnError
import com.paypal.checkout.shipping.OnShippingChange
import com.paypal.pyplcheckout.data.model.pojo.Buyer
import com.paypal.pyplcheckout.data.model.pojo.Email
import com.paypal.pyplcheckout.data.model.pojo.Name
import com.paypal.pyplcheckout.instrumentation.constants.PEnums
import com.paypal.pyplcheckout.instrumentation.di.PLog
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.json.JSONException
import org.json.JSONObject
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Collections.emptyList

@RunWith(RobolectricTestRunner::class)
class PayPalNativeCheckoutClientUnitTest {

    private lateinit var activity: FragmentActivity
    private lateinit var application: Application
    private lateinit var listener: PayPalNativeCheckoutListener
    private lateinit var payPalEnabledConfig: Configuration
    private lateinit var payPalDisabledConfig: Configuration

    @Before
    @Throws(JSONException::class)
    fun beforeEach() {
        mockkStatic(PayPalCheckout::class)
        mockkStatic(PLog::class)

        activity = mockk(relaxed = true)
        listener = mockk(relaxed = true)

        application = mockk(relaxed = true)
        every { activity.application } returns application

        payPalEnabledConfig = fromJson(Fixtures.CONFIGURATION_WITH_LIVE_PAYPAL_NATIVE)
        payPalDisabledConfig = fromJson(Fixtures.CONFIGURATION_WITH_DISABLED_PAYPAL)
    }

    @Test
    fun tokenizePayPalAccount_throwsWhenPayPalRequestIsBaseClass() {
        val baseRequest: PayPalNativeRequest = object : PayPalNativeRequest() {
            @Throws(JSONException::class)
            public override fun createRequestBody(
                configuration: Configuration,
                authorization: Authorization,
                successUrl: String,
                cancelUrl: String
            ): String = ""
        }

        val braintreeClient = MockkBraintreeClientBuilder().build()
        val payPalInternalClient = MockkPayPalInternalClientBuilder().build()
        val sut = PayPalNativeCheckoutClient(braintreeClient, payPalInternalClient)
        var capturedException: Exception? = null
        try {
            sut.tokenizePayPalAccount(activity, baseRequest)
        } catch (e: Exception) {
            capturedException = e
        }
        assertNotNull(capturedException)
        val expectedMessage = ("Unsupported request type. Please use either a "
                + "PayPalNativeCheckoutRequest or a PayPalNativeCheckoutVaultRequest.")
        assertEquals(expectedMessage, capturedException!!.message)
    }

    @Test
    @Ignore("Refactor test to work with mockk")
    fun requestBillingAgreement_launchNativeCheckout_sendsAnalyticsEvents() {
        val payPalVaultRequest = PayPalNativeCheckoutVaultRequest()
        payPalVaultRequest.merchantAccountId = "sample-merchant-account-id"
        payPalVaultRequest.returnUrl = "returnUrl://paypalpay"
        val payPalResponse = PayPalNativeCheckoutResponse(payPalVaultRequest)
            .clientMetadataId("sample-client-metadata-id")
        val payPalInternalClient = MockkPayPalInternalClientBuilder()
            .sendRequestSuccess(payPalResponse)
            .build()
        val braintreeClient = MockkBraintreeClientBuilder()
            .configurationSuccess(payPalEnabledConfig)
            .build()

        val sut = PayPalNativeCheckoutClient(braintreeClient, payPalInternalClient)
        sut.setListener(listener)
        sut.launchNativeCheckout(activity, payPalVaultRequest)

        verify {
            PLog.transition(
                PEnums.TransitionName.BRAINTREE_ROUTING,
                PEnums.Outcome.THIRD_PARTY,
                PEnums.EventCode.E233,
                PEnums.StateName.BRAINTREE,
                null,
                null,
                null,
                null,
                null,
                null,
                "BrainTree"
            )
        }

        verify { braintreeClient.sendAnalyticsEvent("paypal-native.tokenize.started") }
        verify { braintreeClient.sendAnalyticsEvent("paypal-native.tokenize.succeeded") }
        verify { braintreeClient.sendAnalyticsEvent("paypal-native.billing-agreement.selected") }
    }

    @Test
    fun requestNativeCheckout_returnsErrorFromFailedResponse() {
        val payPalVaultRequest = PayPalNativeCheckoutVaultRequest()
        payPalVaultRequest.merchantAccountId = "sample-merchant-account-id"
        payPalVaultRequest.returnUrl = "returnUrl://paypalpay"
        val payPalInternalClient = MockkPayPalInternalClientBuilder()
            .sendRequestError(Exception())
            .build()
        val braintreeClient = MockkBraintreeClientBuilder()
            .configurationSuccess(payPalEnabledConfig)
            .build()
        val sut = PayPalNativeCheckoutClient(braintreeClient, payPalInternalClient)
        sut.setListener(listener)
        sut.launchNativeCheckout(activity, payPalVaultRequest)
        every { listener.onPayPalFailure(any()) }
    }

    @Test
    @Ignore("Refactor test to work with mockk")
    fun requestOneTimePayment_launchNativeCheckout_sendsAnalyticsEvents() {
        val payPalCheckoutRequest = PayPalNativeCheckoutRequest("1.00")
        payPalCheckoutRequest.intent = "authorize"
        payPalCheckoutRequest.merchantAccountId = "sample-merchant-account-id"
        payPalCheckoutRequest.returnUrl = "returnUrl://paypalpay"
        val payPalResponse = PayPalNativeCheckoutResponse(payPalCheckoutRequest)
            .clientMetadataId("sample-client-metadata-id")
        val payPalInternalClient = MockkPayPalInternalClientBuilder()
            .sendRequestSuccess(payPalResponse)
            .build()
        val braintreeClient = MockkBraintreeClientBuilder()
            .configurationSuccess(payPalEnabledConfig)
            .build()
        val configSlot = slot<CheckoutConfig>()
        val onApproveSlot = slot<OnApprove>()
        val onShippingChangeSlot = slot<OnShippingChange>()
        val onCancelSlot = slot<OnCancel>()
        val onErrorSlot = slot<OnError>()

        val sut = PayPalNativeCheckoutClient(braintreeClient, payPalInternalClient)
        sut.setListener(listener)
        sut.launchNativeCheckout(activity, payPalCheckoutRequest)

        verify {
            PayPalCheckout.setConfig(
                CheckoutConfig(
                    application,
                    payPalEnabledConfig.payPalClientId!!, Environment.SANDBOX,
                    null,
                    null,
                    null,
                    SettingsConfig(),
                    UIConfig(
                        false
                    ),
                    "empty"
                )
            )
        }

        val onApprove = OnApprove { approval: Approval? -> }
        val onCancel = OnCancel {}
        val onError = OnError { errorInfo: ErrorInfo? -> }
        PayPalCheckout.registerCallbacks(
            onApprove,
            null,
            onCancel,
            onError
        )

        verify { PayPalCheckout.setConfig(capture(configSlot)) }
        verify {
            PayPalCheckout.registerCallbacks(
                capture(onApproveSlot),
                capture(onShippingChangeSlot),
                capture(onCancelSlot),
                capture(onErrorSlot)
            )
        }

        verify {
            PLog.transition(
                PEnums.TransitionName.BRAINTREE_ROUTING,
                PEnums.Outcome.THIRD_PARTY,
                PEnums.EventCode.E233,
                PEnums.StateName.BRAINTREE,
                null,
                null,
                null,
                null,
                null,
                null,
                "BrainTree"
            )
        }

        assertEquals(payPalEnabledConfig.payPalClientId, configSlot.captured.clientId)
        assertEquals(onApprove, onApproveSlot.captured)
        assertEquals(onCancel, onCancelSlot.captured)
        assertEquals(onError, onErrorSlot.captured)

        verify { braintreeClient.sendAnalyticsEvent("paypal-native.tokenize.started") }
        verify { braintreeClient.sendAnalyticsEvent("paypal-native.tokenize.succeeded") }
        verify { braintreeClient.sendAnalyticsEvent("paypal-native.single-payment.selected") }
        verify { braintreeClient.sendAnalyticsEvent("paypal-native.single-payment.started") }
    }

    @Test
    @Throws(JSONException::class)
    fun paypalAccount_isSetupCorrectly() {
        val riskCorrelationId = "riskId"
        val sampleMerchantId = "sample-merchant-account-id"
        val payPalCheckoutRequest = PayPalNativeCheckoutRequest("1.00")
        payPalCheckoutRequest.intent = "authorize"
        payPalCheckoutRequest.merchantAccountId = sampleMerchantId
        payPalCheckoutRequest.returnUrl = "returnUrl://paypalpay"
        payPalCheckoutRequest.riskCorrelationId = riskCorrelationId
        val payPalResponse = PayPalNativeCheckoutResponse(payPalCheckoutRequest)
            .clientMetadataId("sample-client-metadata-id")
        val payPalInternalClient = MockkPayPalInternalClientBuilder()
            .sendRequestSuccess(payPalResponse)
            .build()
        val braintreeClient = MockkBraintreeClientBuilder()
            .configurationSuccess(payPalEnabledConfig)
            .build()
        val sut = PayPalNativeCheckoutClient(braintreeClient, payPalInternalClient)
        val approvalData = ApprovalData(null, null, null, null, null, null, null, null, null)
        val account = sut.setupAccount(payPalCheckoutRequest, approvalData)
        assertEquals(account.clientMetadataId, riskCorrelationId)
        assertEquals(account.merchantAccountId, sampleMerchantId)
    }

    @Test
    fun payerInfoIsSetIfNonceIsEmpty() {
        val riskCorrelationId = "riskId"
        val sampleMerchantId = "sample-merchant-account-id"
        val payPalCheckoutRequest = PayPalNativeCheckoutRequest("1.00")
        payPalCheckoutRequest.intent = "authorize"
        payPalCheckoutRequest.merchantAccountId = sampleMerchantId
        payPalCheckoutRequest.returnUrl = "returnUrl://paypalpay"
        payPalCheckoutRequest.riskCorrelationId = riskCorrelationId
        val payPalResponse = PayPalNativeCheckoutResponse(payPalCheckoutRequest)
            .clientMetadataId("sample-client-metadata-id")
        val payPalInternalClient = MockkPayPalInternalClientBuilder()
            .sendRequestSuccess(payPalResponse)
            .build()
        val braintreeClient = MockkBraintreeClientBuilder()
            .configurationSuccess(payPalEnabledConfig)
            .build()
        val buyer = Buyer(
            "2",
            Email("test@test.com"),
            Name("test", "givenNameTest", "familyNameTest"),
            emptyList(),
            emptyList()
        )
        val sut = PayPalNativeCheckoutClient(braintreeClient, payPalInternalClient)
        val approvalData = ApprovalData("2", null, null, buyer, null, null, null, null, null)
        val nonceValue = "{\n" +
            "  \"type\": \"PayPalAccount\",\n" +
            "  \"nonce\": \"68a313fb-2747-10cd-1cdf-c85d49e55774\",\n" +
            "  \"description\": \"PayPal\",\n" +
            "  \"consumed\": false,\n" +
            "  \"details\": {\n" +
            "    \"correlationId\": \"EC-6XE6338828653824N\",\n" +
            "    \"billingAddress\": null,\n" +
            "    \"shippingAddress\": {\n" +
            "      \"recipientName\": \"Brian Tree\",\n" +
            "      \"line1\": \"123 Fake Street\",\n" +
            "      \"line2\": \"Floor A\",\n" +
            "      \"city\": \"San Francisco\",\n" +
            "      \"state\": \"CA\",\n" +
            "      \"postalCode\": \"94103\",\n" +
            "      \"countryCode\": \"US\"\n" +
            "    }\n" +
            "  }\n" +
            "}"
        val nonce = PayPalNativeCheckoutAccountNonce.fromJSON(JSONObject(nonceValue))
        sut.setupPayerInfoIfNeeded(nonce, approvalData)
        assertEquals(nonce.payerId, "2")
        assertEquals(nonce.firstName, "givenNameTest")
        assertEquals(nonce.lastName, "familyNameTest")
    }

    @Test
    @Throws(Exception::class)
    @Ignore("Refactor test to work with mockk")
    fun requestOneTimePayment_sendsBrowserSwitchStartAnalyticsEvent() {
        val payPalCheckoutRequest = PayPalNativeCheckoutRequest("1.00")
        payPalCheckoutRequest.intent = "authorize"
        payPalCheckoutRequest.merchantAccountId = "sample-merchant-account-id"
        payPalCheckoutRequest.returnUrl = "returnUrl://paypalpay"
        val payPalResponse = PayPalNativeCheckoutResponse(payPalCheckoutRequest)
            .clientMetadataId("sample-client-metadata-id")
        val payPalInternalClient = MockkPayPalInternalClientBuilder()
            .sendRequestSuccess(payPalResponse)
            .build()
        val braintreeClient = MockkBraintreeClientBuilder()
            .configurationSuccess(payPalEnabledConfig)
            .build()
        val sut = PayPalNativeCheckoutClient(braintreeClient, payPalInternalClient)
        sut.setListener(listener)
        sut.launchNativeCheckout(activity, payPalCheckoutRequest)

        verify {
            PLog.transition(
                PEnums.TransitionName.BRAINTREE_ROUTING,
                PEnums.Outcome.THIRD_PARTY,
                PEnums.EventCode.E233,
                PEnums.StateName.BRAINTREE,
                null,
                null,
                null,
                null,
                null,
                null,
                "BrainTree"
            )
        }
        verify { braintreeClient.sendAnalyticsEvent("paypal-native.single-payment.selected") }
        verify { braintreeClient.sendAnalyticsEvent("paypal-native.single-payment.started") }
    }

    @Test
    @Throws(Exception::class)
    fun requestOneTimePayment_sendsPayPalPayLaterOfferedAnalyticsEvent() {
        val braintreeClient = MockkBraintreeClientBuilder().build()
        val payPalInternalClient = MockkPayPalInternalClientBuilder().build()
        val sut = PayPalNativeCheckoutClient(braintreeClient, payPalInternalClient)
        val request = PayPalNativeCheckoutRequest("1.00")
        request.shouldOfferPayLater = true
        sut.tokenizePayPalAccount(activity, request)
        verify { braintreeClient.sendAnalyticsEvent("paypal-native.single-payment.paylater.offered") }
    }

    @Test
    @Throws(Exception::class)
    fun tokenizePayPalAccount_sendsPayPalRequestViaInternalClient() {
        val payPalInternalClient = MockkPayPalInternalClientBuilder().build()
        val braintreeClient = MockkBraintreeClientBuilder()
            .configurationSuccess(payPalEnabledConfig)
            .build()
        val payPalRequest = PayPalNativeCheckoutVaultRequest()
        val sut = PayPalNativeCheckoutClient(braintreeClient, payPalInternalClient)
        sut.tokenizePayPalAccount(activity, payPalRequest)
        verify { payPalInternalClient.sendRequest(activity, payPalRequest, any()) }
    }

    @Test
    @Throws(Exception::class)
    fun requestOneTimePayment_sendsPayPalRequestViaInternalClient() {
        val payPalInternalClient = MockkPayPalInternalClientBuilder().build()
        val braintreeClient = MockkBraintreeClientBuilder()
            .configurationSuccess(payPalEnabledConfig)
            .build()
        val payPalRequest = PayPalNativeCheckoutRequest("1.00")
        val sut = PayPalNativeCheckoutClient(braintreeClient, payPalInternalClient)
        sut.tokenizePayPalAccount(activity, payPalRequest)
        verify { payPalInternalClient.sendRequest(activity, payPalRequest, any()) }
    }

    @Test
    @Throws(Exception::class)
    fun tokenizePayPalAccount_sendsPayPalCreditOfferedAnalyticsEvent() {
        val payPalInternalClient = MockkPayPalInternalClientBuilder().build()
        val braintreeClient = MockkBraintreeClientBuilder().build()
        val payPalRequest = PayPalNativeCheckoutVaultRequest()
        payPalRequest.shouldOfferCredit = true
        val sut = PayPalNativeCheckoutClient(braintreeClient, payPalInternalClient)
        sut.tokenizePayPalAccount(activity, payPalRequest)
        verify { braintreeClient.sendAnalyticsEvent("paypal-native.billing-agreement.credit.offered") }
    }

    @Test
    fun launchNativeCheckout_notifiesErrorWhenPayPalRequestIsBaseClass_sendsAnalyticsEvents() {
        val baseRequest: PayPalNativeRequest = object : PayPalNativeRequest() {
            @Throws(JSONException::class)
            public override fun createRequestBody(
                configuration: Configuration,
                authorization: Authorization,
                successUrl: String,
                cancelUrl: String
            ): String = ""
        }
        val braintreeClient = MockkBraintreeClientBuilder().build()
        val payPalInternalClient = MockkPayPalInternalClientBuilder().build()
        val sut = PayPalNativeCheckoutClient(braintreeClient, payPalInternalClient)
        sut.setListener(listener)
        sut.launchNativeCheckout(activity, baseRequest)
        val exceptionSlot = slot<Exception>()
        verify { listener.onPayPalFailure(capture(exceptionSlot)) }
        val capturedException = exceptionSlot.captured
        val expectedMessage = ("Unsupported request type. Please use either a "
                + "PayPalNativeCheckoutRequest or a PayPalNativeCheckoutVaultRequest.")
        assertEquals(expectedMessage, capturedException.message)
        verify { braintreeClient.sendAnalyticsEvent("paypal-native.tokenize.started") }
        verify { braintreeClient.sendAnalyticsEvent("paypal-native.tokenize.invalid-request.failed") }
    }
}