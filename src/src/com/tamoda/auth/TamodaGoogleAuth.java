package com.tamoda.auth;

import android.app.Activity;
import android.content.Intent;
import java.lang.reflect.Method;
import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.*;

@DesignerComponent(
    version = 2,
    description = "Tamoda Google Auth V2 - Debug Mode. Menampilkan Kode Error jika gagal.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = ""
)
@SimpleObject(external = true)
public class TamodaGoogleAuth extends AndroidNonvisibleComponent implements ActivityResultListener {
    
    private final Activity activity;
    private Object mGoogleSignInClient;
    private static final int RC_SIGN_IN = 9001;

    public TamodaGoogleAuth(ComponentContainer container) {
        super(container.$form());
        this.activity = (Activity) container.$context();
    }

    @SimpleFunction(description = "Mulai proses login. Gunakan Web Client ID dari Firebase!")
    public void StartLogin(String webClientId) {
        try {
            Class<?> gsoClass = Class.forName("com.google.android.gms.auth.api.signin.GoogleSignInOptions");
            Class<?> gsoBuilderClass = Class.forName("com.google.android.gms.auth.api.signin.GoogleSignInOptions$Builder");
            
            Object defaultSignIn = gsoClass.getField("DEFAULT_SIGN_IN").get(null);
            Object builder = gsoBuilderClass.getConstructor(gsoClass).newInstance(defaultSignIn);

            gsoBuilderClass.getMethod("requestIdToken", String.class).invoke(builder, webClientId);
            gsoBuilderClass.getMethod("requestEmail").invoke(builder);
            
            Object gso = gsoBuilderClass.getMethod("build").invoke(builder);

            Class<?> gsClass = Class.forName("com.google.android.gms.auth.api.signin.GoogleSignIn");
            mGoogleSignInClient = gsClass.getMethod("getClient", Activity.class, gsoClass).invoke(null, activity, gso);

            Class<?> clientClass = Class.forName("com.google.android.gms.auth.api.signin.GoogleSignInClient");
            
            // Logout dulu biar user bisa ganti akun
            clientClass.getMethod("signOut").invoke(mGoogleSignInClient);

            Intent signInIntent = (Intent) clientClass.getMethod("getSignInIntent").invoke(mGoogleSignInClient);
            
            form.registerForActivityResult(this);
            activity.startActivityForResult(signInIntent, RC_SIGN_IN);

        } catch (Exception e) {
            LoginFailed("Init Error: " + e.getMessage());
        }
    }

    @Override
    public void resultReturned(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_SIGN_IN) {
            try {
                Class<?> gsClass = Class.forName("com.google.android.gms.auth.api.signin.GoogleSignIn");
                Object task = gsClass.getMethod("getSignedInAccountFromIntent", Intent.class).invoke(null, data);

                Class<?> taskClass = Class.forName("com.google.android.gms.tasks.Task");
                boolean isSuccessful = (boolean) taskClass.getMethod("isSuccessful").invoke(task);
                
                if (isSuccessful) {
                    Object account = taskClass.getMethod("getResult").invoke(task);
                    Class<?> accountClass = Class.forName("com.google.android.gms.auth.api.signin.GoogleSignInAccount");
                    
                    String idToken = (String) accountClass.getMethod("getIdToken").invoke(account);
                    String email = (String) accountClass.getMethod("getEmail").invoke(account);
                    String displayName = (String) accountClass.getMethod("getDisplayName").invoke(account);
                    
                    LoginSuccess(idToken != null ? idToken : "", email != null ? email : "", displayName != null ? displayName : "");
                } else {
                    // AMBIL KODE ERROR DARI EXCEPTION
                    Object exception = taskClass.getMethod("getException").invoke(task);
                    if (exception != null) {
                        Class<?> apiExceptionClass = Class.forName("com.google.android.gms.common.api.ApiException");
                        int statusCode = (int) apiExceptionClass.getMethod("getStatusCode").invoke(exception);
                        LoginFailed("Google Error Code: " + statusCode);
                    } else {
                        LoginFailed("Login dibatalkan atau tidak ada respon.");
                    }
                }
            } catch (Exception e) {
                LoginFailed("Result Error: " + e.getMessage());
            }
        }
    }

    @SimpleEvent(description = "Berhasil Login.")
    public void LoginSuccess(String idToken, String email, String nama) {
        EventDispatcher.dispatchEvent(this, "LoginSuccess", idToken, email, nama);
    }

    @SimpleEvent(description = "Gagal Login.")
    public void LoginFailed(String errorMessage) {
        EventDispatcher.dispatchEvent(this, "LoginFailed", errorMessage);
    }
}
