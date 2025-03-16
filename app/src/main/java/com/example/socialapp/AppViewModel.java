package com.example.socialapp;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import java.util.Map;

public class AppViewModel extends AndroidViewModel {

    public static class Media {
        public Uri uri;
        public String tipo;
        public Media(Uri uri, String tipo) {
            this.uri = uri;
            this.tipo = tipo;
        }
    }
    public MutableLiveData<Map<String,Object>> postSeleccionado = new MutableLiveData<>();
    public MutableLiveData<Media> mediaSeleccionado = new MutableLiveData<>();
    public AppViewModel(@NonNull Application application) {
        super(application);
    }
    public void setMediaSeleccionado(Uri uri, String type) {
        mediaSeleccionado.setValue(new Media(uri, type));
    }

    // comentarios
    boolean esComentario = false;
    String parentUid;


    public void paraComentar(boolean comentario, String uid) {
        this.esComentario = comentario;
        this.parentUid = uid;
    }

    public boolean getEsComentario() {
        return esComentario;
    }

    public void setEsComentario(boolean esComentario) {
        this.esComentario = esComentario;
    }

    public String getParentUid() {
        return parentUid;
    }

    public void setParentUid(String parentUid) {
        this.parentUid = parentUid;
    }
}