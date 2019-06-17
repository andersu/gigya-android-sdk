package com.gigya.android.sdk.tfa.resolvers.totp;

import android.support.annotation.NonNull;

public interface IVerifyTOTPResolver {

    void verifyTOTPCode(@NonNull String verificationCode, @NonNull VerifyTOTPResolver.ResultCallback resultCallback);
}
