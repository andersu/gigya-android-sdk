package com.gigya.android.sdk.services;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;

import com.gigya.android.sdk.GigyaCallback;
import com.gigya.android.sdk.GigyaDefinitions;
import com.gigya.android.sdk.GigyaLogger;
import com.gigya.android.sdk.GigyaLoginCallback;
import com.gigya.android.sdk.api.GigyaApi;
import com.gigya.android.sdk.api.GigyaConfigApi;
import com.gigya.android.sdk.api.bloc.BlocHandler;
import com.gigya.android.sdk.model.account.GigyaAccount;
import com.gigya.android.sdk.model.account.SessionInfo;
import com.gigya.android.sdk.network.GigyaApiResponse;
import com.gigya.android.sdk.network.GigyaError;
import com.gigya.android.sdk.network.adapter.NetworkAdapter;
import com.gigya.android.sdk.providers.LoginProvider;
import com.gigya.android.sdk.utils.ObjectUtils;
import com.google.gson.Gson;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ApiService<A extends GigyaAccount> {

    private static final String LOG_TAG = "ApiService";

    /*
    Network adapter provided for handling HTTP requests & results.
     */
    final private NetworkAdapter _adapter;

    /*
    Required services for API integrity and logic.
     */
    final private SessionService _sessionService;
    final private AccountService<A> _accountService;

    public boolean isInterruptionsEnabled() {
        return _sessionService.getConfig().isInterruptionsEnabled();
    }

    /*
    Business login handler for interruption enabled Apis.
     */
    private BlocHandler _blocHandler;

    public ApiService(Context appContext, SessionService sessionService, AccountService<A> accountService) {
        _sessionService = sessionService;
        _accountService = accountService;
        _adapter = new NetworkAdapter(appContext, new NetworkAdapter.IConfigurationBlock() {
            @Override
            public void onMissingConfiguration() {
                if (_sessionService.getConfig().getGmid() == null) {
                    // Fetch new config. Completion handler is not needed.
                    loadConfig(null);
                }
            }
        });
        _blocHandler = new BlocHandler(this);
    }

    //region Available APIs

    /**
     * Request SDK configuration. Crucial Endpoint because of the "gmid", appIds dependencies.
     *
     * @param completionHandler Custom completion Runnable.
     */
    public void loadConfig(@Nullable final Runnable completionHandler) {
        GigyaLogger.debug(LOG_TAG, "loadConfig: ");
        new GigyaConfigApi<>(_adapter, _sessionService, _accountService).execute(new GigyaCallback<GigyaApiResponse>() {
            @Override
            public void onSuccess(GigyaApiResponse obj) {
                if (completionHandler != null) {
                    completionHandler.run();
                }
            }

            @Override
            public void onError(GigyaError error) {
                GigyaLogger.error(LOG_TAG, "GigyaConfigApi error: " + error.toString());
            }
        });
    }

    /**
     * General "anonymous" request.
     * API succession is dependant on provided parameters.
     *
     * @param api      Requested Gigya API.
     * @param params   Request parameters.
     * @param callback Response callback.
     */
    public void send(String api, Map<String, Object> params, final GigyaCallback<GigyaApiResponse> callback) {
        new GigyaApi<>(_adapter, _sessionService, _accountService, GigyaApiResponse.class)
                .execute(api, NetworkAdapter.Method.POST, params, callback);
    }

    /**
     * General "anonymous" request with requested response object scheme.
     * API succession is dependant on provided parameters.
     *
     * @param api      Requested Gigya API.
     * @param params   Request parameters.
     * @param clazz    Response class scheme.
     * @param callback Response callback.
     * @param <V>      Generic response scheme.
     */
    public <V> void send(String api, Map<String, Object> params, Class<V> clazz, final GigyaCallback<V> callback) {
        new GigyaApi<>(_adapter, _sessionService, _accountService, clazz)
                .execute(api, NetworkAdapter.Method.POST, params, callback);
    }

    /**
     * Request to log out of current session.
     */
    public void logout() {
        new GigyaApi<>(_adapter, _sessionService, _accountService, GigyaApiResponse.class)
                .execute(GigyaDefinitions.API.API_LOGOUT, NetworkAdapter.Method.POST, null, null);
    }

    /**
     * Request account login according to specified parameters.
     * Will invalidate the cached account on success.
     *
     * @param params        Request parameters.
     * @param loginCallback Login response callback.
     */
    public void login(Map<String, Object> params, final GigyaLoginCallback<? extends GigyaAccount> loginCallback) {
        new GigyaApi<A, A>(_adapter, _sessionService, _accountService, _accountService.getAccountScheme()) {
            @Override
            public void onRequestSuccess(@NonNull String api, GigyaApiResponse apiResponse, @Nullable GigyaCallback<A> callback) {
                //TODO Handle interruption.
                if (callback != null) {
                    callback.onSuccess(onAccountBasedApiSuccess(apiResponse, callback));
                }
            }

            @Override
            public void onRequestError(String api, GigyaApiResponse apiResponse, @Nullable GigyaCallback<A> callback) {
                if (!_blocHandler.evaluateInterruptionError(apiResponse, loginCallback)) {
                    loginCallback.onError(GigyaError.fromResponse(apiResponse));
                }

            }
        }.execute(GigyaDefinitions.API.API_LOGIN, NetworkAdapter.Method.GET, params, (GigyaCallback<A>) loginCallback);
    }

    /**
     * Request account info.
     * Will return the cached account according to the caching policy. If a new request is needed will refresh the
     * account information from the server.
     *
     * @param callback Response callback.
     */
    public void getAccount(final GigyaCallback<? extends GigyaAccount> callback) {
        if (_accountService.isCachedAccount()) {
            // Always return a deep copy.
            ((GigyaCallback<A>) callback).onSuccess(ObjectUtils.deepCopy(new Gson(), _accountService.getAccount(), _accountService.getAccountScheme()));
        } else {
            new GigyaApi<A, A>(_adapter, _sessionService, _accountService, _accountService.getAccountScheme()) {

                @Override
                public void onRequestSuccess(@NonNull String api, GigyaApiResponse apiResponse, @Nullable GigyaCallback<A> callback) {
                    final A parsed = apiResponse.getGson().fromJson(apiResponse.asJson(), _accountService.getAccountScheme());
                    _accountService.setAccount(ObjectUtils.deepCopy(apiResponse.getGson(), parsed, _accountService.getAccountScheme()));
                    if (callback != null) {
                        callback.onSuccess(parsed);
                    }
                }
            }.execute(GigyaDefinitions.API.API_GET_ACCOUNT_INFO, NetworkAdapter.Method.POST,
                    null, (GigyaCallback<A>) callback);
        }
    }

    /**
     * Request account information update given an updated Account object reference.
     *
     * @param updatedAccount Updated account object.
     * @param callback       Response callback.
     */
    public void setAccount(A updatedAccount, final GigyaCallback<? extends GigyaAccount> callback) {
        new GigyaApi<A, A>(_adapter, _sessionService, _accountService, _accountService.getAccountScheme()) {
            @Override
            public void onRequestSuccess(@NonNull String api, GigyaApiResponse apiResponse, GigyaCallback<A> callback) {
                // Chain getAccount call. Invalidate account so getAccount call will refresh info from server.
                _accountService.invalidateAccount();
                getAccount(callback);
            }
        }.execute(GigyaDefinitions.API.API_SET_ACCOUNT_INFO, NetworkAdapter.Method.POST,
                _accountService.calculateDiff(new Gson(), _accountService.getAccount(), updatedAccount), (GigyaCallback<A>) callback);
    }

    public void notifyLogin(String providerSessions) {

    }

    // TODO: 05/03/2019 Waiting for endpoint implementation. 
    public void nativeLogin() {

    }

    /**
     * Request account registration.
     *
     * @param params        Request parameters.
     * @param loginCallback Response callback.
     */
    public void register(final Map<String, Object> params, final GigyaLoginCallback<? extends GigyaAccount> loginCallback) {
        new GigyaApi<GigyaApiResponse, A>(_adapter, _sessionService, _accountService, GigyaApiResponse.class) {
            @Override
            public void onRequestSuccess(@NonNull String api, GigyaApiResponse apiResponse, @Nullable GigyaCallback<GigyaApiResponse> callback) {
                // Registration initialized. Can continue to actual registration API.
                final String regToken = (String) apiResponse.getField("regToken");
                if (regToken != null) {
                    params.put("regToken", regToken);
                    params.put("finalizeRegistration", true);
                } else {
                    if (callback != null) {
                        callback.onError(GigyaError.generalError());
                    }
                }
                // Send register API.
                new GigyaApi<A, A>(_adapter, _sessionService, _accountService, _accountService.getAccountScheme()) {
                    @Override
                    public void onRequestSuccess(@NonNull String api, GigyaApiResponse apiResponse, @Nullable GigyaCallback<A> callback) {
                        //TODO Handle interruption.
                        if (callback != null) {
                            callback.onSuccess(onAccountBasedApiSuccess(apiResponse, callback));
                        }
                    }
                }.execute(GigyaDefinitions.API.API_REGISTER, NetworkAdapter.Method.POST, params, (GigyaCallback<A>) loginCallback);
            }

            @Override
            public void onRequestError(String api, GigyaApiResponse apiResponse, @Nullable GigyaCallback<GigyaApiResponse> callback) {
                loginCallback.onError(GigyaError.fromResponse(apiResponse));
            }
        }.execute(GigyaDefinitions.API.API_INIT_REGISTRATION, NetworkAdapter.Method.POST, params, null);
    }

    /**
     * Request to reset password using "forgot password" option.
     *
     * @param email    User email address. Password reset link will be sent to this address.
     * @param callback Response callback.
     */
    public void forgotPassword(String email, final GigyaCallback<GigyaApiResponse> callback) {
        Map<String, Object> params = new HashMap<>();
        params.put("loginID", email);
        new GigyaApi<>(_adapter, _sessionService, _accountService, GigyaApiResponse.class)
                .execute(GigyaDefinitions.API.API_RESET_PASSWORD, NetworkAdapter.Method.POST, params, callback);
    }

    /**
     * Request to refresh a native provider session in Gigya servers.
     *
     * @param providerSession     Provider session structure as String.
     * @param permissionCallbacks Login permissions callback.
     */
    public void refreshNativeProviderSession(String providerSession, @Nullable final LoginProvider.LoginPermissionCallbacks permissionCallbacks) {
        Map<String, Object> params = new HashMap<>();
        params.put("providerSession", providerSession);
        new GigyaApi<>(_adapter, _sessionService, _accountService, GigyaApiResponse.class)
                .execute(GigyaDefinitions.API.API_REFRESH_PROVIDER_SESSION, NetworkAdapter.Method.POST, params, new GigyaCallback<GigyaApiResponse>() {
                    @Override
                    public void onSuccess(GigyaApiResponse obj) {
                        if (permissionCallbacks != null) {
                            permissionCallbacks.granted();
                        }
                        // Account invalidation required.
                        _accountService.invalidateAccount();
                    }

                    @Override
                    public void onError(GigyaError error) {
                        if (permissionCallbacks != null) {
                            permissionCallbacks.failed(error.getLocalizedMessage());
                        }
                    }
                });
    }

    /**
     * Finalize pending registration/verification using provided RegToken.
     * Response includes full optional account data.
     *
     * @param regToken      Provided registration token.
     * @param loginCallback Login response callback.
     */
    public void finalizeRegistration(String regToken, final GigyaLoginCallback<? extends GigyaAccount> loginCallback, @Nullable final Runnable completionHadler) {
        new GigyaApi<A, A>(_adapter, _sessionService, _accountService, _accountService.getAccountScheme()) {
            @Override
            public void onRequestSuccess(@NonNull String api, GigyaApiResponse apiResponse, @Nullable GigyaCallback<A> callback) {
                super.onRequestSuccess(api, apiResponse, callback);
                if (completionHadler != null) {
                    completionHadler.run();
                }
            }
        }.execute(GigyaDefinitions.API.API_FINALIZE_REGISTRATION, NetworkAdapter.Method.POST,
                ObjectUtils.mapOf(Arrays.asList(
                        new Pair<String, Object>("regToken", regToken),
                        new Pair<String, Object>("include", "profile,data,emails,subscriptions,preferences"),
                        new Pair<String, Object>("includeUserInfo", "true"))),
                (GigyaCallback<A>) loginCallback);
    }

    //endregion

    //region Utilities

    /**
     * Generic success logic for Account based APIs.
     * 1 -> Update the current session if exists in the response body.
     * 2 -> Invalidate the cached account instance in the AccountService.
     *
     * @param apiResponse API response.
     * @param callback    Response callback.
     */
    private A onAccountBasedApiSuccess(GigyaApiResponse apiResponse, GigyaCallback<A> callback) {
        if (apiResponse.contains("SessionInfo")) {
            if (apiResponse.contains("sessionSecret")) {
                final SessionInfo newSession = apiResponse.getField("sessionInfo", SessionInfo.class);
                _sessionService.setSession(newSession);
                _accountService.invalidateAccount();
            }
        }
        A parsed = apiResponse.getGson().fromJson(apiResponse.asJson(), _accountService.getAccountScheme());
        // Update account.
        _accountService.setAccount(ObjectUtils.deepCopy(apiResponse.getGson(), parsed, _accountService.getAccountScheme()));
        return parsed;
    }

    //endregion

}