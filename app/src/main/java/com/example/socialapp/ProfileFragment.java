package com.example.socialapp;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.appwrite.Client;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.InputFile;
import io.appwrite.services.Account;
import io.appwrite.services.Databases;
import io.appwrite.services.Storage;

public class ProfileFragment extends Fragment {
    private ImageView photoImageView;
    private Button changePhotoButton;
    private TextView displayNameTextView, emailTextView;
    private NavController navController;
    private Client client;
    private Account account;
    private String userId;

    // Launcher para seleccionar imagen desde la galería
    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    // Mostrar la imagen seleccionada como previsualización
                    Glide.with(requireView()).load(uri).into(photoImageView);
                    // Subir la imagen a Appwrite
                    uploadProfileImage(uri);
                }
            }
    );

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);
        photoImageView = view.findViewById(R.id.photoImageView);
        changePhotoButton = view.findViewById(R.id.changePhotoButton);
        displayNameTextView = view.findViewById(R.id.displayNameTextView);
        emailTextView = view.findViewById(R.id.emailTextView);

        // Inicializar el cliente de Appwrite
        client = new Client(requireContext()).setProject(getString(R.string.APPWRITE_PROJECT_ID));
        account = new Account(client);
        Databases databases = new Databases(client);

        // Obtener los datos del usuario (nombre, email y foto)
        try {
            account.get(new CoroutineCallback<>((result, error) -> {
                if (error != null) {
                    error.printStackTrace();
                    return;
                }
                // Actualizar la UI con la información del usuario
                userId = result.getId();
                displayNameTextView.setText(result.getName().toString());
                emailTextView.setText(result.getEmail().toString());
                // Si el usuario ya tiene una foto de perfil guardada, cargarla;
                // obtener imagen de la base de datos
                try {
                    databases.getDocument(
                            getString(R.string.APPWRITE_DATABASE_ID),
                            getString(R.string.APPWRITE_USER_COLLECTION_ID),
                            userId,
                            new CoroutineCallback<>((userInfo, error2) -> {
                                if (error2 != null) {
                                    System.out.println("No se ha podido obtener la información del usuario");
                                    error2.printStackTrace();
                                    return;
                                }

                                // Obtener los datos del documento como un Map
                                Map<String, Object> userData = userInfo.getData();
                                String imageUrl = userData.get("profileImage") != null ? userData.get("profileImage").toString() : "";

                                // Actualizar la UI en el hilo principal
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    if (!imageUrl.isEmpty()) {
                                        Glide.with(requireView()).load(imageUrl).into(photoImageView);
                                    } else {
                                        Glide.with(requireView()).load(R.drawable.user).into(photoImageView);
                                    }
                                });
                            })
                    );
                } catch (AppwriteException e) {
                    throw new RuntimeException(e);
                }


                new Handler(Looper.getMainLooper()).post(() ->
                        Glide.with(requireView()).load(R.drawable.user).into(photoImageView)
                );
            }));
        } catch (AppwriteException e) {
            e.printStackTrace();
        }

        // Al presionar el botón "Cambiar foto", se abre la galería para seleccionar una imagen
        changePhotoButton.setOnClickListener(v -> galleryLauncher.launch("image/*"));
    }

    // Convierte la URI en un archivo temporal para poder subirlo a Appwrite
    public File getFileFromUri(Context context, Uri uri) throws Exception {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            throw new java.io.FileNotFoundException("No se pudo abrir el URI: " + uri);
        }
        String fileName = getFileName(context, uri);
        File tempFile = new File(context.getCacheDir(), fileName);
        FileOutputStream outputStream = new FileOutputStream(tempFile);
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, length);
        }
        outputStream.close();
        inputStream.close();
        return tempFile;
    }

    // Obtiene el nombre del archivo a partir de la URI
    private String getFileName(Context context, Uri uri) {
        String fileName = "temp_file";
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex);
                }
            }
        }
        return fileName;
    }

    // Sube la imagen de perfil a Appwrite Storage y actualiza la foto del usuario
    private void uploadProfileImage(Uri imageUri) {
        try {
            File tempFile = getFileFromUri(requireContext(), imageUri);
            Storage storage = new Storage(client);
            storage.createFile(
                    getString(R.string.APPWRITE_STORAGE_BUCKET_ID), // Bucket ID configurado en Appwrite
                    "unique()", // Se genera un ID único para el archivo
                    InputFile.Companion.fromFile(tempFile),
                    new ArrayList<>(), // Permisos (opcional)
                    new CoroutineCallback<>((result, error) -> {
                        if (error != null) {
                            error.printStackTrace();
                            return;
                        }
                        // Se obtiene el ID del archivo subido y se construye la URL de descarga
                        String fileId = result.getId();
                        String imageUrl = "https://cloud.appwrite.io/v1/storage/buckets/" +
                                getString(R.string.APPWRITE_STORAGE_BUCKET_ID) + "/files/" + fileId +
                                "/view?project=" + getString(R.string.APPWRITE_PROJECT_ID) + "&mode=admin";

                        // Vincular la imagen al usuario: actualizamos su documento en la base de datos
                        try {
                            updateUserProfileImage(imageUrl);
                        } catch (AppwriteException e) {
                            throw new RuntimeException(e);
                        }

                        // Actualizar la UI con la nueva imagen
                        new Handler(Looper.getMainLooper()).post(() ->
                                Glide.with(requireView()).load(imageUrl).into(photoImageView)
                        );
                    })
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Actualiza el campo "fotoPerfil" del documento del usuario en la base de datos
    private void updateUserProfileImage(String imageUrl) throws AppwriteException {
        Databases databases = new Databases(client);
        Map<String, Object> data = new HashMap<>();
        data.put("profileImage", imageUrl);
        String collectionId = getString(R.string.APPWRITE_USER_COLLECTION_ID);
        String documentId = userId;

        databases.updateDocument(
                getString(R.string.APPWRITE_DATABASE_ID),
                collectionId,
                documentId,
                data,
                new ArrayList<>(), // Permisos (opcional)
                new CoroutineCallback<>((result, error) -> {
                    if (error != null) {
                        error.printStackTrace();
                        return;
                    }
                    // Actualización exitosa del documento del usuario
                })
        );
    }
}
