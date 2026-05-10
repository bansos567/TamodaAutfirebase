package com.tamoda.auth;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.*;

@DesignerComponent(
    version = 1,
    description = "Tamoda Pure Google Auth. Login Native super ringan (Tanpa Jar/Bentrok) untuk ditarik ID Token-nya ke Firebase Web.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = ""
)
@SimpleObject(external = true)
public class TamodaGoogleAuth extends AndroidNonvisibleComponent implements ActivityResultListener {
    
    private ComponentContainer container;
    private Activity activity;
    private Object mGoogleSignInClient;
    private static final int RC_SIGN_IN = 9001;

    public TamodaGoogleAuth(ComponentContainer container) {
        super(container.$form());
        this.container = container;
        this.activity = (Activity) container.$context();
    }

    @SimpleFunction(description = "Mulai proses login Google. Masukkan Web Client ID dari Firebase Console.")
    public void StartLogin(String webClientId) {
        try {
            // Menggunakan Reflection untuk meminjam library GMS bawaan HP/Kodular
            Class<?> gsoClass = Class.forName("com.google.android.gms.auth.api.signin.GoogleSignInOptions");
            Class<?> gsoBuilderClass = Class.forName("com.google.android.gms.auth.api.signin.GoogleSignInOptions$Builder");
            
            Object defaultSignIn = gsoClass.getField("DEFAULT_SIGN_IN").get(null);
            Object builder = gsoBuilderClass.getConstructor(gsoClass).newInstance(defaultSignIn);

            // Request Token untuk dilempar ke Firebase Web
            gsoBuilderClass.getMethod("requestIdToken", String.class).invoke(builder, webClientId);
            gsoBuilderClass.getMethod("requestEmail").invoke(builder);
            
            Object gso = gsoBuilderClass.getMethod("build").invoke(builder);

            Class<?> gsClass = Class.forName("com.google.android.gms.auth.api.signin.GoogleSignIn");
            mGoogleSignInClient = gsClass.getMethod("getClient", Activity.class, gsoClass).invoke(null, activity, gso);

            Class<?> clientClass = Class.forName("com.google.android.gms.auth.api.signin.GoogleSignInClient");
            
            // Sign out paksa di awal biar user selalu bisa pilih email (Bisa ganti akun)
            clientClass.getMethod("signOut").invoke(mGoogleSignInClient);

            Intent signInIntent = (Intent) clientClass.getMethod("getSignInIntent").invoke(mGoogleSignInClient);
            
            container.$form().registerForActivityResult(this);
            activity.startActivityForResult(signInIntent, RC_SIGN_IN);

        } catch (Exception e) {
            LoginFailed("Gagal inisialisasi GMS: " + e.getMessage());
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
                    LoginFailed("Login dibatalkan oleh user atau koneksi terputus.");
                }
            } catch (Exception e) {
                LoginFailed("Gagal membaca akun: " + e.getMessage());
            }
        }
    }

    @SimpleEvent(description = "Berhasil Login. Tangkap ID Token ini lalu lempar ke JavaScript (Web).")
    public void LoginSuccess(String idToken, String email, String nama) {
        EventDispatcher.dispatchEvent(this, "LoginSuccess", idToken, email, nama);
    }

    @SimpleEvent(description = "Gagal Login.")
    public void LoginFailed(String errorMessage) {
        EventDispatcher.dispatchEvent(this, "LoginFailed", errorMessage);
    }
    
    @SimpleFunction(description = "Logout akun Google dari HP.")
    public void Logout() {
        try {
            if (mGoogleSignInClient != null) {
                Class<?> clientClass = Class.forName("com.google.android.gms.auth.api.signin.GoogleSignInClient");
                clientClass.getMethod("signOut").invoke(mGoogleSignInClient);
            }
        } catch (Exception e) {
            // Abaikan jika error
        }
    }
}
