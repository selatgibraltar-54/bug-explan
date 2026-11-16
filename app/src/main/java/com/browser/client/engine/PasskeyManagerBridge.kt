package com.browser.client.engine

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PasswordCredential
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException

sealed class PasskeyGetResult {
    data class PublicKeyJson(val authenticationResponseJson: String) : PasskeyGetResult()
    data class Password(val id: String, val password: String) : PasskeyGetResult()
    data object Canceled : PasskeyGetResult()
    data class FallbackRequired(val reason: String) : PasskeyGetResult()
}

sealed class PasskeyCreateResult {
    data class RegistrationJson(val registrationResponseJson: String) : PasskeyCreateResult()
    data object Canceled : PasskeyCreateResult()
    data class FallbackRequired(val reason: String) : PasskeyCreateResult()
}

enum class GooglePasswordManagerLaunchResult {
    OPENED_NATIVE,
    OPENED_WEB_FALLBACK,
    FAILED
}

object PasskeyManagerBridge {

    const val GOOGLE_PASSWORD_MANAGER_WEB_URL = "https://passwords.google.com"

    private const val GMS_PACKAGE = "com.google.android.gms"
    private const val ACTION_OPEN_CREDENTIAL_MANAGER =
        "com.google.android.gms.credential.manager.ACTION_OPEN_CREDENTIAL_MANAGER"
    private const val GMS_PASSWORD_MANAGER_ACTIVITY =
        "com.google.android.gms.credential.manager.PasswordManagerActivity"
    private const val GMS_VIEW_SETTINGS_ACTION =
        "com.google.android.gms.accountsettings.action.VIEW_SETTINGS"

    suspend fun getPasskeyOrPassword(
        context: Context,
        requestJson: String
    ): PasskeyGetResult {
        val uiContext = findActivity(context) ?: context
        if (requestJson.isBlank()) {
            return PasskeyGetResult.FallbackRequired("Passkey verification link requires Google Password Manager.")
        }
        return try {
            val credentialManager = CredentialManager.create(uiContext)
            val options = listOf(
                GetPublicKeyCredentialOption(requestJson = requestJson),
                GetPasswordOption()
            )
            val request = GetCredentialRequest(options)
            val response = credentialManager.getCredential(
                context = uiContext,
                request = request
            )
            when (val credential = response.credential) {
                is PublicKeyCredential -> {
                    PasskeyGetResult.PublicKeyJson(credential.authenticationResponseJson)
                }
                is PasswordCredential -> {
                    PasskeyGetResult.Password(credential.id, credential.password)
                }
                else -> {
                    PasskeyGetResult.FallbackRequired("Unsupported credential type returned.")
                }
            }
        } catch (_: GetCredentialCancellationException) {
            PasskeyGetResult.Canceled
        } catch (e: NoCredentialException) {
            PasskeyGetResult.FallbackRequired(
                e.errorMessage?.toString()
                    ?: "No saved passkey matched in Credential Manager."
            )
        } catch (e: GetCredentialException) {
            PasskeyGetResult.FallbackRequired(
                e.errorMessage?.toString()
                    ?: e.message
                    ?: "Credential Manager could not complete passkey verification in WebView."
            )
        } catch (e: SecurityException) {
            PasskeyGetResult.FallbackRequired(
                e.message ?: "Passkey origin check requires Google Password Manager."
            )
        } catch (t: Throwable) {
            PasskeyGetResult.FallbackRequired(
                t.message ?: "Passkey prompt requires Google Password Manager."
            )
        }
    }

    suspend fun createPasskey(
        context: Context,
        requestJson: String
    ): PasskeyCreateResult {
        val uiContext = findActivity(context) ?: context
        if (requestJson.isBlank()) {
            return PasskeyCreateResult.FallbackRequired("Passkey registration requires Google Password Manager.")
        }
        return try {
            val credentialManager = CredentialManager.create(uiContext)
            val request = CreatePublicKeyCredentialRequest(requestJson = requestJson)
            val response = credentialManager.createCredential(
                context = uiContext,
                request = request
            )
            val pubKeyResponse = response as? CreatePublicKeyCredentialResponse
            if (pubKeyResponse != null) {
                PasskeyCreateResult.RegistrationJson(pubKeyResponse.registrationResponseJson)
            } else {
                PasskeyCreateResult.FallbackRequired("Passkey creation response was empty.")
            }
        } catch (_: CreateCredentialCancellationException) {
            PasskeyCreateResult.Canceled
        } catch (e: CreateCredentialException) {
            PasskeyCreateResult.FallbackRequired(
                e.errorMessage?.toString()
                    ?: e.message
                    ?: "Credential Manager could not save passkey from WebView."
            )
        } catch (e: SecurityException) {
            PasskeyCreateResult.FallbackRequired(
                e.message ?: "Passkey registration requires Google Password Manager."
            )
        } catch (t: Throwable) {
            PasskeyCreateResult.FallbackRequired(
                t.message ?: "Passkey setup requires Google Password Manager."
            )
        }
    }

