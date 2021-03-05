package com.braintreepayments.api;

import androidx.fragment.app.FragmentActivity;

/**
 * Callback for receiving result of
 * {@link GooglePayClient#requestPayment(FragmentActivity, GooglePayRequest, GooglePayRequestPaymentCallback)}.
 */
public interface GooglePayRequestPaymentCallback {

    /**
     * @param paymentRequested true if Google Pay flow started successfully; false otherwise.
     * @param error an exception that occurred while initiating the Google Pay flow
     */
    void onResult(boolean paymentRequested, Exception error);
}
