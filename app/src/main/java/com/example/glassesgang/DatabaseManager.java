package com.example.glassesgang;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.glassesgang.Notification.App;
import com.example.glassesgang.Notification.Notification;
import com.example.glassesgang.Transaction.TransactionType;
import com.example.glassesgang.Transaction.Request;
import com.example.glassesgang.BookStatus.Status;
import com.example.glassesgang.Transaction.RequestReference;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import kotlin.coroutines.Continuation;

import static com.example.glassesgang.BookStatus.statusString;
import static com.example.glassesgang.BookStatus.stringStatus;

/**
 * Object for handling transactions in the database
 */
public class DatabaseManager {
    private static FirebaseFirestore db;
    private static final String TAG = "DatabaseManager";


    public DatabaseManager() {
        db = FirebaseFirestore.getInstance();
    }

    /**
     * adds a book to the owner catalogue of given user
     *
     * @param newBook a Book object. It is the book to be added
     * @param user    a String consisting of the user's email.
     */
    public static void addBook(final Book newBook, final String user) {
        // add the book to the database books collection
        db.collection("books")
                .add(newBook)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        Log.d(TAG, "DocumentSnapshot written with ID: " + documentReference.getId());
                        documentReference.update("bid", documentReference.getId());
                        addBookInOwnerCatalogue(documentReference.getId(), user);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error adding document", e);
                    }
                });

    }

    /**
     * deletes the book from the database
     *
     * @param bookToDelete a Book object representing the book to delete
     */
    public static void deleteBook(Book bookToDelete) {
        String owner = bookToDelete.getOwner();
        String borrower = bookToDelete.getBorrower();
        String bid = bookToDelete.getBID();
        DocumentReference bookRef = db.collection("books").document(bid);

        // delete from owner Catalogue
        DocumentReference ownerRef = db.collection("users").document(owner);
        ownerRef.update("ownerCatalogue", FieldValue.arrayRemove(bid));

        // delete requests associated with the book and the book from borrower catalogues
        // if book is either REQUESTED, ACCEPTED, or BORROWED
        if (!bookToDelete.getStatus().equals(Status.AVAILABLE)) {
            // delete requests associated with book and the book from borrower catalogues
            CollectionReference requestsRef = db.collection("requests");
            requestsRef
                    .whereEqualTo("bookId", bid)   // get requests associated with the book
                    .get()
                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                            ArrayList<String> borrowerList = new ArrayList<>();
                            if (task.isSuccessful()) {
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    requestsRef.document(document.getId()).delete();
                                    borrowerList.add((String) document.get("borrowerEmail"));
                                }
                                clearBorrowerCatalogues(borrowerList, bid);
                            } else {
                                Log.d(TAG, "Error getting documents when clearing owner Catalogue: ", task.getException());
                            }
                        }
                    });
        }

        // delete book from database
        bookRef.delete()
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


    /**
     * Helper function for addBook function to update a book in
     * the ownerCatalogue's field for a specific user
     * @param bid book id
     * @param user user id
     */
    private static void addBookInOwnerCatalogue(String bid, String user) {
        db.collection("users")
                .document(user)
                .update("ownerCatalogue", FieldValue.arrayUnion(bid))
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "DocumentSnapshot successfully updated!");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error updating document", e);
                    }
                });
    }

    /**
     * Creates user in the database
     * @param user user id
     */
    public static void createUser(final User user) {
        final CollectionReference usersDatabase = FirebaseFirestore.getInstance().collection("users");
        usersDatabase.document(user.getEmail()).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document.exists()) {
                        Log.d(TAG, "User already exist: " + document.getData());
                    } else {
                        Map<String, Object> userCatalogue = new HashMap<>();
                        userCatalogue.put("ownerCatalogue", new ArrayList<String>());
                        userCatalogue.put("notificationCatalogue", new ArrayList<String>());
                        userCatalogue.put("phoneNumber", "");
                        //userCatalogue.put("email", new String());
                        // adding the user
                        usersDatabase.document(user.getEmail())
                                .set(userCatalogue)
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        Log.d(TAG, "User successfully added to database");
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.w(TAG, "Error adding user to database", e);
                                    }
                                });
                    }
                } else {
                    Log.d(TAG, "get failed with ", task.getException());
                }
            }
        });
    }

    public static void editContactInfo(String currentEmail, String newEmail) {
        final CollectionReference usersDatabase = FirebaseFirestore.getInstance().collection("users");
        usersDatabase.document(currentEmail).update("email", newEmail)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "contact information successfully updated!");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error updating contact information", e);
                    }
                });
    }

    public static void editPhoneNumber(String email, String phoneNumber) {
        DocumentReference userRef = db.collection("users").document(email);
        HashMap<String, Object> contactInfoHash = new HashMap<>();
        contactInfoHash.put("phoneNumber", phoneNumber);

        userRef.update(contactInfoHash)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "DocumentSnapshot successfully written!");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error writing document", e);
                    }
                });
    }


    /**
     * Remove all the books that a user owns
     * @param email A string, the email of the user whose books must be cleared.
     */
    public static void clearOwnerCatalogue(String email) {
        CollectionReference booksRef = db.collection("books");

        // delete books from db
        booksRef
                .whereEqualTo("owner", email)   // get books owned by the owner
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                booksRef.document(document.getId()).delete();
                            }
                        } else {
                            Log.d(TAG, "Error getting documents when clearing owner Catalogue: ", task.getException());
                        }
                    }
                });

        // delete books from owner Catalogue
        db.collection("users").document(email)
                .update("ownerCatalogue", new ArrayList<String>())  // set owner Catalogue to an empty string
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Owner Catalogue cleared");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error clearing Owner catalogue", e);
                    }
                });
    }
    
    /**
     * Make a request as a borrower. Creates a request object and attaches it to the book
     *
     * @param request The request object to be added to the database
     */
    public static void addRequest(Request request, String bookTitle) {
        // add the request to the requests collection in the database
        db.collection("requests")
                .add(request)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        Log.d(TAG, "Request added successfully, written with ID: " + documentReference.getId());
                        documentReference.update("requestId", documentReference.getId());
                        addRequestToBook(documentReference.getId(), request.getBookId());
                        addRequestToUser(documentReference.getId(), request.getBookId(), request.getBorrowerEmail());
                        documentReference.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                            @Override
                            public void onSuccess(DocumentSnapshot documentSnapshot) {
                                Request requestObj = documentSnapshot.toObject(Request.class);
                                addNotification(new Notification(requestObj.getBorrowerEmail(),
                                        requestObj.getOwnerEmail(),
                                        Notification.NotificationType.NEW_REQUEST,
                                        bookTitle));
                            }
                        });

                        //create notification and add it
                        changeBookStatus(Status.REQUESTED, request.getBookId());
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error adding document", e);
                    }
                });
    }

    public static void addRequestToBook(String requestId, String bid) {
        db.collection("books").document(bid)
                .update("requests", FieldValue.arrayUnion(requestId))
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Request successfully added to book " + bid);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error adding request to book " + bid, e);
                    }
                });
    }

    public static void addRequestToUser(String requestId, String bid, String userEmail) {
        db.collection("users").document(userEmail).collection("borrowerCatalogue").document(bid)
                .set(new RequestReference(Status.REQUESTED, requestId))
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Request reference for request " + requestId + " successfully added to user");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Failed to add request reference for request " + requestId + " to user", e);
                    }
                })

        ;
    }

    public static void changeBookStatus(Status status, String bid) {
        db.collection("books").document(bid).update("status", status)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Book status successfully updated!");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error updating book status", e);
                    }
                });
    }

    public static void deleteRequest(String userId, String bid) {
        //get user's request id for that book from their borrowerCatalogue
        DocumentReference borrowerCatReqRef = db.collection("users").document(userId).collection("borrowerCatalogue").document(bid);
        if (borrowerCatReqRef != null) {
            borrowerCatReqRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                @Override
                public void onSuccess(DocumentSnapshot documentSnapshot) {
                    if (documentSnapshot.exists()) {
                        String requestId = (String) documentSnapshot.get("requestRefId");
                        DocumentReference bookRef = db.collection("books").document(bid);

                        //delete request from books request list
                        bookRef.update("requests", FieldValue.arrayRemove(requestId));
                        bookRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                            @Override
                            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                if (task.isSuccessful()) {
                                    DocumentSnapshot document = task.getResult();
                                    if (document.exists()) {
                                        ArrayList<String> requests = (ArrayList<String>) document.get("requests");
                                        if (requests.size() == 0) { // it means the book has no more requests and so its status must be set to available
                                            changeBookStatus(Status.AVAILABLE, bid);
                                        }
                                        Log.d(TAG, "DocumentSnapshot data: " + document.getData());
                                    } else {
                                        Log.d(TAG, "No such document");
                                    }
                                } else {
                                    Log.d(TAG, "get failed with ", task.getException());
                                }
                            }
                        });

                        //delete user's request from db
                        DocumentReference reqRef = db.collection("requests").document(requestId);
                        reqRef.delete()
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        Log.d(TAG, "Request " + requestId + " successfully deleted!");
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.w(TAG, "Error deleting request " + requestId, e);
                                    }
                                });
                        //delete request from user's borrowerCatalogue
                        borrowerCatReqRef.delete();
                    }
                }
            });
        }
    }

    public static void addNotification(Notification notification) {
        // add the request to the requests collection in the database
        db.collection("notifications")
                .add(notification)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        Log.d(TAG, "Notification added successfully, written with ID: " + documentReference.getId());
                        documentReference.update("notificationId", documentReference.getId());
                        notification.setNotificationId(documentReference.getId());
                        addNotificationToUser(notification);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error adding document", e);
                    }
                });
    }

    private static void addNotificationToUser(Notification notification) {
        // adds to receiver's notification array
        db.collection("users")
                .document(notification.getReceiverEmail())
                .update("notificationCatalogue", FieldValue.arrayUnion(notification.getNotificationId()))
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Notification successfully added to user " + notification.getReceiverEmail());
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error adding notification to user" + notification.getReceiverEmail(), e);
                    }
                });
    }

    public static void deleteNotification(Notification not) {
        String notId = not.getNotificationId();
        DocumentReference notRef = db.collection("notifications").document(notId);
        DocumentReference userRef = db.collection("users").document(not.getReceiverEmail());

        // delete notification from users notificationCatalogue //TODO: add a check for if the notification exists
        userRef.update("notificationCatalogue", FieldValue.arrayRemove(notId));

        // delete notification from database
        notRef.delete()
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Notification " + notId + " successfully deleted!");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error deleting request " + notId, e);
                    }
                });

    }

    public static void acceptRequest(String bid, Request request) {
        //change book status to accepted
        changeBookStatus(Status.ACCEPTED, bid);

        //change accepted borrower's request in borrowerCatalogue to have status of accepted
        DocumentReference borrowerRequest = db.collection("users")
                .document(request.getBorrowerEmail())
                .collection("borrowerCatalogue")
                .document(request.getBookId());
        borrowerRequest.update("requestRefStatus", Status.ACCEPTED);

        // get book name from bid
        DocumentReference docRef = db.collection("books").document(bid);

        docRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document.exists()) {
                        //send notification to borrower that their request has been accepted
                        addNotification(new Notification(request.getOwnerEmail(), request.getBorrowerEmail(), Notification.NotificationType.ACCEPT_REQUEST, (String)document.get("title")));
                        Log.d(TAG, "DocumentSnapshot data: " + document.getData());
                    } else {
                        Log.d(TAG, "No such document");
                    }
                } else {
                    Log.d(TAG, "get failed with ", task.getException());
                }
            }
        });

        //delete all other requests of other borrowers of that book
        db.collection("users").get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if (task.isSuccessful()) {
                    ArrayList<String> userList = new ArrayList<String>();
                    for (QueryDocumentSnapshot document : task.getResult()) {  //add rejected borrowers to a list
                        if (!document.getId().equals(request.getBorrowerEmail())) //exclude accepted borrower
                            userList.add(document.getId());
                    }
                    userList.forEach(user -> deleteRequest(user, bid));
                } else {
                    Log.d(TAG, "Error getting users: ", task.getException());
                }
            }
        });
    }

    public static void transactionAction(String requestId, String userType, TransactionType transactionType) {
        DocumentReference reqRef = db.collection("requests").document(requestId);
        reqRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot req) {
                if (userType == "o") {
                    reqRef.update("ownerAction", true);
                }
                else if (userType == "b") {
                    reqRef.update("borrowerAction", true);
                }
                if (req.get("borrowerAction").equals(true) && req.get("ownerAction").equals(true)) {
                    String bookId = req.get("bookId").toString();
                    String userId = req.get("borrowerEmail").toString();
                    completeTransaction(requestId, bookId, userId, transactionType);
                }
            }
        });
    }

    public static void completeTransaction(String requestId, String bookId, String userId, TransactionType transactionType) {
        if (transactionType == TransactionType.REQUEST) {
            //request has been processed (both users have scanned)
            changeBookStatus(Status.BORROWED, bookId);

            //update borrower's catalogue to have book as borrowed
            db.collection("users").document(userId).collection("borrowerCatalogue").document(bookId).update("requestRefStatus", Status.BORROWED);

        }
        else if (transactionType == TransactionType.RETURN) {
            //return has been processed
            changeBookStatus(Status.AVAILABLE, bookId);

            //delete the request, end of request lifecycle
            deleteRequest(userId, bookId);
        }
    }

    /**
     * removes the specified book from the catalogue of the give borrowers
     * @param borrowers an array list of borrowers
     * @param bid the bid of the book to remove from the borrowers catalogue
     */
    private static void clearBorrowerCatalogues(ArrayList<String> borrowers, String bid) {
        for (String borrower: borrowers) {
            DocumentReference bookRef = db.collection("users").document(borrower).collection("borrowerCatalogue").document(bid);
            bookRef.delete()
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
}