    fun openGooglePasswordManager(
        context: Context,
        onFallbackWebUrl: ((String) -> Unit)? = null
    ): GooglePasswordManagerLaunchResult {
        val activity = findActivity(context)
        val launchContext = activity ?: context
        val pm = context.packageManager

        for (candidate in buildPasswordManagerCandidateIntents()) {
            if (activity == null) {
                candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolved = runCatching {
                pm.resolveActivity(candidate, PackageManager.MATCH_DEFAULT_ONLY)
            }.getOrNull()

            if (resolved != null) {
                val started = runCatching {
                    launchContext.startActivity(candidate)
                }.isSuccess
                if (started) {
                    return GooglePasswordManagerLaunchResult.OPENED_NATIVE
                }
            }
        }

        if (onFallbackWebUrl != null) {
            onFallbackWebUrl(GOOGLE_PASSWORD_MANAGER_WEB_URL)
            return GooglePasswordManagerLaunchResult.OPENED_WEB_FALLBACK
        }

        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(GOOGLE_PASSWORD_MANAGER_WEB_URL)).apply {
            if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val webResolved = runCatching {
            pm.resolveActivity(webIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull()
        if (webResolved != null && runCatching { launchContext.startActivity(webIntent) }.isSuccess) {
            return GooglePasswordManagerLaunchResult.OPENED_WEB_FALLBACK
        }

        return GooglePasswordManagerLaunchResult.FAILED
    }

    fun buildPasswordManagerCandidateIntents(): List<Intent> = buildList {
        add(
            Intent(ACTION_OPEN_CREDENTIAL_MANAGER).apply {
                setPackage(GMS_PACKAGE)
            }
        )
        add(
            Intent().apply {
                setPackage(GMS_PACKAGE)
                component = ComponentName(GMS_PACKAGE, GMS_PASSWORD_MANAGER_ACTIVITY)
            }
        )
        add(
            Intent(GMS_VIEW_SETTINGS_ACTION).apply {
                setPackage(GMS_PACKAGE)
                putExtra("extra.screenId", 408)
            }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(Intent(Settings.ACTION_CREDENTIAL_PROVIDER))
        }
    }

    fun isPasskeyUri(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase()
        if (lower.startsWith("fido:") ||
            lower.startsWith("webauthn:") ||
            lower.startsWith("passkey:")
        ) {
            return true
        }
        if (lower.startsWith("intent://")) {
            return lower.contains("fido") ||
                lower.contains("webauthn") ||
                lower.contains("passkey") ||
                lower.contains("credential.manager") ||
                lower.contains("passwordmanageractivity")
        }
        return false
    }

    private tailrec fun findActivity(context: Context?): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> findActivity(context.baseContext)
        else -> null
    }

    const val PASSKEY_INTERCEPTOR_JS = """
        (function() {
            try {
                if (window.__clearpassPasskeyPatched) return;
                window.__clearpassPasskeyPatched = true;

                function bufferToBase64Url(value) {
                    var bytes = null;
                    if (value instanceof ArrayBuffer) {
                        bytes = new Uint8Array(value);
                    } else if (ArrayBuffer.isView(value)) {
                        bytes = new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
                    }
                    if (!bytes) return value;
                    var binary = '';
                    for (var i = 0; i < bytes.length; i++) {
                        binary += String.fromCharCode(bytes[i]);
                    }
                    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+${'$'}/, '');
                }

                function base64UrlToBuffer(str) {
                    if (typeof str !== 'string') return str;
                    var base64 = str.replace(/-/g, '+').replace(/_/g, '/');
                    while (base64.length % 4 !== 0) base64 += '=';
                    var binary = atob(base64);
                    var bytes = new Uint8Array(binary.length);
                    for (var i = 0; i < binary.length; i++) {
                        bytes[i] = binary.charCodeAt(i);
                    }
                    return bytes.buffer;
                }

                function serializeWebAuthn(input) {
                    if (input === null || input === undefined) return input;
                    if (input instanceof ArrayBuffer || ArrayBuffer.isView(input)) {
                        return bufferToBase64Url(input);
                    }
                    if (Array.isArray(input)) {
                        return input.map(serializeWebAuthn);
                    }
                    if (typeof input === 'object') {
                        var out = {};
                        var keys = Object.keys(input);
                        for (var i = 0; i < keys.length; i++) {
                            var k = keys[i];
                            var v = input[k];
                            if (v !== undefined && typeof v !== 'function') {
                                out[k] = serializeWebAuthn(v);
                            }
                        }
                        return out;
                    }
                    return input;
                }

                function hydrateCredential(jsonStr) {
                    var raw = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr;
                    var resp = raw && raw.response ? Object.assign({}, raw.response) : {};
                    if (typeof resp.clientDataJSON === 'string') {
                        resp.clientDataJSON = base64UrlToBuffer(resp.clientDataJSON);
                    }
                    if (typeof resp.authenticatorData === 'string') {
                        resp.authenticatorData = base64UrlToBuffer(resp.authenticatorData);
                    }
                    if (typeof resp.signature === 'string') {
                        resp.signature = base64UrlToBuffer(resp.signature);
                    }
                    if (typeof resp.userHandle === 'string') {
                        resp.userHandle = base64UrlToBuffer(resp.userHandle);
                    }
                    if (typeof resp.attestationObject === 'string') {
                        resp.attestationObject = base64UrlToBuffer(resp.attestationObject);
                    }
                    if (!resp.getTransports) {
                        var transports = (raw.response && raw.response.transports) || ['internal', 'hybrid'];
                        resp.getTransports = function() { return transports; };
                    }
                    if (resp.attestationObject && !resp.getAuthenticatorData && resp.authenticatorData) {
                        resp.getAuthenticatorData = function() { return resp.authenticatorData; };
                    }
                    var ext = (raw && raw.clientExtensionResults) || {};
                    return {
                        id: raw.id,
                        rawId: typeof raw.rawId === 'string' ? base64UrlToBuffer(raw.rawId) : base64UrlToBuffer(raw.id),
                        type: raw.type || 'public-key',
                        authenticatorAttachment: raw.authenticatorAttachment || 'platform',
                        response: resp,
                        getClientExtensionResults: function() { return ext; },
                        toJSON: function() { return raw; }
                    };
                }

                window.__clearpassPasskeyCalls = window.__clearpassPasskeyCalls || {};

                window.__clearpassResolvePasskey = function(callId, jsonStr) {
                    var entry = window.__clearpassPasskeyCalls[callId];
                    if (!entry) return;
                    delete window.__clearpassPasskeyCalls[callId];
                    try {
                        entry.resolve(hydrateCredential(jsonStr));
                    } catch (err) {
                        entry.reject(new DOMException(err && err.message ? err.message : 'Invalid passkey payload', 'OperationError'));
                    }
                };

                window.__clearpassRejectPasskey = function(callId, reason) {
                    var entry = window.__clearpassPasskeyCalls[callId];
                    if (!entry) return;
                    delete window.__clearpassPasskeyCalls[callId];
                    entry.reject(new DOMException(reason || 'Passkey verification cancelled', 'NotAllowedError'));
                };

                function dispatchPasskey(mode, publicKeyOptions, fallbackFn, originalArgs) {
                    if (window.ClearPassPasskeyBridge && typeof window.ClearPassPasskeyBridge.onPasskeyRequested === 'function') {
                        return new Promise(function(resolve, reject) {
                            var callId = 'pk_' + Date.now() + '_' + Math.floor(Math.random() * 100000);
                            window.__clearpassPasskeyCalls[callId] = { resolve: resolve, reject: reject };
                            try {
                                var serialized = JSON.stringify(serializeWebAuthn(publicKeyOptions));
                                window.ClearPassPasskeyBridge.onPasskeyRequested(
                                    callId,
                                    mode,
                                    serialized,
                                    window.location.href || ''
                                );
                            } catch (e) {
                                delete window.__clearpassPasskeyCalls[callId];
                                if (fallbackFn) {
                                    fallbackFn.apply(navigator.credentials, originalArgs).then(resolve, reject);
                                } else {
                                    reject(e);
                                }
                            }
                        });
                    }
                    if (fallbackFn) {
                        return fallbackFn.apply(navigator.credentials, originalArgs);
                    }
                    return Promise.reject(new DOMException('Passkeys not supported', 'NotSupportedError'));
                }

                if (navigator.credentials) {
                    var origGet = typeof navigator.credentials.get === 'function'
                        ? navigator.credentials.get.bind(navigator.credentials)
                        : null;
                    var origCreate = typeof navigator.credentials.create === 'function'
                        ? navigator.credentials.create.bind(navigator.credentials)
                        : null;

                    navigator.credentials.get = function(options) {
                        if (options && options.publicKey) {
                            return dispatchPasskey('get', options.publicKey, origGet, arguments);
                        }
                        if (origGet) {
                            return origGet.apply(navigator.credentials, arguments);
                        }
                        return Promise.reject(new DOMException('Credentials get unavailable', 'NotSupportedError'));
                    };

                    navigator.credentials.create = function(options) {
                        if (options && options.publicKey) {
                            return dispatchPasskey('create', options.publicKey, origCreate, arguments);
                        }
                        if (origCreate) {
                            return origCreate.apply(navigator.credentials, arguments);
                        }
                        return Promise.reject(new DOMException('Credentials create unavailable', 'NotSupportedError'));
                    };
                }
            } catch (e) {}
        })();
    """
}
