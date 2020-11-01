package com.example.glassesgang;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

public class OwnerBookProfileActivity extends AppCompatActivity implements DeleteBookDialogFragment.DeleteBookDialogListener{
    private TextView titleTextView;
    private TextView authorTextView;
    private TextView isbnTextView;
    private TextView statusTextView;
    private TextView borrowerTextView;
    private Button deleteButton;
    private Button editButton;
    private String author;
    private String title;
    private String isbn;
    private String status;
    private String bookID;
    private String borrower;
    private Book book;
    private  FirebaseFirestore db;
    private static final String TAG = "OwnerBkProfileActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owner_book_profile);

        findViewsById();

        db = FirebaseFirestore.getInstance();
        final String path = getIntent().getStringExtra("path");   // get the path to the book document
        DocumentReference docRef = db.document(path);    // get reference to the book object using path

        // get the book document from firestore using the document reference
        docRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot documentSnapshot) {
                book = documentSnapshot.toObject(Book.class);   // convert the book document to Book Object
                author = book.getAuthor();
                title = book.getTitle();
                isbn = book.getISBN();
                status = book.getStatus();
                borrower = book.getBorrower();
                setTextViews();
            }
        });

        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new DeleteBookDialogFragment().show(getSupportFragmentManager(), "DELETE_BOOK");
            }
        });

        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent editIntent = new Intent(OwnerBookProfileActivity.this, EditBookActivity.class);
                editIntent.putExtra("path", path);   // pass in the path to the book document
                startActivityForResult(editIntent, 1);
            }
        });

    }

    // assigns each attribute to the proper textView.
    private void findViewsById() {
        titleTextView = findViewById(R.id.title_textView);
        authorTextView = findViewById(R.id.author_textView);
        isbnTextView = findViewById(R.id.isbn_textView);
        statusTextView = findViewById(R.id.status_textView);
        borrowerTextView = findViewById(R.id.borrower_textView);
        deleteButton = findViewById(R.id.delete_button);
        editButton = findViewById(R.id.edit_button);
    }

    // just set each TextViews text with the appropriate text
    private void setTextViews() {
        titleTextView.setText(title);
        authorTextView.setText(author);
        isbnTextView.setText(isbn);
        statusTextView.setText(status);
        borrowerTextView.setText(borrower);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // If user chose to save changes made in EditBookActivity
        // Update the book profile upon returning
        if (requestCode == 1) {
            if(resultCode == Activity.RESULT_OK){

                String path =  data.getStringExtra("path");
                DocumentReference docRef = db.document(path);    // get reference to the book object using path
                // get the book document from firestore using the document reference
                docRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        book = documentSnapshot.toObject(Book.class);   // convert the book document to Book Object
                        author = book.getAuthor();
                        title = book.getTitle();
                        isbn = book.getISBN();
                        status = book.getStatus();
                        setTextViews();
                    }
                });
            }
        }
    }

    // When user confirms the deletion of a book, this method is called.
    // It deletes that book from the database.
    @Override
    public void onConfirmPressed() {
        DocumentReference bookRef = db.document(getIntent().getStringExtra("path"));
        String owner = getIntent().getStringExtra("owner");

        // delete the book from the owner catalogue
        DocumentReference ownerRef = db.collection("users").document(owner);
        ownerRef.update("ownerCatalogue", FieldValue.arrayRemove(bookRef.getId()));

        // delete the book from the data base
        bookRef
                .delete()
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "DocumentSnapshot successfully deleted!");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error deleting document", e);
                    }
                });
    }
}