package com.example.glassesgang;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

/**
 * Activity to add a book in the database
 * title, author, isbn are fields required to add book
 */
public class AddBookActivity extends AppCompatActivity {
    private Button backButton;
    private Button scanButton;
    private Button addImageButton;
    private Button saveButton;
    private ImageView bookImageView;
    private EditText titleEditText;
    private EditText authorEditText;
    private EditText isbnEditText;
    private String user;
    private final int CAMERA_PHOTO_TAKEN = 102;
    private FirebaseStorage storage = FirebaseStorage.getInstance();
    private String bookImageUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_edit_book);

        // get the user
        String filename = getResources().getString(R.string.email_account);
        SharedPreferences sharedPref = this.getSharedPreferences(filename, Context.MODE_PRIVATE);
        user = sharedPref.getString("email", null);

        if (user == null) {
            Log.e("Email","No user email recorded");
        }

        findViewsById();

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // meant to return to the previous activity without adding a book
            }
        });

        // add information to database upon saving
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // get the values for title, author, and isbn from the EditTexts
                final String title = titleEditText.getText().toString();
                final String author = authorEditText.getText().toString();
                final String isbn = isbnEditText.getText().toString();

                if (title.length()>0 && author.length()>0 && isbn.length()>0) {
                    Book newBook = new Book(title, author, isbn, user, bookImageUrl);
                    DatabaseManager database = new DatabaseManager();
                    database.addBook(newBook, user);
                    // TODO: somehow add to the system and make sure photos are attached
                    finish();
                }

                CameraActivity.cleanBookImage();
            }
        });

        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: implement scanning for submission 4
            }
        });

        addImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Encapsulate Camera in its own class
                Intent intent = new Intent(getApplicationContext(), CameraActivity.class);
                startActivityForResult(intent, CAMERA_PHOTO_TAKEN);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_PHOTO_TAKEN) {
            Bitmap bookImage = CameraActivity.getBookImage();
            bookImageView.setImageBitmap(bookImage);
            uploadPictureToStorage(bookImage);
        }
    }

    private void uploadPictureToStorage(Bitmap bitmap) {
        // save image
        String path = "bookImages/" + UUID.randomUUID() + ".jpg";
        StorageReference storageRef = storage.getReference();
        StorageReference reference = storageRef.child(path);

        // Create file metadata including the content type
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType("image/jpg")
                .build();

        // convert bitmap to bytes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        byte[] data = baos.toByteArray();

        // Upload the file and metadata
        UploadTask uploadTask = reference.putBytes(data, metadata);
        Task<Uri> urlTask = uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
            @Override
            public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                if (!task.isSuccessful()) {
                    throw task.getException();
                }

                // Continue with the task to get the download URL
                return reference.getDownloadUrl();
            }
        }).addOnCompleteListener(new OnCompleteListener<Uri>() {
            @Override
            public void onComplete(@NonNull Task<Uri> task) {
                if (task.isSuccessful()) {
                    Uri downloadUri = task.getResult();
                    setBookImageUrl(downloadUri.toString());
                } else {
                    Toast.makeText(AddBookActivity.this, "Failed to upload image", Toast.LENGTH_SHORT);
                }
            }
        });
    }

    private void setBookImageUrl(String newBookImageUrl) {
        bookImageUrl = newBookImageUrl;
    }

    /**
     * assigns each attribute to the proper textView.
     */
    private void findViewsById() {
        addImageButton = findViewById(R.id.add_image_button);
        backButton = findViewById(R.id.back_button);
        scanButton = findViewById(R.id.scan_button);
        saveButton = findViewById(R.id.finish_button);
        bookImageView = findViewById(R.id.login_book_image_view);
        titleEditText = findViewById(R.id.book_title_bar);
        authorEditText = findViewById(R.id.author_name_bar);
        isbnEditText = findViewById(R.id.isbn_bar);
    }
}
