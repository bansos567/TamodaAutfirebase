package com.tamoda.auth;

import android.app.Activity;
import android.content.Intent;
import java.lang.reflect.Method;
import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.*;

@DesignerComponent(
    version = 3,
    description = "Tamoda Google Auth V3 - Fix Activity Result. Dijamin pasti ngerespon!",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = ""
)
@SimpleObject(external = true)
public class TamodaGoogleAuth extends AndroidNonvisibleComponent implements ActivityResultListener {
    
    private final Activity activity;
    private ComponentContainer container;
    private Object mGoogleSignInClient;
    
    // KUNCI PERBAIKAN: Jangan pakai angka mati, biarkan Kodular yang ngasih nomor tiketnya
    private int requestCode; 

    public TamodaGoogleAuth(ComponentContainer container) {
        super(container.$form());
        this.container = container;
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
            clientClass.getMethod("signOut").invoke(mGoogleSignInClient);

            Intent signInIntent = (Intent) clientClass.getMethod("getSignInIntent").invoke(mGoogleSignInClient);
            
            // --- PERBAIKAN FATAL DI SINI ---
            // Minta Kodular buatkan jalur khusus (Request Code) untuk ekstensi ini
            this.requestCode = container.$form().registerForActivityResult(this);
            
            // Panggil Google dengan tiket resmi dari Kodular
            activity.startActivityForResult(signInIntent, this.requestCode);

        } catch (Exception e) {
            LoginFailed("Init Error: " + e.getMessage());
        }
    }

    @Override
    public void resultReturned(int reqCode, int resultCode, Intent data) {
        // Cek apakah balasan ini beneran untuk ekstensi kita
        if (reqCode == this.requestCode) {
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
                    Object exception = taskClass.getMethod("getException").invoke(task);
                    if (exception != null) {
                        Class<?> apiExceptionClass = Class.forName("com.google.android.gms.common.api.ApiException");
                        int statusCode = (int) apiExceptionClass.getMethod("getStatusCode").invoke(exception);
                        LoginFailed("Google Error Code: " + statusCode);
                    } else {
                        LoginFailed("Login dibatalkan oleh user.");
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
