package com.example.socialapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.appwrite.Client;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.services.Account;
import io.appwrite.services.Databases;

public class RegisterFragment extends Fragment {
    private EditText usernameEditText, emailEditText, passwordEditText;
    private Button registerButton;
    private Client client;
    private NavController navController;
    private String userId;

    public RegisterFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);
        usernameEditText = view.findViewById(R.id.usernameEditText);
        emailEditText = view.findViewById(R.id.emailEditText);
        passwordEditText = view.findViewById(R.id.passwordEditText);
        registerButton = view.findViewById(R.id.registerButton);

        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                crearCuenta();
            }
        });
    }

    private void crearCuenta() {
        if (!validarFormulario()) {
            return;
        }
        registerButton.setEnabled(false);
        client = new Client(requireActivity().getApplicationContext());
        client.setProject(getString(R.string.APPWRITE_PROJECT_ID));
        Account account = new Account(client);
        Handler mainHandler = new Handler(Looper.getMainLooper());

        try {
            account.create(
                    "unique()", // userId, aunque luego usaremos el id generado para el documento
                    emailEditText.getText().toString(), // email
                    passwordEditText.getText().toString(), // password
                    usernameEditText.getText().toString(), // name (opcional)
                    new CoroutineCallback<>((result, error) -> {
                        mainHandler.post(() -> registerButton.setEnabled(true));
                        if (error != null) {
                            Snackbar.make(requireView(), "Error: " + error.toString(), Snackbar.LENGTH_LONG).show();
                            return;
                        }
                        // Guardamos el id del usuario creado
                        userId = result.getId().toString();

                        // Creamos la sesión con el nuevo usuario
                        account.createEmailPasswordSession(
                                emailEditText.getText().toString(), // email
                                passwordEditText.getText().toString(), // password
                                new CoroutineCallback<>((result2, error2) -> {
                                    if (error2 != null) {
                                        Snackbar.make(requireView(), "Error: " + error2.toString(), Snackbar.LENGTH_LONG).show();
                                    } else {
                                        System.out.println("Sesión creada para el usuario:" + result2.toString());
                                        // Creamos el documento de perfil en la colección de usuarios
                                        createUserProfileDocument(userId);
                                        mainHandler.post(() -> actualizarUI("Ok"));
                                    }
                                })
                        );
                    })
            );
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }
    }

    // Crea el documento en la colección de usuarios
    private void createUserProfileDocument(String userId) {
        Databases databases = new Databases(client);
        Map<String, Object> data = new HashMap<>();
        data.put("uid", userId);
        data.put("fotoPerfil", null);

        // Crear el documento
        try {
            databases.createDocument(
                    getString(R.string.APPWRITE_DATABASE_ID),
                    getString(R.string.APPWRITE_USER_COLLECTION_ID),
                    userId, // Usamos el mismo userId para el documento
                    data,
                    new ArrayList<>(),
                    new CoroutineCallback<>((result, error) -> {
                        if (error != null) {
                            System.out.println("Error al crear el documento de perfil: " + error.getMessage());
                            return;
                        }
                        System.out.println("Documento de perfil creado: " + result.toString());
                    })
            );
        } catch (AppwriteException e) {
            e.printStackTrace();
        }
    }

    private void actualizarUI(String currentUser) {
        if (currentUser != null) {
            navController.navigate(R.id.homeFragment);
        }
    }

    private boolean validarFormulario() {
        boolean valid = true;
        if (TextUtils.isEmpty(emailEditText.getText().toString())) {
            emailEditText.setError("Required.");
            valid = false;
        } else {
            emailEditText.setError(null);
        }
        if (TextUtils.isEmpty(passwordEditText.getText().toString())) {
            passwordEditText.setError("Required.");
            valid = false;
        } else {
            passwordEditText.setError(null);
        }
        return valid;
    }
}
