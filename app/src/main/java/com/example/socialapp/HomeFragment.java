package com.example.socialapp;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.appwrite.Client;
import io.appwrite.Query;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.DocumentList;
import io.appwrite.services.Account;
import io.appwrite.services.Databases;

public class HomeFragment extends Fragment {
    NavController navController;
    ImageView photoImageView;
    TextView displayNameTextView, emailTextView;
    Client client;
    Account account;
    String userId;
    PostsAdapter adapter;
    AppViewModel appViewModel;

    public HomeFragment() {}
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
        appViewModel.setEsComentario(false);
        appViewModel.setParentUid(null);

        NavigationView navigationView = view.getRootView().findViewById(R.id.nav_view);
        View header = navigationView.getHeaderView(0);

        photoImageView = header.findViewById(R.id.imageView);
        displayNameTextView = header.findViewById(R.id.displayNameTextView);
        emailTextView = header.findViewById(R.id.emailTextView);

        client = new Client(requireContext())
                .setProject(getString(R.string.APPWRITE_PROJECT_ID));
        account = new Account(client);
        Handler mainHandler = new Handler(Looper.getMainLooper());

        try {
            account.get(new CoroutineCallback<>((result, error) -> {
                if (error != null) {
                    error.printStackTrace();
                    return;
                }
                mainHandler.post(() ->
                {
                    userId = result.getId();
                    displayNameTextView.setText(result.getName().toString());
                    emailTextView.setText(result.getEmail().toString());

                    // actualizar la foto de perfil en el drawer
                    Databases databases = new Databases(client);
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

                                    // Actualizar la UI
                                    new Handler(Looper.getMainLooper()).post(() -> {
                                        if (!imageUrl.isEmpty()) {
                                            Glide.with(requireView()).load(imageUrl).circleCrop().into(photoImageView);
                                        } else {
                                            Glide.with(requireView()).load(R.drawable.user).circleCrop().into(photoImageView);
                                        }
                                    });
                                })
                        );
                    } catch (AppwriteException e) {
                        throw new RuntimeException(e);
                    }
                    obtenerPosts(); // < – Pedir los posts tras obtener el usuario
                });
            }));
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }


        navController = Navigation.findNavController(view); //<-----------------

        view.findViewById(R.id.gotoNewPostFragmentButton).setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View v) {
                 navController.navigate(R.id.newPostFragment);
             }
        });

        RecyclerView postsRecyclerView = view.findViewById(R.id.postsRecyclerView);
        adapter = new PostsAdapter();
        postsRecyclerView.setAdapter(adapter);
    }

    class PostViewHolder extends RecyclerView.ViewHolder{
        ImageView authorPhotoImageView, likeImageView, mediaImageView, deletePost, commentButton;
        TextView authorTextView, contentTextView, numLikesTextView, timeTextView, commentCountTextView ;

        RecyclerView commentsRecycler;
        PostViewHolder(@NonNull View itemView) {
            super(itemView);
            authorPhotoImageView = itemView.findViewById(R.id.authorPhotoImageView);
            likeImageView = itemView.findViewById(R.id.likeImageView);
            mediaImageView = itemView.findViewById(R.id.mediaImage);
            authorTextView = itemView.findViewById(R.id.authorTextView);
            contentTextView = itemView.findViewById(R.id.contentTextView);
            timeTextView = itemView.findViewById(R.id.timeTextView);
            numLikesTextView = itemView.findViewById(R.id.numLikesTextView);
            deletePost = itemView.findViewById(R.id.deletePostIcon);
            commentButton = itemView.findViewById(R.id.comentButton);
            commentsRecycler = itemView.findViewById(R.id.commentsRecyclerView);
            commentCountTextView = itemView.findViewById(R.id.commentCountTextView);
        }

        // mostrar el modal de confirmacion
        void mostrarConfirmacionEliminar(Map<String, Object> post, Client client, Runnable actualizarLista) {
            new androidx.appcompat.app.AlertDialog.Builder(itemView.getContext())
                    .setTitle("Eliminar post")
                    .setMessage("¿Estás seguro de que quieres eliminar este post?")
                    .setPositiveButton("Eliminar", (dialog, which) -> eliminarPost(post, client, actualizarLista))
                    .setNegativeButton("Cancelar", null)
                    .show();
        }

        // Eliminar el post
        void eliminarPost(Map<String, Object> post, Client client, Runnable actualizarLista) {
            Databases databases = new Databases(client);
            Handler mainHandler = new Handler(Looper.getMainLooper());

            databases.deleteDocument(
                    itemView.getContext().getString(R.string.APPWRITE_DATABASE_ID),
                    itemView.getContext().getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                    post.get("$id").toString(),
                    new CoroutineCallback<>((result, error) -> {
                        if (error != null) {
                            error.printStackTrace();
                            return;
                        }
                        mainHandler.post(() -> {
                            actualizarLista.run();
                            Snackbar.make(itemView, "Post eliminado", Snackbar.LENGTH_SHORT).show();
                        });
                    })
            );
        }
    }

    class PostsAdapter extends RecyclerView.Adapter<PostViewHolder> {
        DocumentList<Map<String,Object>> lista = null;
        DocumentList<Map<String,Object>> listaComentarios = null;
        @NonNull
        @Override
        public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PostViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.viewholder_post, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
            Map<String,Object> post = lista.getDocuments().get(position).getData();
            if (post.get("authorPhotoUrl") == null) {
                holder.authorPhotoImageView.setImageResource(R.drawable.user);
            }
            else {
                Glide.with(getContext()).load(post.get("authorPhotoUrl").toString()).circleCrop()
                        .into(holder.authorPhotoImageView);
            }
            holder.authorTextView.setText(post.get("author").toString());
            holder.contentTextView.setText(post.get("content").toString());

            // comments

            holder.commentsRecycler.setLayoutManager(new LinearLayoutManager(holder.commentsRecycler.getContext()));
            PostsAdapter adapterComentarios = new PostsAdapter();
            holder.commentsRecycler.setAdapter(adapterComentarios);

            obtenerComentarios((String) post.get("$id"), adapterComentarios, holder.commentCountTextView);

            // comment button

            holder.commentButton.setOnClickListener(v -> {
                appViewModel.paraComentar(true, (String) post.get("$id"));
                navController.navigate(R.id.newPostFragment);

            });

            // icono delete
            if(!post.get("uid").equals(userId)) {
                holder.deletePost.setVisibility(View.INVISIBLE);
            } else {
                holder.deletePost.setVisibility(View.VISIBLE);
                holder.deletePost.setOnClickListener(v ->
                        holder.mostrarConfirmacionEliminar(post, client, () -> obtenerPosts())
                );
            }

            // fecha y hora
            SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            Calendar calendar = Calendar.getInstance();

            if (post.get("time") != null) {
                calendar.setTimeInMillis((long) post.get("time"));
            } else {
                calendar.setTimeInMillis(0);
            }

            holder.timeTextView.setText(formatter.format(calendar.getTime()));

            // Gestion de likes
            List<String> likes = (List<String>) post.get("likes");
            if(likes.contains(userId)) { holder.likeImageView.setImageResource(R.drawable.like_on);}
            else
                holder.likeImageView.setImageResource(R.drawable.like_off);
                holder.numLikesTextView.setText(String.valueOf(likes.size()));
                holder.likeImageView.setOnClickListener(view -> {
                Databases databases = new Databases(client);
                Handler mainHandler = new Handler(Looper.getMainLooper());
                List<String> nuevosLikes = likes;
                if(nuevosLikes.contains(userId))
                    nuevosLikes.remove(userId);
                else
                    nuevosLikes.add(userId);
                Map<String, Object> data = new HashMap<>();
                data.put("likes", nuevosLikes);

                try {
                    databases.updateDocument(
                            getString(R.string.APPWRITE_DATABASE_ID),
                            getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                            post.get("$id").toString(), // documentId
                            data, // data (optional)
                            new ArrayList<>(), // permissions (optional)
                            new CoroutineCallback<>((result, error) -> {
                                if (error != null) {
                                    error.printStackTrace();
                                    return;
                                }
                                System.out.println("Likes actualizados:" +
                                        result.toString());
                                mainHandler.post(() -> obtenerPosts());
                            })
                    );
                } catch (AppwriteException e) {
                    throw new RuntimeException(e);
                }
            });

            // Miniatura de media
            if (post.get("mediaUrl") != null) {
                holder.mediaImageView.setVisibility(View.VISIBLE);
                if ("audio".equals(post.get("mediaType").toString())) {
                    Glide.with(requireView()).load(R.drawable.audio).centerCrop().into(holder.mediaImageView);
                } else {
                    Glide.with(requireView()).load(post.get("mediaUrl").toString()).centerCrop().into
                            (holder.mediaImageView);
                }
                holder.mediaImageView.setOnClickListener(view -> {
                    appViewModel.postSeleccionado.setValue(post);
                    navController.navigate(R.id.mediaFragment);
                });
            } else {
                holder.mediaImageView.setVisibility(View.GONE);
            }


        }

        @Override
        public int getItemCount() {
            return lista == null ? 0 : lista.getDocuments().size();
        }
        public void establecerLista(DocumentList<Map<String,Object>> lista) {
            this.lista = lista;
            notifyDataSetChanged();
        }

    }

    void obtenerPosts() {
        Databases databases = new Databases(client);
        Handler mainHandler = new Handler(Looper.getMainLooper());
        try {
            databases.listDocuments(
                    getString(R.string.APPWRITE_DATABASE_ID), // databaseId
                    getString(R.string.APPWRITE_POSTS_COLLECTION_ID), // collectionId
                    Arrays.asList(Query.Companion.orderDesc("time"), Query.Companion.limit(50), Query.Companion.isNull("parent")), // queries (optional)
                    new CoroutineCallback<>((result, error) -> {
                        if (error != null) {
                            Snackbar.make(requireView(), "Error al obtener los posts: "
                                    + error.toString(), Snackbar.LENGTH_LONG).show();
                            return;
                        }
                        System.out.println( result.toString() );
                        mainHandler.post(() -> adapter.establecerLista(result));
                    })
            );
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }
    }

    void obtenerComentarios(String id, PostsAdapter adapterComentarios, TextView commentCountTextView) {
        Databases databases = new Databases(client);
        Handler mainHandler = new Handler(Looper.getMainLooper());

        try {
            databases.listDocuments(
                    getString(R.string.APPWRITE_DATABASE_ID),
                    getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                    Arrays.asList(Query.Companion.orderDesc("time"), Query.Companion.equal("parent", id)),
                    new CoroutineCallback<>((result, error) -> {
                        if (error != null) {
                            Snackbar.make(requireView(), "Error al obtener comentarios", Snackbar.LENGTH_LONG).show();
                            return;
                        }

                        mainHandler.post(() -> {
                            adapterComentarios.establecerLista(result);
                            int numComentarios = result.getDocuments().size();
                            commentCountTextView.setText(String.valueOf(numComentarios));
                        });
                    })
            );
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }
    }

}