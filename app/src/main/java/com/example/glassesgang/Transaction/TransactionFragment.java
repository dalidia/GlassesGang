package com.example.glassesgang.Transaction;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.glassesgang.Book;
import com.example.glassesgang.Helpers.OverrideBackPressed;
import com.example.glassesgang.R;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * A fragment representing a list of Items.
 */
public class TransactionFragment extends Fragment implements OverrideBackPressed {

    private OnTransactionInteractionListener listener;
    private TextView emailTextView;
    private Button transactionButton;
    private Button showLocationButton;
    private String requestId;
    private String userEmail;
    private String userType;
    private LatLng givenLocation;
    final String TAG = "TransactionFragment";
    private FirebaseFirestore db;
    private LatLng mapMarker;

    public interface OnTransactionInteractionListener {
        void onTransactionPressed(String requestId, TransactionType transactionType);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnTransactionInteractionListener) {
            listener = (OnTransactionInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + "must implement OnTransactionInteractionListener");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getParentFragmentManager().setFragmentResultListener("location", this, (FragmentResultListener) (locationString, bundle) -> {
            //convert to latlng coordinates
            mapMarker = bundle.getParcelable("location");
            if (mapMarker != null)
            {
                transactionButton.setText("CONFIRM LOCATION");
                transactionButton.setEnabled(true);
            }
            else transactionButton.setEnabled(false);
        });

        // connect to the database
        db = FirebaseFirestore.getInstance();

        //get requestId, borrower or owner email, and userType from bundle
        requestId = getArguments().getString("requestId");
        userEmail = getArguments().getString("userEmail");
        userType = getArguments().getString("userType");
        if (getArguments().getParcelable("givenLocation") != null){
            com.google.android.gms.maps.model.LatLng location = getArguments().getParcelable("givenLocation");
            givenLocation = new LatLng(location);
        }
    }

    @Override
    public boolean onBackPressed() {
        if (userType == "o" && mapMarker != null) return true;
        else if (userType == "o" && mapMarker == null) {
            Toast.makeText(getContext(), "Please specify a location to accept this request", Toast.LENGTH_SHORT).show();
            return false;
        }
        else return true;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.fragment_transaction, container, false);
        return v;
    }

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //get email displayed based on userType
        emailTextView = view.findViewById(R.id.email_textview);
        emailTextView.setText(userEmail);

        //location button, default gone. if owner, enable. if borrower, disabled permanently
        showLocationButton = view.findViewById(R.id.show_location_button);
        showLocationButton.setVisibility(View.GONE); //disabled by default
        transactionButton = view.findViewById(R.id.transaction_button);

        //if owner, then transaction is offering a requested book. involves map and scan
        if (userType.equals("o")) {
            transactionButton.setText("CONFIRM LOCATION");
            transactionButton.setEnabled(false);

            if (givenLocation == null) {
                //owner has not specified a location yet
                showLocationButton.setVisibility(View.VISIBLE);
                showLocationButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        //display map fragment
                        Fragment mapFragment = new MapFragment(userType); //initialized as an owner map fragment (no params)
                        getParentFragmentManager().beginTransaction().replace(R.id.transaction_fragment_container, mapFragment).commit();

                        //destroy button
                        showLocationButton.setVisibility(View.GONE);
                    }
                });
            } else {
                //owner has already put in location. display this location
                Fragment mapFragment = new MapFragment(userType, givenLocation);
                getParentFragmentManager().beginTransaction().replace(R.id.transaction_fragment_container, mapFragment).commit();
            }

            transactionButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //offer book functionality, TODO: open scan activity
                    if (transactionButton.getText().toString().equals("CONFIRM LOCATION") && mapMarker != null) {
                        //write location to request
                        db.collection("requests").document(requestId).update("location", mapMarker);
                        transactionButton.setText("OFFER");
                    }
                    else if (transactionButton.getText().toString().equals("OFFER")){
                        //TODO: open scanner and initiate transaction
                        listener.onTransactionPressed(userType, TransactionType.REQUEST);
                    }
                }
            });
        }

        //if borrower, then transaction is accepting owners offer. load map fragment
        else if (userType.equals("b")) {
            transactionButton.setText("ACCEPT"); //transaction button enabled by default, location button uninteractable
            //display map fragment
            Fragment mapFragment = new MapFragment(userType, givenLocation); //initialized as a borrower map fragment
            getParentFragmentManager().beginTransaction().replace(R.id.transaction_fragment_container, mapFragment).commit();
            transactionButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onTransactionPressed(userType, TransactionType.REQUEST);
                }
            });
        }
    }
}