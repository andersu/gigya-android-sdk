package com.gigya.android.sdk.interruption.tfa;

import com.gigya.android.sdk.GigyaLogger;
import com.gigya.android.sdk.GigyaLoginCallback;
import com.gigya.android.sdk.account.models.GigyaAccount;
import com.gigya.android.sdk.api.GigyaApiResponse;
import com.gigya.android.sdk.api.IBusinessApiService;
import com.gigya.android.sdk.containers.IoCContainer;
import com.gigya.android.sdk.interruption.Resolver;
import com.gigya.android.sdk.interruption.tfa.models.TFAProvider;
import com.gigya.android.sdk.interruption.tfa.models.TFAProvidersModel;
import com.gigya.android.sdk.network.GigyaError;

import java.util.List;

public class TfaProviderResolver<A extends GigyaAccount> extends Resolver {

    private static final String LOG_TAG = "TfaProviderResolver";

    private final IoCContainer _container;

    public TfaProviderResolver(IoCContainer container,
                               GigyaLoginCallback<A> loginCallback,
                               GigyaApiResponse interruption,
                               IBusinessApiService<A> businessApiService) {
        super(loginCallback, interruption, businessApiService);
        _container = container.clone();
        getProviders();
    }

    private void getProviders() {
        GigyaLogger.debug(LOG_TAG, "getProviders: ");
        final String regToken = _interruption.getField("regToken", String.class);

        try {
            final IBusinessApiService service = _container.get(IBusinessApiService.class);
            service.getTFAProviders(regToken, new GigyaLoginCallback<TFAProvidersModel>() {
                @Override
                public void onSuccess(TFAProvidersModel model) {
                    // Get provider lists.
                    final List<TFAProvider> activeProviders = model.getActiveProviders();
                    final List<TFAProvider> inactiveProviders = model.getInactiveProviders();

                    // Instantiate the TFA factory and forward initial interruption.
                    _container.bind(GigyaApiResponse.class, _interruption)
                            .bind(GigyaLoginCallback.class, _loginCallback);
                    try {
                        TfaResolverFactory factory = _container.createInstance(TfaResolverFactory.class);
                        if (_interruption.getErrorCode() == GigyaError.Codes.ERROR_PENDING_TWO_FACTOR_REGISTRATION) {
                            _loginCallback.onPendingTwoFactorRegistration(_interruption, inactiveProviders, factory);
                        } else if (_interruption.getErrorCode() == GigyaError.Codes.ERROR_PENDING_TWO_FACTOR_VERIFICATION) {
                            _loginCallback.onPendingTwoFactorVerification(_interruption, activeProviders, factory);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        // TODO: 2019-06-16 General error?
                        _loginCallback.onError(GigyaError.generalError());
                    } finally {
                        _container.dispose();
                    }
                }

                @Override
                public void onError(GigyaError error) {
                    _loginCallback.onError(error);
                }
            });
        } catch (Exception ex) {
            ex.printStackTrace();
            // TODO: 2019-06-16 General error?
            _loginCallback.onError(GigyaError.generalError());
        }
    }
}