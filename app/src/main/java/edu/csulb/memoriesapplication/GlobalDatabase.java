package edu.csulb.memoriesapplication;

import android.net.Uri;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Created by Daniel on 12/2/2017.
 * Customizes the storing feature to store it globally instead of just in the user's database
 */

public class GlobalDatabase {
    public final static String GLOBAL_MEDIA_REFERENCE = "globalMedia_";
    public final static String URL_KEY = "url";
    public final static String MEDIA_TYPE_KEY = "mediaType";
    public final static String CITY_KEY = "city";
    public final static String LIKES_COUNT_KEY = "likesCount";

    public enum MediaType{
        IMAGE,
        VIDEO
    }

    //Adds the URL globally
    public static void addMediaUrl(Uri uri,String city, String state, MediaType mediaType) {
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference(GLOBAL_MEDIA_REFERENCE + state);
        databaseReference = databaseReference.push();
        databaseReference.child(URL_KEY).setValue(uri.toString());
        if(mediaType == MediaType.IMAGE) {
            databaseReference.child(MEDIA_TYPE_KEY).setValue("image");
        } else {
            databaseReference.child(MEDIA_TYPE_KEY).setValue("video");
        }
        databaseReference.child(CITY_KEY).setValue(city);
        databaseReference.child(LIKES_COUNT_KEY).setValue(0);
    }

    //Retrieves the Root database reference based on the state that the user is in
    public static DatabaseReference getMediaListReference(String state) {
        return FirebaseDatabase.getInstance().getReference(GLOBAL_MEDIA_REFERENCE + state);
    }
}
