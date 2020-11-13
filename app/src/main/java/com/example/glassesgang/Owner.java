package com.example.glassesgang;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Owner extends User {
    // Once book object is implemented
    //public ArrayList<Books> catalogue;
    public ArrayList<String> catalogue;
    //private FirebaseFirestore db;
    //DocumentReference ownerDatabase = FirebaseFirestore.getInstance().collection("users").document(this.email);
    DocumentReference ownerDatabase = this.userDatabase.document(this.email)
            .collection("owners").document("ownerObject");

    public Owner(Context context) {
        super(context);
        this.catalogue = new ArrayList<String>();
        addOwnerToDatabase();
    }

    public Owner() {
        super();
        this.catalogue = new ArrayList<>();
    }

        super(email);
        this.catalogue = new ArrayList<>();
    }

    public ArrayList<String> getCatalogue() {
        return catalogue;
    }

    public void setCatalogue(ArrayList<String> catalogue) {
        this.catalogue = catalogue;
    }

}

