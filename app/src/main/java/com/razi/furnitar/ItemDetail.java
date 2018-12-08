package com.razi.furnitar;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import com.Database.Database;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.ar.core.ArCoreApk;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.squareup.picasso.Picasso;

import butterknife.BindView;
import butterknife.ButterKnife;

public class ItemDetail extends AppCompatActivity {
    public static final String tag = ItemDetail.class.getSimpleName();
    FirebaseFirestore db;
    DocumentReference itemRef;
    ImageView itemImages;
    TextView itemName, itemPrice, errorText;
    Button btn, addToCart;
    NumberPicker numberPicker;
    Item item;
    private static Context c;
    private static GoogleApiClient mGoogleApiClient;
    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @BindView(R.id.toolbar_detail)
    public Toolbar toolBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_detail);
        ButterKnife.bind(this);
        c = this;
        toolBar.setTitle(getResources().getString(R.string.app_name));
        setSupportActionBar(toolBar);

        DrawerUtil.getDrawer(this, toolBar);

        MobileAds.initialize(this, "ca-app-pub-3785462047203626~3446159950");
        itemImages = findViewById(R.id.itemImages);
        itemName = findViewById(R.id.itemName);
        itemPrice = findViewById(R.id.itemPrice);
        btn = findViewById(R.id.viewInAR);
        numberPicker = findViewById(R.id.quantity);
        errorText = findViewById(R.id.errorText);
        addToCart = findViewById(R.id.addToCart);

        // Obtain the FirebaseAnalytics instance.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        Intent intent = getIntent();
        String path = intent.getStringExtra("path");
        db = FirebaseFirestore.getInstance();
        itemRef = db.document(path);
        itemRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                item = task.getResult().toObject(Item.class);
                Picasso.get()
                        .load(item.getImages().get(0))
                        .into(itemImages);
                itemName.setText(item.getName());
                itemPrice.setText("Rs. " + item.getPrice());
                if (item.getIsAR() && maybeEnableArButton()) {
                    btn.setVisibility(View.VISIBLE);
                }
                numberPicker.setMinValue(1);
                numberPicker.setMaxValue(item.getQuantity());
            }
        });
        AdView adView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder()
                .build();
        adView.loadAd(adRequest);
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, connectionResult -> Log.i("OK", "NOT OK"))
                .addApi(Auth.GOOGLE_SIGN_IN_API)
                .build();
    }

    boolean maybeEnableArButton() {
        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        if (availability.isTransient()) {
            // Re-query at 5Hz while compatibility is checked in the background.
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    maybeEnableArButton();
                }
            }, 200);
        }
        if (availability.isSupported()) {
            return true;
        } else { // Unsupported or unknown.
            errorText.setVisibility(View.VISIBLE);
            return false;
        }
    }

    public void viewInAR(View view) {
        Bundle bundle = new Bundle();
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
        String id = itemRef.getId();
        String name = item.getName();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, id);
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, name + ": View in AR");
        String asset = item.getImages().get(1);
        Intent intent = new Intent(getApplicationContext(), ARactivity.class);
        intent.putExtra("asset", asset);
        startActivity(intent);
    }

    public void addToCart(View view) {
        itemRef.update("quantity", item.getPrice() - numberPicker.getValue()).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                try {
                    new Database((getBaseContext()))
                            .addToCart(new order(common.currentUser.getId(),
                                    itemRef.getId(),
                                    item.getName(),
                                    item.getPrice(),
                                    numberPicker.getValue()));
                    Toast.makeText(ItemDetail.this, "Item Added to Cart",
                            Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    itemRef.update("quantity", item.getPrice() + numberPicker.getValue());
                    Toast.makeText(ItemDetail.this, "Something went Wrong",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    public static void signOut() {
        FirebaseAuth.getInstance().signOut();
        Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
                status -> c.startActivity(new Intent(c, Login.class)));
    }
}
