package com.braintreepayments.api;

import android.content.Context;

import androidx.annotation.VisibleForTesting;

/**
 * Used to tokenize credit or debit cards using a {@link CardBuilder}. For more information see the
 * <a href="https://developers.braintreepayments.com/guides/credit-cards/overview">documentation</a>
 */
public class CardClient {

    private final BraintreeClient braintreeClient;
    private final DataCollector dataCollector;
    private final TokenizationClient tokenizationClient;

    public CardClient(BraintreeClient braintreeClient) {
        this(braintreeClient, new TokenizationClient(braintreeClient), new DataCollector(braintreeClient));
    }

    @VisibleForTesting
    CardClient(BraintreeClient braintreeClient, TokenizationClient tokenizationClient, DataCollector dataCollector) {
        this.braintreeClient = braintreeClient;
        this.tokenizationClient = tokenizationClient;
        this.dataCollector = dataCollector;
    }

    /**
     * Create a {@link CardNonce}.
     * <p>
     * The tokenization result is returned via a {@link CardTokenizeCallback} callback.
     *
     * <p>
     * On success, the callback's {@link PaymentMethodNonceCallback#success} method will
     * be invoked with a nonce.
     *
     * <p>
     * If creation fails validation, the callback's {@link PaymentMethodNonceCallback#failure}
     * method will be invoked with an {@link ErrorWithResponse} exception.
     *
     * <p>
     * If an error not due to validation (server error, network issue, etc.) occurs, the callback's
     * {@link PaymentMethodNonceCallback#failure} method will be invoked with
     * an {@link Exception} describing the error.
     *
     * @param context Android context
     * @param cardBuilder {@link CardBuilder}
     * @param callback {@link CardTokenizeCallback}
     */
    public void tokenize(final Context context, final CardBuilder cardBuilder, final CardTokenizeCallback callback) {
        tokenizationClient.tokenize(cardBuilder, new PaymentMethodNonceCallback() {
            @Override
            public void success(PaymentMethodNonce paymentMethodNonce) {
                dataCollector.collectRiskData(context, paymentMethodNonce);

                callback.onResult((CardNonce) paymentMethodNonce, null);
                braintreeClient.sendAnalyticsEvent("card.nonce-received");
            }

            @Override
            public void failure(Exception exception) {
                callback.onResult(null, exception);
                braintreeClient.sendAnalyticsEvent("card.nonce-failed");
            }
        });
    }
}